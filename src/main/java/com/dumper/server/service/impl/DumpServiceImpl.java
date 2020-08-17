package com.dumper.server.service.impl;

import com.dumper.server.entity.Dump;
import com.dumper.server.enums.Query;
import com.dumper.server.enums.Version;
import com.dumper.server.repository.BackupsetRepository;
import com.dumper.server.service.DumpService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    int CONNECT_TIMEOUT = 10000;
    int READ_TIMEOUT = 10000;

    @Override
    public void executeCommand(String[] command) {
        Runtime runtime = Runtime.getRuntime();
        try {
            log.info("Start executing command: " + Arrays.toString(command));

            Process proc = runtime.exec(command);
            proc.waitFor();
            InputStream errorStream = proc.getErrorStream();
            InputStream outStream = proc.getInputStream();
            String errors = IOUtils.toString(errorStream, StandardCharsets.UTF_8);
            String out = IOUtils.toString(outStream, StandardCharsets.UTF_8);
            log.info("out: " + out);
            if (!errors.isEmpty()) {
                log.error("errors: " + errors);
            }
        } catch (Exception e) {
            log.error("Error during process: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void executeQuery(String filename, Query query) {
        Command command = getBaseCommand(filename);
        setQuery(command, query);
        executeCommand(getCommands(command));
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
        return query.getQuery().replace(DATABASE_KEY.getKey(), command.getParams().get(DATABASE_KEY.getKey()))
                .replace(DIRECTORY_KEY.getKey(), command.getParams().get(DIRECTORY_KEY.getKey()) +
                        command.getParams().get(FILENAME_KEY.getKey()));
    }

    public Command getBaseCommand(String filename) {
        Command command = new Command();

        command.setParams(new HashMap<String, String>() {{
            put(DIRECTORY_KEY.getKey(), directory);
            put(DATABASE_KEY.getKey(), database);
            put(FILENAME_KEY.getKey(), filename);
            put(PASSWORD_KEY.getKey(), password);
            put(USER_KEY.getKey(), username);
            put(SERVER_KEY.getKey(), server);
        }});

        log.info("Created command with params: " + command.getParams());

        return command;
    }

    public void downloadActualDumps() {
        if (isCompatible()) {
            for (Dump dump : downloadDumpList()) {
                String[] filename = dump.getFilename().split("/");
                String name = filename[filename.length - 1];
                downloadFile(downloadUrl + name, directory + name);
            }
        } else {
            log.error("The server version is not compatible");
        }
    }

    private void downloadFile(String url, String outputFilename) {
        log.info("Donloading from " + url + " to " + outputFilename);
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

    private List<Dump> downloadDumpList() {
        String response = restTemplate.getForObject(actualDumpsUrl, String.class);

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

    public int getVersion() {
        String version = repository.getVersion();

        Pattern pattern = Pattern.compile("Server \\d{4}");
        Matcher matcher = pattern.matcher(version);

        return matcher.find() ? Integer.parseInt(matcher.group().split(" ")[1]) : -1;
    }

    public boolean isCompatible() {
        int serverVersion = restTemplate.getForObject(versionUrl, Integer.class);
        log.info("Received server mssql version " + serverVersion);

        int localVersion = getVersion();
        log.info("Received local mssql version " + serverVersion);

        return isSameGroup(localVersion, serverVersion) && localVersion >= serverVersion;
    }

    private boolean isSameGroup(int a, int b) {
        return Version.FIRST_GROUP.getVersions().contains(a) && Version.FIRST_GROUP.getVersions().contains(b) ||
                Version.SECOND_GROUP.getVersions().contains(a) && Version.SECOND_GROUP.getVersions().contains(b);
    }
}
