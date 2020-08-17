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
}
