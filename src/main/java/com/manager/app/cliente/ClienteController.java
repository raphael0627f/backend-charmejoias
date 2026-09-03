package com.manager.app.cliente;

import com.manager.app.cliente.dto.ClienteRequestDTO;
import com.manager.app.cliente.dto.ClienteResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value="/clientes")
public class ClienteController {

    private final ClienteService service;
    private final ClienteMapper mapper;

    @GetMapping
    public ResponseEntity<Page<ClienteResponseDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) Long grupoId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, ativo, grupoId, pageable).map(mapper::toResponseDTO));
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
