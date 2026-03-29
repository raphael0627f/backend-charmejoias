package com.manager.app.controller;

import com.manager.app.dto.CategoriaDTO;
import com.manager.app.entity.Categoria;
import com.manager.app.mapper.CategoriaMapper;
import com.manager.app.service.CategoriaService;
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
@RequestMapping(value="/categorias")
public class CategoriaController {


    private final CategoriaService service;
    private final CategoriaMapper mapper;

    @GetMapping
    public ResponseEntity<List<CategoriaDTO>> findAll(){
        return ResponseEntity.ok(service.findAll().stream().map(mapper::toDTO).collect(Collectors.toList()));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<CategoriaDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<CategoriaDTO> create(@RequestBody @Valid CategoriaDTO categoriaDTO){
        Categoria categoria = service.create(mapper.toEntity(categoriaDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(categoria.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toDTO(categoria));
    }

    @PutMapping
    public ResponseEntity<CategoriaDTO> update(@RequestBody @Valid CategoriaDTO categoriaDTO){
        Categoria categoria = service.update(mapper.toEntity(categoriaDTO));
        return ResponseEntity.ok(mapper.toDTO(categoria));
    }

    @PutMapping(value = "activate/{id}")
    public ResponseEntity<CategoriaDTO> activate(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toDTO(service.activate(id)));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
