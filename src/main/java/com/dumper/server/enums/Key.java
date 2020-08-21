package com.dumper.server.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Key {

    PASSWORD_KEY("-P"),
    QUERY_KEY("-Q"),
    USER_KEY("-U"),
    SERVER_KEY("-S"),
    USER_QUERY_KEY("user_query"),
    DIRECTORY_KEY("directory"),
    DATABASE_KEY("database"),
    FILENAME_KEY("filename");

    private final String key;
}
