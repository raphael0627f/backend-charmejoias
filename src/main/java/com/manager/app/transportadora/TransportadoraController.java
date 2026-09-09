package com.manager.app.transportadora;

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
@RequestMapping(value = "/transportadoras")
public class TransportadoraController {

    private final TransportadoraService service;
    private final TransportadoraMapper mapper;

    @GetMapping
    public ResponseEntity<Page<TransportadoraDTO>> findAll(
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, pageable).map(mapper::toDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<TransportadoraDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<TransportadoraDTO> create(@RequestBody @Valid TransportadoraDTO transportadoraDTO){
        Transportadora transportadora = service.create(mapper.toEntity(transportadoraDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(transportadora.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toDTO(transportadora));
    }

    @PutMapping
    public ResponseEntity<TransportadoraDTO> update(@RequestBody @Valid TransportadoraDTO transportadoraDTO){
        Transportadora transportadora = service.update(mapper.toEntity(transportadoraDTO));
        return ResponseEntity.ok(mapper.toDTO(transportadora));
    }

    @PutMapping(value = "/activate/{id}")
    public ResponseEntity<TransportadoraDTO> activate(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toDTO(service.activate(id)));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
