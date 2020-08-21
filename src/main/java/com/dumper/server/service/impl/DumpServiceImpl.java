package com.dumper.server.service.impl;

import com.dumper.server.entity.CheckResult;
import com.dumper.server.entity.Dump;
import com.dumper.server.entity.ShortDump;
import com.dumper.server.enums.Query;
import com.dumper.server.enums.UserAccess;
import com.dumper.server.enums.Version;
import com.dumper.server.repository.BackupsetRepository;
import com.dumper.server.service.DumpService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.dumper.server.enums.Key.*;

// todo: убрать ссылку после ознакомления
// https://docs.microsoft.com/ru-ru/sql/t-sql/statements/backup-transact-sql?view=sql-server-ver15
@Slf4j
@RequiredArgsConstructor
@Service
@PropertySource("classpath:application.properties")
public class DumpServiceImpl implements DumpService {

    @Value(value = "${spring.datasource.username:username}")
    private String username;

    @Value(value = "${spring.datasource.password:password}")
    private String password;

    @Value(value = "${server:localhost}")
    private String server;

    @Value(value = "${database.name:TestDB}")
    private String database;

    @Value(value = "${directory:directory}")
    private String directory;

    @Value(value = "${api.server.url}")
    private String actualDumpsUrl;

    @Value("${api.server.version.url}")
    private String versionUrl;

    @Value(value = "${api.server.download.url}")
    private String downloadUrl;

    private final static String BASE_COMMAND = "/opt/mssql-tools/bin/sqlcmd";

    private final BackupsetRepository repository;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;
    private static final String NO_DUMPS = "No dumps available";

    // todo: мне не нравится, как здесь получилось
    @SneakyThrows
    @Override
    public String executeCommand(String[] command) {
        Runtime runtime = Runtime.getRuntime();
        String errors, out;
        InputStream errorStream = null, outStream = null;

        log.info("Start executing command: " + Arrays.toString(command));
        try {
            Process proc = runtime.exec(command);
            proc.waitFor();
            errorStream = proc.getErrorStream();
            outStream = proc.getInputStream();
            errors = IOUtils.toString(errorStream, StandardCharsets.UTF_8);
            out = IOUtils.toString(outStream, StandardCharsets.UTF_8);
            log.info("out: " + out);
            if (!errors.isEmpty()) {
                log.error("errors: " + errors);
                return errors;
            }
            return out;
        } catch (Exception e) {
            errorStream.close();
            outStream.close();
            log.error("Error during process: " + e.getLocalizedMessage());
        }
        return "Error during process..";
    }

    @Override
    public String executeDumpQuery(String filename, Query query) {
        Command command = getCommandWithFileName(filename);
        setQuery(command, query);
        return String.format("Executed command %s for filename %s \n\n with output %s\n\n",
                query.getQuery(),
                filename,
                executeCommand(getCommands(command)));
    }

    public String executeUserQuery(String userQuery) {
        Command command = getCommandWithUserQuery(userQuery);
        setQuery(command, Query.USER_QUERY);
        return String.format("Executed query %s \n\n with output %s\n\n",
                userQuery,
                executeCommand(getCommands(command)));
    }

    @Override
    public String executeQuery(Query query) {
        Command command = getBaseCommand();
        setQuery(command, query);
        return executeCommand(getCommands(command));
    }

    public String[] getCommands(Command command) {
        List<String> result = new ArrayList<>();
        result.add(BASE_COMMAND);

        command.getParams().forEach((k, v) -> {
            if (k.contains("-")) {
                result.add(k);
                result.add(v);
            }
        });

        return result.toArray(new String[0]);
    }

    @Data
    static class Command {
        private HashMap<String, String> params;
    }

    private void setQuery(Command command, Query query) {
        command.getParams().put(QUERY_KEY.getKey(), getQueryWithParams(command, query));
    }

