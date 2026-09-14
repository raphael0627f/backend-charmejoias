package com.manager.app.acertoconsignacao;

import com.manager.app.acertoconsignacao.dto.AcertoConsignacaoRequestDTO;
import com.manager.app.acertoconsignacao.dto.AcertoConsignacaoResponseDTO;
import com.manager.app.acertoconsignacao.dto.AcertoConsignacaoResumoDTO;
import com.manager.app.acertoconsignacao.dto.ProdutoVendidoDTO;
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
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/acertos-consignacao")
public class AcertoConsignacaoController {

    private final AcertoConsignacaoService service;
    private final AcertoConsignacaoMapper mapper;

    @GetMapping
    public ResponseEntity<Page<AcertoConsignacaoResumoDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long consignacaoId,
            @RequestParam(required = false) Boolean confirmado,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataEntregaInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataEntregaFim,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataPreAcertoInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataPreAcertoFim,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataAcertoInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataAcertoFim,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAllResumo(busca, consignacaoId, confirmado,
                dataEntregaInicio, dataEntregaFim, dataPreAcertoInicio, dataPreAcertoFim, dataAcertoInicio, dataAcertoFim, pageable));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<AcertoConsignacaoResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACERTO_CONSIGNACAO_GERENCIAR')")
    public ResponseEntity<AcertoConsignacaoResponseDTO> create(@RequestBody @Valid AcertoConsignacaoRequestDTO request){
        AcertoConsignacao acerto = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(acerto.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(acerto));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ACERTO_CONSIGNACAO_GERENCIAR')")
    public ResponseEntity<AcertoConsignacaoResponseDTO> update(@RequestBody @Valid AcertoConsignacaoRequestDTO request){
        AcertoConsignacao acerto = service.update(request);
        return ResponseEntity.ok(mapper.toResponseDTO(acerto));
    }

    @PutMapping(value = "/{id}/confirmar")
    @PreAuthorize("hasAuthority('ACERTO_CONSIGNACAO_GERENCIAR')")
    public ResponseEntity<AcertoConsignacaoResponseDTO> confirmar(@PathVariable Long id){
        AcertoConsignacao acerto = service.confirmar(id);
        return ResponseEntity.ok(mapper.toResponseDTO(acerto));
    }

    @GetMapping(value = "/{id}/produtos-vendidos")
    public ResponseEntity<List<ProdutoVendidoDTO>> produtosVendidos(@PathVariable Long id){
        return ResponseEntity.ok(service.produtosVendidos(id));
    }
}
