package com.dumper.server.service;

import com.dumper.server.entity.CheckResult;
import com.dumper.server.entity.Dump;
import com.dumper.server.entity.ShortDump;

import java.math.BigDecimal;
import java.util.List;

public interface DumpService {
    String initialCheck(String databaseName);

    String dumpsCheck(List<Dump> dumps);

    List<ShortDump> getDownloadedDumpsForeRestore(List<Dump> dumps);

    void downloadFile(String url, String outputFilename);

    List<Dump> downloadDumpList(String databaseName);

    int getVersion();

    CheckResult checkCompatibility();

    boolean isSameGroup(int a, int b);

    CheckResult checkAvailability(String databaseName);

    BigDecimal getTotalSize(List<Dump> dumps);

    List<String> getDrivers(List<Dump> dumps);

    CheckResult checkFreeSpace(List<String> drivers, BigDecimal sizeOfDumps);

    CheckResult checkExistingFiles(List<Dump> dumps);
}
