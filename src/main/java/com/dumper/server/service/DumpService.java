package com.dumper.server.service;

import com.dumper.server.entity.CheckResult;
import com.dumper.server.entity.ShortDump;
import com.dumper.server.enums.Query;

import java.io.IOException;
import java.util.List;

public interface DumpService {
    String executeCommand(String[] command) throws IOException;

    String executeDumpQuery(String filename, Query query);

    String executeQuery(Query query);

    String restore(String databaseName);

    List<String> executeRestoreDumps(List<ShortDump> dumps);

    int getVersion();

    CheckResult checkCompatibility();

    boolean isSameGroup(int a, int b);

    CheckResult checkAvailability(String databaseName);

    void setIfRequiredUserAccess(String databaseName);
}
