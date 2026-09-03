package com.manager.app.controller;

import com.manager.app.dto.empresa.EmpresaRequestDTO;
import com.manager.app.dto.empresa.EmpresaResponseDTO;
import com.manager.app.entity.Empresa;
import com.manager.app.mapper.EmpresaMapper;
import com.manager.app.service.EmpresaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/empresas")
public class EmpresaController {

    private final EmpresaService service;
    private final EmpresaMapper mapper;

    @GetMapping
    public ResponseEntity<Page<EmpresaResponseDTO>> findAll(@PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<EmpresaResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<EmpresaResponseDTO> create(@RequestBody @Valid EmpresaRequestDTO empresaRequestDTO){
        Empresa empresa = service.create(mapper.toEntity(empresaRequestDTO));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(empresa.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(empresa));
    }

    @PutMapping
    public ResponseEntity<EmpresaResponseDTO> update(@RequestBody @Valid EmpresaRequestDTO empresaRequestDTO){
        Empresa empresa = service.update(mapper.toEntity(empresaRequestDTO));
        return ResponseEntity.ok(mapper.toResponseDTO(empresa));
    }

    @PostMapping(value = "/{id}/certificado", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmpresaResponseDTO> enviarCertificado(
            @PathVariable Long id,
            @RequestParam("certificado") MultipartFile certificado,
            @RequestParam("senha") String senha){
        Empresa empresa = service.enviarCertificado(id, certificado, senha);
        return ResponseEntity.ok(mapper.toResponseDTO(empresa));
    }
}
