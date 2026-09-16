package com.manager.app.usuario;

import com.manager.app.usuario.dto.UsuarioRequestDTO;
import com.manager.app.usuario.dto.UsuarioResponseDTO;
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
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/usuarios")
public class UsuarioController {

    private final UsuarioService service;
    private final UsuarioMapper mapper;

    @GetMapping
    public ResponseEntity<Page<UsuarioResponseDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) ModoBusca modoBusca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 20, sort = "usuario") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, modoBusca, ativo, pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<UsuarioResponseDTO> findById(@PathVariable UUID id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
    public ResponseEntity<UsuarioResponseDTO> create(@RequestBody @Valid UsuarioRequestDTO requestDTO){
        Usuario usuario = service.create(mapper.toEntity(requestDTO), requestDTO.getSenha(), requestDTO.getPerfilId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(usuario.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(usuario));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
    public ResponseEntity<UsuarioResponseDTO> update(@RequestBody @Valid UsuarioRequestDTO requestDTO){
        Usuario usuario = service.update(mapper.toEntity(requestDTO), requestDTO.getSenha(), requestDTO.getPerfilId());
        return ResponseEntity.ok(mapper.toResponseDTO(usuario));
    }

    @PutMapping(value = "activate/{id}")
    @PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
    public ResponseEntity<UsuarioResponseDTO> activate(@PathVariable UUID id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.activate(id)));
    }

    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
