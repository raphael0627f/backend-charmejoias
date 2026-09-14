package com.manager.app.acertojoia;

import com.manager.app.acertojoia.dto.AcertoJoiaRequestDTO;
import com.manager.app.acertojoia.dto.AcertoJoiaResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping(value="/acertos-joia")
public class AcertoJoiaController {

    private final AcertoJoiaService service;
    private final AcertoJoiaMapper mapper;

    @GetMapping
    public ResponseEntity<Page<AcertoJoiaResponseDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataFim,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, dataInicio, dataFim, pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<AcertoJoiaResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACERTO_JOIA_GERENCIAR')")
    public ResponseEntity<AcertoJoiaResponseDTO> create(@RequestBody @Valid AcertoJoiaRequestDTO request){
        AcertoJoia acertoJoia = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(acertoJoia.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(acertoJoia));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ACERTO_JOIA_GERENCIAR')")
    public ResponseEntity<AcertoJoiaResponseDTO> update(@RequestBody @Valid AcertoJoiaRequestDTO request){
        AcertoJoia acertoJoia = service.update(request);
        return ResponseEntity.ok(mapper.toResponseDTO(acertoJoia));
    }

    @GetMapping(value = "/{id}/relatorio")
    public ResponseEntity<byte[]> gerarRelatorio(@PathVariable Long id){
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=relatorio-acerto-joia.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.gerarRelatorio(id));
    }

    @GetMapping(value = "/{id}/relatorio-final")
    public ResponseEntity<byte[]> gerarRelatorioFinal(@PathVariable Long id){
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=relatorio-acerto-joia-final.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.gerarRelatorioFinal(id));
    }
}
