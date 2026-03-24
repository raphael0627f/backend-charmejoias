package com.manager.app.controller;

import com.manager.app.dto.ClienteDTO;
import com.manager.app.entity.Cliente;
import com.manager.app.mapper.ClienteMapper;
import com.manager.app.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping(value="/clientes")
public class ClienteController {

    private final ClienteService service;
    private final ClienteMapper mapper;

    @GetMapping
    public ResponseEntity<List<ClienteDTO>> findAll(){
        return ResponseEntity.ok(service.findAll().stream().map(mapper::toDTO).collect(Collectors.toList()));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<ClienteDTO> findById(@PathVariable String id){
        return ResponseEntity.ok(mapper.toDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ClienteDTO> create(@RequestBody @Valid ClienteDTO clienteDTO){
        Cliente cliente = service.create(mapper.toEntity(clienteDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(cliente.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toDTO(cliente));
    }

    @PutMapping
    public ResponseEntity<ClienteDTO> update(@RequestBody @Valid ClienteDTO clienteDTO){
        Cliente cliente = service.update(mapper.toEntity(clienteDTO));
        return ResponseEntity.ok(mapper.toDTO(cliente));
    }

    @PutMapping(value = "activate/{id}")
    public ResponseEntity<ClienteDTO> activate(@PathVariable String id){
        return ResponseEntity.ok(mapper.toDTO(service.activate(id)));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id){
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
