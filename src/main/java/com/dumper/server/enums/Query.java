package com.dumper.server.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Query {

    VERSION("SELECT @@VERSION"),
    USE_MASTER("USE master"),
    USE_MSDB("USE masdb"),
    SET_MULTI_USER("ALTER DATABASE [database] SET MULTI_USER"),
    CHECK_AVAILABILITY("SELECT COUNT(*) FROM sys.sysprocesses WHERE dbid = DB_ID([database])"),

    FULL_BACKUP("BACKUP DATABASE [database] TO DISK = N'directory' WITH COPY_ONLY, STATS = 10"),
    DIFFERENTIAL_BACKUP("BACKUP DATABASE [database] TO DISK = N'directory' WITH DIFFERENTIAL, STATS = 10"),

    RESTORE_FULL("RESTORE DATABASE [database] FROM DISK = N'directory' WITH REPLACE, NORECOVERY"),
    RESTORE_FULL_RECOVERY("RESTORE DATABASE [database] FROM DISK = N'directory' WITH REPLACE, RECOVERY"),
    RESTORE_DIFFERENTIAL("RESTORE DATABASE [database] FROM DISK = N'directory' WITH RECOVERY"),

    LOG_BACKUP("BACKUP LOG [database] TO DISK = N'directory' WITH NOFORMAT, INIT, STATS = 10"),
    RESTORE_LOG("RESTORE LOG [database] FROM DISK = N'directory' WITH STATS = 10");

    private final String query;
}
