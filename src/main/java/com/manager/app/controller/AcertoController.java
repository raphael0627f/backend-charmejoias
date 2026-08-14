package com.manager.app.controller;

import com.manager.app.dto.acerto.AcertoRequestDTO;
import com.manager.app.dto.acerto.AcertoResponseDTO;
import com.manager.app.entity.Acerto;
import com.manager.app.mapper.AcertoMapper;
import com.manager.app.service.AcertoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping(value="/acertos")
public class AcertoController {

    private final AcertoService service;
    private final AcertoMapper mapper;

    @GetMapping
    public ResponseEntity<List<AcertoResponseDTO>> findAll(){
        return ResponseEntity.ok(service.findAll().stream().map(mapper::toResponseDTO).collect(Collectors.toList()));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<AcertoResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<AcertoResponseDTO> create(@RequestBody @Valid AcertoRequestDTO request){
        Acerto acerto = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(acerto.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(acerto));
    }

    @PutMapping
    public ResponseEntity<AcertoResponseDTO> update(@RequestBody @Valid AcertoRequestDTO request){
        Acerto acerto = service.update(request);
        return ResponseEntity.ok(mapper.toResponseDTO(acerto));
    }

    @GetMapping(value = "/{id}/relatorio")
    public ResponseEntity<byte[]> gerarRelatorio(@PathVariable Long id){
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=relatorio-acerto.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.gerarRelatorio(id));
    }

    @GetMapping(value = "/{id}/relatorio-final")
    public ResponseEntity<byte[]> gerarRelatorioFinal(@PathVariable Long id){
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=relatorio-acerto-final.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.gerarRelatorioFinal(id));
    }
}
