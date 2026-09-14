package com.manager.app.comissao;

import com.manager.app.comissao.dto.ComissaoRequestDTO;
import com.manager.app.comissao.dto.ComissaoResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/comissoes")
public class ComissaoController {

    private final ComissaoService service;
    private final ComissaoMapper mapper;

    @GetMapping
    public ResponseEntity<Page<ComissaoResponseDTO>> findAll(@PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<ComissaoResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COMISSAO_GERENCIAR')")
    public ResponseEntity<ComissaoResponseDTO> create(@RequestBody @Valid ComissaoRequestDTO requestDTO){
        Comissao comissao = service.create(mapper.toEntity(requestDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(comissao.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(comissao));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('COMISSAO_GERENCIAR')")
    public ResponseEntity<ComissaoResponseDTO> update(@RequestBody @Valid ComissaoRequestDTO requestDTO){
        Comissao comissao = service.update(mapper.toEntity(requestDTO));
        return ResponseEntity.ok(mapper.toResponseDTO(comissao));
    }

    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('COMISSAO_GERENCIAR')")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
