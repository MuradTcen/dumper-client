package com.dumper.server.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckResult {

    private final boolean correct;
    private final String message;
}
