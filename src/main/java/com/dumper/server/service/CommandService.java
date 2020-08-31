package com.dumper.server.service;

import com.dumper.server.entity.ShortDump;
import com.dumper.server.enums.Query;

import java.io.IOException;
import java.util.List;

public interface CommandService {
    String executeCommand(String[] command) throws IOException;

    String executeDumpQuery(String filename, Query query);

    String executeUserQuery(String userQuery);

    String restore(String databaseName, String path);

    List<String> executeRestoreDumps(List<ShortDump> dumps);

    void setIfRequiredUserAccess(String databaseName);
}
