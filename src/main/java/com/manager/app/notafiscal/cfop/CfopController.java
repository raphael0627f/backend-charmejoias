package com.manager.app.notafiscal.cfop;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/cfop")
public class CfopController {

    private final CfopService service;

    @GetMapping
    public ResponseEntity<List<Cfop>> findAll(){
        return ResponseEntity.ok(service.findAll());
    }
}
