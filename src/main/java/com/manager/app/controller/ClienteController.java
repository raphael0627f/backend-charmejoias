package com.manager.app.controller;

import com.manager.app.dto.ClienteRequestDTO;
import com.manager.app.dto.ClienteResponseDTO;
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
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping(value="/clientes")
public class ClienteController {

    private final ClienteService service;
    private final ClienteMapper mapper;

    @GetMapping
    public ResponseEntity<List<ClienteResponseDTO>> findAll(){
        return ResponseEntity.ok(service.findAll().stream().map(mapper::toResponseDTO).collect(Collectors.toList()));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<ClienteResponseDTO> findById(@PathVariable UUID id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ClienteResponseDTO> create(@RequestBody @Valid ClienteRequestDTO clienteRequestDTO){
        Cliente cliente = service.create(mapper.toEntity(clienteRequestDTO), clienteRequestDTO.getGruposIds());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(cliente.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(cliente));
    }

    @PutMapping
    public ResponseEntity<ClienteResponseDTO> update(@RequestBody @Valid ClienteRequestDTO clienteRequestDTO){
        Cliente cliente = service.update(mapper.toEntity(clienteRequestDTO), clienteRequestDTO.getGruposIds());
        ClienteResponseDTO response = mapper.toResponseDTO(cliente);
        return ResponseEntity.ok(response);
    }

    @PutMapping(value = "activate/{id}")
    public ResponseEntity<ClienteResponseDTO> activate(@PathVariable UUID id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.activate(id)));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
