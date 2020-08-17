package com.dumper.server.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Query {

    BACKUP("BACKUP DATABASE [database] TO DISK = N'directory' WITH COPY_ONLY, STATS = 10"),
    DIFFERENTIAL_BACKUP("BACKUP DATABASE [database] TO DISK = N'directory' WITH DIFFERENTIAL, STATS = 10"),
    RESTORE("RESTORE DATABASE [database] FROM DISK = N'directory' WITH REPLACE"),
    RESTORE_DIFFERENTIAL("RESTORE DATABASE [database] FROM DISK = N'directory' WITH NORECOVERY");

    private final String query;
}
