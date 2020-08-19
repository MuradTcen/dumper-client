package com.dumper.server.repository;

import com.dumper.server.entity.Backupset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BackupsetRepository extends JpaRepository<Backupset, Long> {

    @Query(
            value = "select @@version",
            nativeQuery = true)
    String getVersion();


    @Query(
            value = "select compatibility_level from master.sys.databases where name = ?1",
            nativeQuery = true)
    int getCompatibilityLevel(String databaseName);

    @Query(
            value = "select user_access from master.sys.databases where name = ?1",
            nativeQuery = true)
    int getUserAccess(String databaseName);

    @Query(
            value = "select count(*) from sys.sysprocesses where dbid = DB_ID(N'?1')",
            nativeQuery = true)
    int getCountConnectionsForDatabase(String databaseName);
}
