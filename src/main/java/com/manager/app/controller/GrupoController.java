package com.manager.app.controller;

import com.manager.app.dto.GrupoDTO;
import com.manager.app.entity.Grupo;
import com.manager.app.mapper.GrupoMapper;
import com.manager.app.service.GrupoService;
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
@RequestMapping(value="/grupos")
public class GrupoController {

    private final GrupoService service;
    private final GrupoMapper mapper;

    @GetMapping
    public ResponseEntity<List<GrupoDTO>> findAll(){
        return ResponseEntity.ok(service.findAll().stream().map(mapper::toDTO).collect(Collectors.toList()));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<GrupoDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<GrupoDTO> create(@RequestBody @Valid GrupoDTO grupoDTO){
        Grupo grupo = service.create(mapper.toEntity(grupoDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(grupo.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toDTO(grupo));
    }

    @PutMapping
    public ResponseEntity<GrupoDTO> update(@RequestBody @Valid GrupoDTO grupoDTO){
        Grupo grupo = service.update(mapper.toEntity(grupoDTO));
        return ResponseEntity.ok(mapper.toDTO(grupo));
    }

    @PutMapping(value = "activate/{id}")
    public ResponseEntity<GrupoDTO> activate(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toDTO(service.activate(id)));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
