package com.dumper.server.service.impl;

import com.dumper.server.entity.CheckResult;
import com.dumper.server.entity.Dump;
import com.dumper.server.entity.ShortDump;
import com.dumper.server.enums.Query;
import com.dumper.server.enums.UserAccess;
import com.dumper.server.enums.Version;
import com.dumper.server.repository.BackupsetRepository;
import com.dumper.server.service.CommandService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.dumper.server.enums.Key.*;

@Slf4j
@RequiredArgsConstructor
@Service
@PropertySource("classpath:application.properties")
public class DumpServiceImpl implements DumpService {

    @Value(value = "${directory:directory}")
    private String directory;

    @Value(value = "${api.server.url}")
    private String actualDumpsUrl;

    @Value("${api.server.version.url}")
    private String versionUrl;

    @Value(value = "${api.server.download.url}")
    private String downloadUrl;

    private final BackupsetRepository repository;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;
    private static final String NO_DUMPS = "No dumps available";

    /**
     * Первоначальные проверки, перед скачиванием дампов
     * @param databaseName
     * @return
     */
    @Override
    public String initialCheck(String databaseName) {
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

    /**
     * Проверки с полученными дампами
     * @param dumps
     * @return
     */
    @Override
    public String dumpsCheck(List<Dump> dumps) {
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

    /**
     * Получаем список дампов для восставновления
     * @param dumps
     * @return
     */
    @Override
    public List<ShortDump> getDownloadedDumpsForeRestore(List<Dump> dumps) {
        List<ShortDump> dumpsForRestore = new ArrayList<>();
        for (Dump dump : dumps) {
            String[] filename = dump.getFilename().split("/");
            String name = filename[filename.length - 1];
            downloadFile(downloadUrl + name, directory + name);
            dumpsForRestore.add(new ShortDump(name, dump.getType()));
        }
        return dumpsForRestore;
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

    /**
     * Скачиваем список актуальных дампов
     * @param databaseName
     * @return
     */
    @Override
    public List<Dump> downloadDumpList(String databaseName) {
        String response = restTemplate.getForObject(actualDumpsUrl + "?databaseName=" + databaseName, String.class);

        log.info("Received dump list " + response);

        List<Dump> dumps = new ArrayList<>();

        try {
            dumps = mapper.readValue(response, new TypeReference<List<Dump>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("An error occurred while reading dump list " + e.getMessage());
        }

        return dumps;
    }

    private int getVersion() {
        String version = repository.getVersion();

        Pattern pattern = Pattern.compile("Server \\d{4}");
        Matcher matcher = pattern.matcher(version);

        return matcher.find() ? Integer.parseInt(matcher.group().split(" ")[1]) : -1;
    }

    // todo: лучше сделать явную проверку через compatibility
    // https://docs.microsoft.com/ru-ru/sql/t-sql/statements/alter-database-transact-sql-compatibility-level?view=sql-server-ver15
    private CheckResult checkCompatibility() {
        int serverVersion = restTemplate.getForObject(versionUrl, Integer.class);
        log.info("Received server mssql version " + serverVersion);

        int localVersion = getVersion();
        log.info("Received local mssql version " + localVersion);

        return new CheckResult(isSameGroup(serverVersion, localVersion),
                String.format("Incompatible ms sql version: %d. Server version: %d", localVersion, serverVersion));
    }

    private boolean isSameGroup(int a, int b) {
        return Version.FIRST_GROUP.getVersions().contains(a) && Version.FIRST_GROUP.getVersions().contains(b) ||
                Version.SECOND_GROUP.getVersions().contains(a) && Version.SECOND_GROUP.getVersions().contains(b);
    }

    private CheckResult checkAvailability(String databaseName) {
        int count = repository.getCountConnectionsForDatabase(databaseName);
        log.info("Count " + count + " connections to database " + databaseName);

        return new CheckResult(count == 0,
                String.format("Drop %d connections to database %s", count, databaseName));
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

    private CheckResult checkFreeSpace(List<String> drivers, BigDecimal sizeOfDumps) {
        long freeSpace = drivers.stream()
                .map(x -> new File(x).getFreeSpace())
                .reduce(0L, Long::sum);

        String disks = drivers.stream().collect(Collectors.joining(", "));
        log.info(String.format("Free space of disk %s ", disks));

        return new CheckResult(BigDecimal.valueOf(freeSpace).compareTo(sizeOfDumps) > 0,
                String.format("Not enough free space on disk %s . Size of dumps is %s. Free space is %s", disks, sizeOfDumps, freeSpace));
    }

    private CheckResult checkExistingFiles(List<Dump> dumps) {
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
