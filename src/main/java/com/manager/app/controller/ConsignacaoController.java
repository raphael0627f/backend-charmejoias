package com.manager.app.controller;

import com.manager.app.dto.*;
import com.manager.app.entity.Consignacao;
import com.manager.app.mapper.ConsignacaoMapper;
import com.manager.app.service.ConsignacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping(value="/consignacoes")
public class ConsignacaoController {

    private final ConsignacaoService service;
    private final ConsignacaoMapper mapper;

    @GetMapping
    public ResponseEntity<List<ConsignacaoResponseDTO>> findAll(){
        return ResponseEntity.ok(service.findAll().stream().map(mapper::toResponseDTO).collect(Collectors.toList()));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<ConsignacaoResponseDTO> findById(@PathVariable long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ConsignacaoResponseDTO> create(@RequestBody @Valid ConsignacaoRequestDTO request){
        Consignacao consignacao = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(consignacao.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(consignacao));
    }

    @PutMapping
    public ResponseEntity<ConsignacaoResponseDTO> update(@RequestBody @Valid ConsignacaoRequestDTO request){
        Consignacao consignacao = service.update(request);
        return ResponseEntity.ok(mapper.toResponseDTO(consignacao));
    }

}
