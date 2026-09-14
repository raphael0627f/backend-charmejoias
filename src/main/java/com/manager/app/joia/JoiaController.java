package com.manager.app.joia;

import com.manager.app.joia.dto.JoiaRelacoesDTO;
import com.manager.app.joia.dto.JoiaRequestDTO;
import com.manager.app.joia.dto.JoiaResponseDTO;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value="/joias")
public class JoiaController {

    private final JoiaService service;
    private final JoiaMapper mapper;

    @GetMapping
    public ResponseEntity<Page<JoiaResponseDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) Long categoriaId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, ativo, categoriaId, pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/busca-exata")
    public ResponseEntity<List<JoiaResponseDTO>> buscaExata(@RequestParam String termo){
        return ResponseEntity.ok(service.buscarExata(termo).stream().map(mapper::toResponseDTO).toList());
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<JoiaResponseDTO> findById(@PathVariable UUID id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('JOIA_GERENCIAR')")
    public ResponseEntity<JoiaResponseDTO> create(@RequestBody @Valid JoiaRequestDTO joiaRequestDTO){
        JoiaRelacoesDTO relacoes = new JoiaRelacoesDTO(joiaRequestDTO.getCategoriaId(), joiaRequestDTO.getGruposIds());
        Joia joia = service.create(mapper.toEntity(joiaRequestDTO), relacoes);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(joia.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(joia));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('JOIA_GERENCIAR')")
    public ResponseEntity<JoiaResponseDTO> update(@RequestBody @Valid JoiaRequestDTO joiaRequestDTO){
        JoiaRelacoesDTO relacoes = new JoiaRelacoesDTO(joiaRequestDTO.getCategoriaId(), joiaRequestDTO.getGruposIds());
        Joia joia = service.update(mapper.toEntity(joiaRequestDTO), relacoes);
        return ResponseEntity.ok(mapper.toResponseDTO(joia));
    }

    @PutMapping(value = "activate/{id}")
    @PreAuthorize("hasAuthority('JOIA_GERENCIAR')")
    public ResponseEntity<JoiaResponseDTO> activate(@PathVariable UUID id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.activate(id)));
    }

    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('JOIA_GERENCIAR')")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
