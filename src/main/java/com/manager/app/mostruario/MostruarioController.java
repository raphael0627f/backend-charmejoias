package com.manager.app.mostruario;

import com.manager.app.mostruario.dto.MostruarioRequestDTO;
import com.manager.app.mostruario.dto.MostruarioResponseDTO;
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
@RequestMapping(value = "/mostruarios")
public class MostruarioController {

    private final MostruarioService service;
    private final MostruarioMapper mapper;

    @GetMapping
    public ResponseEntity<Page<MostruarioResponseDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) ModoBusca modoBusca,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, modoBusca, pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<MostruarioResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MOSTRUARIO_GERENCIAR')")
    public ResponseEntity<MostruarioResponseDTO> create(@RequestBody @Valid MostruarioRequestDTO requestDTO){
        Mostruario mostruario = service.create(mapper.toEntity(requestDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(mostruario.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(mostruario));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('MOSTRUARIO_GERENCIAR')")
    public ResponseEntity<MostruarioResponseDTO> update(@RequestBody @Valid MostruarioRequestDTO requestDTO){
        Mostruario mostruario = service.update(mapper.toEntity(requestDTO));
        return ResponseEntity.ok(mapper.toResponseDTO(mostruario));
    }

    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('MOSTRUARIO_GERENCIAR')")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