    private String getQueryWithParams(Command command, Query query) {
        String result = query.getQuery().replace(DATABASE_KEY.getKey(), command.getParams().get(DATABASE_KEY.getKey()));
        result = result.replace(DIRECTORY_KEY.getKey(), command.getParams().get(DIRECTORY_KEY.getKey()) +
                command.getParams().get(FILENAME_KEY.getKey()));

        if (command.getParams().get(USER_QUERY_KEY.getKey()) != null) {
            result = result.replace(USER_QUERY_KEY.getKey(), command.getParams().get(USER_QUERY_KEY.getKey()));
        }
        return result;
    }

    public Command getCommandWithFileName(String filename) {
        Command command = getBaseCommand();
        command.getParams().put(FILENAME_KEY.getKey(), filename);

        return command;
    }

    public Command getCommandWithUserQuery(String query) {
        Command command = getBaseCommand();
        command.getParams().put(USER_QUERY_KEY.getKey(), query);

        return command;
    }

    public Command getBaseCommand() {
        Command command = new Command();

        command.setParams(new HashMap<String, String>() {{
            put(DIRECTORY_KEY.getKey(), directory);
            put(DATABASE_KEY.getKey(), database);
            put(PASSWORD_KEY.getKey(), password);
            put(USER_KEY.getKey(), username);
            put(SERVER_KEY.getKey(), server);
        }});

        log.info("Created command with params: " + command.getParams());

        return command;
    }

    @Override
    public String restore(String databaseName) {
        String initialCheck = initialCheck(databaseName);
        if (initialCheck != null) {
            return initialCheck;
        }

        List<Dump> dumps = downloadDumpList(databaseName);
        String dumpsCheck = dumpsCheck(dumps);
        if (dumpsCheck != null) {
            return dumpsCheck;
        }

        String result = executeRestoreDumps(getDownloadedDumpsForeRestore(dumps)).stream()
                .collect(Collectors.joining(""));

        setIfRequiredUserAccess(databaseName);

        return result;
    }

    private String initialCheck(String databaseName) {
        CheckResult compatibility = checkCompatibility();
        if (!compatibility.isCorrect()) {
            return compatibility.getMessage();
        }

        CheckResult availability = checkAvailability(databaseName);
        if (!availability.isCorrect()) {
            return availability.getMessage();
        }

        return null;
    }

    private String dumpsCheck(List<Dump> dumps) {
        if (dumps.isEmpty()) {
            return NO_DUMPS;
        }

        CheckResult freeSpace = checkFreeSpace(getDrivers(dumps), getTotalSize(dumps));
        if (!freeSpace.isCorrect()) {
            return freeSpace.getMessage();
        }

        CheckResult existsFiles = checkExistingFiles(dumps);
        if (!existsFiles.isCorrect()) {
            return existsFiles.getMessage();
        }

        return null;
    }

    private List<ShortDump> getDownloadedDumpsForeRestore(List<Dump> dumps) {
        List<ShortDump> dumpsForRestore = new ArrayList<>();
        for (Dump dump : dumps) {
            String[] filename = dump.getFilename().split("/");
            String name = filename[filename.length - 1];
            downloadFile(downloadUrl + name, directory + name);
            dumpsForRestore.add(new ShortDump(name, dump.getType()));
        }
        return dumpsForRestore;
    }

    @Override
    public List<String> executeRestoreDumps(List<ShortDump> dumps) {
        List<String> result = new ArrayList<>();
        log.info("Start restoring.. ");

        for (ShortDump dump : dumps) {
            switch (dump.getType()) {
                case 'D':
                    result.add(executeDumpQuery(dump.getFilename(), Query.RESTORE_FULL));
                    break;
                case 'I':
                    result.add(executeDumpQuery(dump.getFilename(), Query.RESTORE_DIFFERENTIAL));
                    break;
                case 'L':
                    result.add(executeDumpQuery(dump.getFilename(), Query.RESTORE_LOG));
                    break;
                default:
                    break;
            }
        }
        result.add(executeQuery(Query.RECOVERY));

        return result;
    }


