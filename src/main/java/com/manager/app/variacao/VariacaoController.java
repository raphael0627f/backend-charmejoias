package com.manager.app.variacao;

import com.manager.app.variacao.dto.VariacaoDTO;
import com.manager.app.shared.ModoBusca;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/variacoes")
public class VariacaoController {

    private final VariacaoService service;
    private final VariacaoMapper mapper;

    @GetMapping
    public ResponseEntity<Page<VariacaoDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) ModoBusca modoBusca,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, modoBusca, pageable).map(mapper::toDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<VariacaoDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<VariacaoDTO> create(@RequestBody @Valid VariacaoDTO variacaoDTO){
        Variacao variacao = service.create(mapper.toEntity(variacaoDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(variacao.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toDTO(variacao));
    }

    @PutMapping
    public ResponseEntity<VariacaoDTO> update(@RequestBody @Valid VariacaoDTO variacaoDTO){
        Variacao variacao = service.update(mapper.toEntity(variacaoDTO));
        return ResponseEntity.ok(mapper.toDTO(variacao));
    }

    @PutMapping(value = "activate/{id}")
    public ResponseEntity<VariacaoDTO> activate(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toDTO(service.activate(id)));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(value = "/{variacaoId}/itens/{itemId}")
    public ResponseEntity<VariacaoDTO> deleteItem(@PathVariable Long variacaoId, @PathVariable Long itemId){
        return ResponseEntity.ok(mapper.toDTO(service.deleteItem(variacaoId, itemId)));
    }
}
