package com.dumper.server.controller;

import com.dumper.server.enums.Query;
import com.dumper.server.service.impl.DumpServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/dump")
@RequiredArgsConstructor
@Slf4j
public class DumpController {

    private final DumpServiceImpl dumpService;

    private final static String FULL_POSTFIX = "_full.bck";
    private final static String DIFFERENTIAL_POSTFIX = "_differential.bck";

    @GetMapping(path = "restore")
    public ResponseEntity<String> restoreFullDump() {
        String filename = LocalDate.now() + FULL_POSTFIX;
        dumpService.executeQuery(filename, Query.RESTORE_FULL);

        return ResponseEntity
                .ok().body("Full dump restored");
    }

    @GetMapping(path = "restore-diff")
    public ResponseEntity<String> restoreDifferentialDump() {
        String filename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH")) + DIFFERENTIAL_POSTFIX;
        dumpService.executeQuery(filename, Query.RESTORE_FULL);

        return ResponseEntity
                .ok().body("Differential dump restored");
    }

    @GetMapping(path = "start-restore")
    public ResponseEntity<String> tryRestoreDumps() {

        dumpService.downloadActualDumps();

        return ResponseEntity
                .ok()
                .body("ok");
    }
}
