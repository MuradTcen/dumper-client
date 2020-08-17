package com.dumper.server.service;

import com.dumper.server.enums.Query;

public interface DumpService {
    void executeCommand(String[] command);

    void executeQuery(String filename, Query query);
}
