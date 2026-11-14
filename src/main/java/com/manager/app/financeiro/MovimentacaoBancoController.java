package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.AjusteRequestDTO;
import com.manager.app.financeiro.dto.EstornoRequestDTO;
import com.manager.app.financeiro.dto.MovimentacaoResponseDTO;
import com.manager.app.financeiro.dto.MovimentacaoResultadoDTO;
import com.manager.app.financeiro.dto.SaldosBancosDTO;
import com.manager.app.financeiro.dto.TransferenciaRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/movimentacoes-banco")
public class MovimentacaoBancoController {

    private static final String CONSULTA = "hasAnyAuthority('MOVIMENTACAO_VISUALIZAR', 'MOVIMENTACAO_GERENCIAR')";
    private static final String GERENCIA = "hasAuthority('MOVIMENTACAO_GERENCIAR')";

    private final MovimentacaoBancoService service;

    @GetMapping
    @PreAuthorize(CONSULTA)
    public ResponseEntity<Page<MovimentacaoResponseDTO>> extrato(
            @RequestParam(required = false) Long bancoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) OrigemMovimentacao origem,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.extrato(bancoId, dataInicio, dataFim, origem, busca, page, size));
    }

    @GetMapping("/saldos")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<SaldosBancosDTO> saldos() {
        return ResponseEntity.ok(service.saldos());
    }

    @PostMapping("/transferencias")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<MovimentacaoResultadoDTO> transferir(@RequestBody @Valid TransferenciaRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.transferir(request));
    }

    @PostMapping("/ajustes")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<MovimentacaoResultadoDTO> ajustar(@RequestBody @Valid AjusteRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ajustar(request));
    }

    @PostMapping("/{id}/estornar")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<MovimentacaoResultadoDTO> estornar(@PathVariable Long id, @RequestBody(required = false) EstornoRequestDTO request) {
        return ResponseEntity.ok(service.estornar(id, request));
    }
}
