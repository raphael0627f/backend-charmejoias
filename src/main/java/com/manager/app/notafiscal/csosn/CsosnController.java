package com.manager.app.notafiscal.csosn;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/csosn")
public class CsosnController {

    private final CsosnService service;

    @GetMapping
    public ResponseEntity<List<Csosn>> findAll(){
        return ResponseEntity.ok(service.findAll());
    }
}
