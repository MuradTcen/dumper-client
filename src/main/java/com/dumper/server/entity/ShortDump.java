package com.dumper.server.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ShortDump {

    private String filename;
    private char type;

}
