package com.manager.app.controller;

import com.manager.app.dto.produto.ProdutoRequestDTO;
import com.manager.app.dto.produto.ProdutoRelacoesDTO;
import com.manager.app.dto.produto.ProdutoResponseDTO;
import com.manager.app.entity.Produto;
import com.manager.app.mapper.ProdutoMapper;
import com.manager.app.service.ProdutoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping(value="/produtos")
public class ProdutoController {

    private final ProdutoService service;
    private final ProdutoMapper mapper;

    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> findAll(){
        return ResponseEntity.ok(service.findAll().stream().map(mapper::toResponseDTO).collect(Collectors.toList()));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<ProdutoResponseDTO> findById(@PathVariable UUID id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> create(@RequestBody @Valid ProdutoRequestDTO produtoRequestDTO){
        ProdutoRelacoesDTO relacoes = new ProdutoRelacoesDTO(produtoRequestDTO.getCategoriaId(), produtoRequestDTO.getFornecedorId(), produtoRequestDTO.getGruposIds());
        Produto produto = service.create(mapper.toEntity(produtoRequestDTO), relacoes);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(produto.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(produto));
    }

    @PutMapping
    public ResponseEntity<ProdutoResponseDTO> update(@RequestBody @Valid ProdutoRequestDTO produtoRequestDTO){
        ProdutoRelacoesDTO relacoes = new ProdutoRelacoesDTO(produtoRequestDTO.getCategoriaId(), produtoRequestDTO.getFornecedorId(), produtoRequestDTO.getGruposIds());
        Produto produto = service.update(mapper.toEntity(produtoRequestDTO), relacoes);
        return ResponseEntity.ok(mapper.toResponseDTO(produto));
    }

    @PutMapping(value = "activate/{id}")
    public ResponseEntity<ProdutoResponseDTO> activate(@PathVariable UUID id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.activate(id)));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
