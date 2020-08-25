package com.dumper.server.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Query {

    VERSION("SELECT @@VERSION"),
    USE_MASTER("USE master"),
    USE_MSDB("USE msdb"),
    USER_QUERY("user_query"),
    SET_MULTI_USER("ALTER DATABASE [database] SET MULTI_USER"),
    CHECK_AVAILABILITY("SELECT COUNT(*) FROM sys.sysprocesses WHERE dbid = DB_ID([database])"),

    FULL_BACKUP("BACKUP DATABASE [database] TO DISK = N'directory' WITH COPY_ONLY"),
    DIFFERENTIAL_BACKUP("BACKUP DATABASE [database] TO DISK = N'directory' WITH DIFFERENTIAL"),

    RESTORE_FULL("RESTORE DATABASE [database] FROM DISK = N'directory' WITH REPLACE, NORECOVERY"),
    RESTORE_DIFFERENTIAL("RESTORE DATABASE [database] FROM DISK = N'directory' WITH NORECOVERY"),
    RECOVERY("RESTORE DATABASE [database] WITH RECOVERY"),

    LOG_BACKUP("BACKUP LOG [database] TO DISK = N'directory'"),
    RESTORE_LOG("RESTORE LOG [database] FROM DISK = N'directory' WITH NORECOVERY");

    private final String query;
}
