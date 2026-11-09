package com.manager.app.agenda;

import com.manager.app.agenda.dto.AgendaRotaRequestDTO;
import com.manager.app.agenda.dto.AgendaRotaResponseDTO;
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
@RequestMapping(value = "/agenda/rotas")
public class AgendaRotaController {

    private final AgendaRotaService service;
    private final AgendaRotaMapper mapper;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('AGENDA_VISUALIZAR', 'AGENDA_GERENCIAR')")
    public ResponseEntity<Page<AgendaRotaResponseDTO>> findAll(
            @RequestParam(required = false) String nome,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable){
        return ResponseEntity.ok(service.findAll(nome, pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/{id}")
    @PreAuthorize("hasAnyAuthority('AGENDA_VISUALIZAR', 'AGENDA_GERENCIAR')")
    public ResponseEntity<AgendaRotaResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('AGENDA_GERENCIAR')")
    public ResponseEntity<AgendaRotaResponseDTO> create(@RequestBody @Valid AgendaRotaRequestDTO request){
        AgendaRota rota = new AgendaRota();
        rota.setNome(request.getNome());
        rota = service.create(rota, request.getCidadeIds());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(rota.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(rota));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('AGENDA_GERENCIAR')")
    public ResponseEntity<AgendaRotaResponseDTO> update(@RequestBody @Valid AgendaRotaRequestDTO request){
        AgendaRota rota = new AgendaRota();
        rota.setId(request.getId());
        rota.setNome(request.getNome());
        return ResponseEntity.ok(mapper.toResponseDTO(service.update(rota, request.getCidadeIds())));
    }

    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('AGENDA_GERENCIAR')")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
