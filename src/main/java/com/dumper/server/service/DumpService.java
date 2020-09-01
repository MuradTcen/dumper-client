package com.dumper.server.service;

import com.dumper.server.entity.Dump;
import com.dumper.server.entity.ShortDump;

import java.util.List;

public interface DumpService {

    String initialCheck(String databaseName, String path);

    String dumpsCheck(List<Dump> dumps);

    List<ShortDump> getDownloadedDumpsForRestore(List<Dump> dumps, String path);

    List<Dump> downloadDumpList(String databaseName, String date);

}
