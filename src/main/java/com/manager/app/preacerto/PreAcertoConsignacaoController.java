package com.manager.app.preacerto;

import com.manager.app.preacerto.dto.PreAcertoConsignacaoRequestDTO;
import com.manager.app.preacerto.dto.PreAcertoConsignacaoResponseDTO;
import com.manager.app.preacerto.dto.PreAcertoConsignacaoResumoDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/pre-acertos-consignacao")
public class PreAcertoConsignacaoController {

    private final PreAcertoConsignacaoService service;
    private final PreAcertoConsignacaoMapper mapper;

    @GetMapping
    public ResponseEntity<Page<PreAcertoConsignacaoResumoDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long consignacaoId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataEntregaInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataEntregaFim,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataPreAcertoInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataPreAcertoFim,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataAcertoInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataAcertoFim,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAllResumo(busca, consignacaoId,
                dataEntregaInicio, dataEntregaFim, dataPreAcertoInicio, dataPreAcertoFim, dataAcertoInicio, dataAcertoFim, pageable));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<PreAcertoConsignacaoResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CONSIGNACAO_GERENCIAR')")
    public ResponseEntity<PreAcertoConsignacaoResponseDTO> create(@RequestBody @Valid PreAcertoConsignacaoRequestDTO request){
        PreAcertoConsignacao preAcerto = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(preAcerto.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(preAcerto));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('CONSIGNACAO_GERENCIAR')")
    public ResponseEntity<PreAcertoConsignacaoResponseDTO> update(@RequestBody @Valid PreAcertoConsignacaoRequestDTO request){
        PreAcertoConsignacao preAcerto = service.update(request);
        return ResponseEntity.ok(mapper.toResponseDTO(preAcerto));
    }
}
