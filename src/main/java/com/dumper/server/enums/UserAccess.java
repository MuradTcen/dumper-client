package com.dumper.server.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
public enum UserAccess {

    MULTI_USER("MULTI_USER", 0),
    SINGLE_USER("SINGLE_USER", 1),
    RESTRICTED_USER("RESTRICTED_USER", 2);

    private final String description;
    private final int code;
}
