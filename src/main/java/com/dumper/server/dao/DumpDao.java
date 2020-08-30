package com.dumper.server.dao;

public interface DumpDao {

    int getCountConnectionsForDatabase(String databaseName);

    int getUserAccess(String databaseName);

    int getCompatibilityLevel(String databaseName);

    String getVersion();
}
