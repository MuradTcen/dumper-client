package com.dumper.server.controller;

import com.dumper.server.service.impl.CommandServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dump")
@RequiredArgsConstructor
@Slf4j
public class DumpController {

    private final CommandServiceImpl dumpService;

    @GetMapping(path = "start-restore")
    public ResponseEntity<String> tryRestoreDumps(@RequestParam String databaseName,
                                                  @RequestParam(required = false) String path) {
        return ResponseEntity.ok().body(dumpService.restore(databaseName, path));
    }

    @GetMapping(path = "execute-query")
    public ResponseEntity<String> tryExecuteQuery(@RequestParam String query) {
        return ResponseEntity.ok().body(dumpService.executeUserQuery(query));
    }
}
