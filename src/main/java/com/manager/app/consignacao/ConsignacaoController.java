package com.manager.app.consignacao;

import com.manager.app.consignacao.dto.ConsignacaoRequestDTO;
import com.manager.app.consignacao.dto.ConsignacaoResponseDTO;
import com.manager.app.consignacao.dto.ConsignacaoResumoDTO;
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
@RequestMapping(value="/consignacoes")
public class ConsignacaoController {

    private final ConsignacaoService service;
    private final ConsignacaoMapper mapper;

    @GetMapping
    public ResponseEntity<Page<ConsignacaoResumoDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) com.manager.app.shared.ModoBusca modoBusca,
            @RequestParam(required = false) java.util.UUID clienteId,
            @RequestParam(required = false) Boolean temCliente,
            @RequestParam(required = false) Boolean temAcerto,
            @RequestParam(required = false) Boolean confirmada,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataEntregaInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataEntregaFim,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataPreAcertoInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataPreAcertoFim,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataAcertoInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataAcertoFim,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAllResumo(busca, modoBusca, clienteId, temCliente, temAcerto, confirmada,
                dataEntregaInicio, dataEntregaFim, dataPreAcertoInicio, dataPreAcertoFim, dataAcertoInicio, dataAcertoFim, pageable));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<ConsignacaoResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CONSIGNACAO_GERENCIAR')")
    public ResponseEntity<ConsignacaoResponseDTO> create(@RequestBody @Valid ConsignacaoRequestDTO request){
        Consignacao consignacao = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(consignacao.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(consignacao));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('CONSIGNACAO_GERENCIAR')")
    public ResponseEntity<ConsignacaoResponseDTO> update(@RequestBody @Valid ConsignacaoRequestDTO request){
        Consignacao consignacao = service.update(request);
        return ResponseEntity.ok(mapper.toResponseDTO(consignacao));
    }

    @PutMapping(value = "/{id}/confirmar")
    @PreAuthorize("hasAuthority('CONSIGNACAO_GERENCIAR')")
    public ResponseEntity<ConsignacaoResponseDTO> confirmar(@PathVariable Long id){
        Consignacao consignacao = service.confirmar(id);
        return ResponseEntity.ok(mapper.toResponseDTO(consignacao));
    }

    @GetMapping(value = "/{id}/relatorio")
    public ResponseEntity<byte[]> gerarRelatorio(@PathVariable Long id,
                                                  @RequestParam(value = "tipo", defaultValue = "PADRAO") TipoRelatorioConsignacao tipo){
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=relatorio-consignacao.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.gerarRelatorio(id, tipo));
    }

}
