package com.dumper.server.service;

import com.dumper.server.entity.Dump;
import com.dumper.server.entity.ShortDump;

import java.util.List;

public interface DumpService {

    String initialCheck(String databaseName);

    String dumpsCheck(List<Dump> dumps);

    List<ShortDump> getDownloadedDumpsForeRestore(List<Dump> dumps);

    List<Dump> downloadDumpList(String databaseName);

}
