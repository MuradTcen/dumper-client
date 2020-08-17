package com.dumper.server.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DumpType {
    L('L'),
    I('I'),
    D('D');

    private final char name;
}
