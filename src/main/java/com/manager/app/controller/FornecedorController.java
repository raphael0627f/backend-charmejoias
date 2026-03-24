package com.manager.app.controller;

import com.manager.app.dto.FornecedorDTO;
import com.manager.app.entity.Fornecedor;
import com.manager.app.mapper.FornecedorMapper;
import com.manager.app.service.FornecedorService;
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
@RequestMapping(value="/fornecedores")
public class FornecedorController {

    private final FornecedorService service;
    private final FornecedorMapper mapper;

    @GetMapping
    public ResponseEntity<List<FornecedorDTO>> findAll(){
        return ResponseEntity.ok(service.findAll().stream().map(mapper::toDTO).collect(Collectors.toList()));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<FornecedorDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<FornecedorDTO> create(@RequestBody @Valid FornecedorDTO fornecedorDTO){
        Fornecedor fornecedor = service.create(mapper.toEntity(fornecedorDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(fornecedor.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toDTO(fornecedor));
    }

    @PutMapping
    public ResponseEntity<FornecedorDTO> update(@RequestBody @Valid FornecedorDTO fornecedorDTO){
        Fornecedor fornecedor = service.update(mapper.toEntity(fornecedorDTO));
        return ResponseEntity.ok(mapper.toDTO(fornecedor));
    }

    @PutMapping(value = "activate/{id}")
    public ResponseEntity<FornecedorDTO> activate(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toDTO(service.activate(id)));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
