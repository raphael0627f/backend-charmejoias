package com.manager.app.agenda;

import com.manager.app.agenda.dto.AgendaMarcadorRequestDTO;
import com.manager.app.agenda.dto.AgendaMarcadorResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/agenda/marcadores")
public class AgendaMarcadorController {

    private final AgendaMarcadorService service;
    private final AgendaMarcadorMapper mapper;

    @GetMapping
    public ResponseEntity<List<AgendaMarcadorResponseDTO>> findByPeriodo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim){
        return ResponseEntity.ok(service.findByPeriodo(inicio, fim).stream().map(mapper::toResponseDTO).toList());
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<AgendaMarcadorResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('AGENDA_GERENCIAR')")
    public ResponseEntity<AgendaMarcadorResponseDTO> create(@RequestBody @Valid AgendaMarcadorRequestDTO request){
        AgendaMarcador marcador = service.create(mapper.toEntity(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(marcador.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(marcador));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('AGENDA_GERENCIAR')")
    public ResponseEntity<AgendaMarcadorResponseDTO> update(@RequestBody @Valid AgendaMarcadorRequestDTO request){
        return ResponseEntity.ok(mapper.toResponseDTO(service.update(mapper.toEntity(request))));
    }

    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('AGENDA_GERENCIAR')")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