    private void downloadFile(String url, String outputFilename) {
        log.info("Downloading from " + url + " to " + outputFilename);
        try {
            FileUtils.copyURLToFile(
                    new URL(url),
                    new File(outputFilename),
                    CONNECT_TIMEOUT,
                    READ_TIMEOUT);
        } catch (IOException e) {
            log.error("during downloading " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<Dump> downloadDumpList(String databaseName) {
        String response = restTemplate.getForObject(actualDumpsUrl + "?databaseName=" + databaseName, String.class);

        log.info("Received dump list " + response);

        List<Dump> dumps = null;

        try {
            dumps = mapper.readValue(response, new TypeReference<List<Dump>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("An error occurred while reading dump list " + e.getMessage());
        }

        return dumps;
    }

    @Override
    public int getVersion() {
        String version = repository.getVersion();

        Pattern pattern = Pattern.compile("Server \\d{4}");
        Matcher matcher = pattern.matcher(version);

        return matcher.find() ? Integer.parseInt(matcher.group().split(" ")[1]) : -1;
    }

    // todo: лучше сделать явную проверку через compatibility
    // https://docs.microsoft.com/ru-ru/sql/t-sql/statements/alter-database-transact-sql-compatibility-level?view=sql-server-ver15
    @Override
    public CheckResult checkCompatibility() {
        int serverVersion = restTemplate.getForObject(versionUrl, Integer.class);
        log.info("Received server mssql version " + serverVersion);

        int localVersion = getVersion();
        log.info("Received local mssql version " + localVersion);

        return new CheckResult(true,
                String.format("Incompatible ms sql version: %d. Server version: %d", localVersion, serverVersion));
    }

    @Override
    public boolean isSameGroup(int a, int b) {
        return Version.FIRST_GROUP.getVersions().contains(a) && Version.FIRST_GROUP.getVersions().contains(b) ||
                Version.SECOND_GROUP.getVersions().contains(a) && Version.SECOND_GROUP.getVersions().contains(b);
    }

    @Override
    public CheckResult checkAvailability(String databaseName) {
        int count = repository.getCountConnectionsForDatabase(databaseName);
        log.info("Count " + count + " connections to database " + databaseName);

        return new CheckResult(count == 0,
                String.format("Drop %d connections to database %s", count, databaseName));
    }

    @Override
    public void setIfRequiredUserAccess(String databaseName) {
        if (UserAccess.MULTI_USER.getCode() != repository.getUserAccess(databaseName)) {
            executeQuery(Query.USE_MASTER);
            executeQuery(Query.SET_MULTI_USER);
            executeQuery(Query.USE_MSDB);
        }
    }

    private BigDecimal getTotalSize(List<Dump> dumps) {
        BigDecimal total = dumps.stream()
                .map(x -> x.getBackupSize())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info(String.format("Total size of dumps: %s bytes", total));

        return total;
    }

    // todo: непонятно как на windows будет работать
    private List<String> getDrivers(List<Dump> dumps) {
        List<String> drivers = dumps.stream()
                .map(x -> x.getFilename())
                .distinct()
                .collect(Collectors.toList());
        log.info("Got drivers: " + drivers.stream().collect(Collectors.joining(", ")));

        return drivers;
    }

    public CheckResult checkFreeSpace(List<String> drivers, BigDecimal sizeOfDumps) {
        long freeSpace = drivers.stream()
                .map(x -> new File(x).getFreeSpace())
                .reduce(0L, Long::sum);

        String disks = drivers.stream().collect(Collectors.joining(", "));
        log.info(String.format("Free space of disk %s ", disks));

        return new CheckResult(BigDecimal.valueOf(freeSpace).compareTo(sizeOfDumps) > 0,
                String.format("Not enough free space on disk %s . Size of dumps is %s. Free space is %s", disks, sizeOfDumps, freeSpace));
    }

    public CheckResult checkExistingFiles(List<Dump> dumps) {
        List<Dump> dumpsWithExistingFile = dumps.stream()
                .filter(x -> {
                    File file = new File(x.getPhysicalName());
                    if (file.exists() && !file.isDirectory()) {
                        return true;
                    } else {
                        return false;
                    }
                }).collect(Collectors.toList());

        String files = dumpsWithExistingFile.stream()
                .map(x -> x.getPhysicalName())
                .collect(Collectors.joining(", "));

        String out = String.format("Dumps with existing file %s ", files);
        log.info(out);

        return new CheckResult(dumpsWithExistingFile.isEmpty(), out);
    }
}
