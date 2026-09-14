package com.manager.app.ncm;

import com.manager.app.ncm.dto.NcmDTO;
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
@RequestMapping(value = "/ncms")
public class NcmController {

    private final NcmService service;
    private final NcmMapper mapper;

    @GetMapping
    public ResponseEntity<Page<NcmDTO>> findAll(
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "codigo") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, pageable).map(mapper::toDTO));
    }

    @GetMapping(value = "/{codigo}")
    public ResponseEntity<NcmDTO> findById(@PathVariable String codigo){
        return ResponseEntity.ok(mapper.toDTO(service.findById(codigo)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('NCM_GERENCIAR')")
    public ResponseEntity<NcmDTO> create(@RequestBody @Valid NcmDTO ncmDTO){
        Ncm ncm = service.create(mapper.toEntity(ncmDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{codigo}").buildAndExpand(ncm.getCodigo()).toUri();
        return ResponseEntity.created(location).body(mapper.toDTO(ncm));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('NCM_GERENCIAR')")
    public ResponseEntity<NcmDTO> update(@RequestBody @Valid NcmDTO ncmDTO){
        Ncm ncm = service.update(mapper.toEntity(ncmDTO));
        return ResponseEntity.ok(mapper.toDTO(ncm));
    }

    @PutMapping(value = "activate/{codigo}")
    @PreAuthorize("hasAuthority('NCM_GERENCIAR')")
    public ResponseEntity<NcmDTO> activate(@PathVariable String codigo){
        return ResponseEntity.ok(mapper.toDTO(service.activate(codigo)));
    }

    @DeleteMapping(value = "/{codigo}")
    @PreAuthorize("hasAuthority('NCM_GERENCIAR')")
    public ResponseEntity<Void> delete(@PathVariable String codigo){
        service.deactivate(codigo);
        return ResponseEntity.noContent().build();
    }
}
