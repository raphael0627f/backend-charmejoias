package com.manager.app.grupo;

import com.manager.app.shared.ModoBusca;
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
@RequestMapping(value="/grupos")
public class GrupoController {

    private final GrupoService service;
    private final GrupoMapper mapper;

    @GetMapping
    public ResponseEntity<Page<GrupoDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) ModoBusca modoBusca,
            @RequestParam(required = false) TipoGrupo tipo,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, modoBusca, tipo, pageable).map(mapper::toDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<GrupoDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GRUPO_GERENCIAR')")
    public ResponseEntity<GrupoDTO> create(@RequestBody @Valid GrupoDTO grupoDTO){
        Grupo grupo = service.create(mapper.toEntity(grupoDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(grupo.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toDTO(grupo));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('GRUPO_GERENCIAR')")
    public ResponseEntity<GrupoDTO> update(@RequestBody @Valid GrupoDTO grupoDTO){
        Grupo grupo = service.update(mapper.toEntity(grupoDTO));
        return ResponseEntity.ok(mapper.toDTO(grupo));
    }

    @PutMapping(value = "activate/{id}")
    @PreAuthorize("hasAuthority('GRUPO_GERENCIAR')")
    public ResponseEntity<GrupoDTO> activate(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toDTO(service.activate(id)));
    }

    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('GRUPO_GERENCIAR')")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
