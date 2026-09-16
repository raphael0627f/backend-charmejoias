package com.manager.app.usuario;

import com.manager.app.usuario.dto.PerfilRequestDTO;
import com.manager.app.usuario.dto.PerfilResponseDTO;
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
@RequestMapping(value = "/perfis")
public class PerfilController {

    private final PerfilService service;
    private final PerfilMapper mapper;

    @GetMapping
    public ResponseEntity<Page<PerfilResponseDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) ModoBusca modoBusca,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, modoBusca, pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<PerfilResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
    public ResponseEntity<PerfilResponseDTO> create(@RequestBody @Valid PerfilRequestDTO requestDTO){
        Perfil perfil = service.create(mapper.toEntity(requestDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(perfil.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(perfil));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
    public ResponseEntity<PerfilResponseDTO> update(@RequestBody @Valid PerfilRequestDTO requestDTO){
        Perfil perfil = service.update(mapper.toEntity(requestDTO));
        return ResponseEntity.ok(mapper.toResponseDTO(perfil));
    }

    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
