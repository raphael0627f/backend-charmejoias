package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.CreditoClienteDTOs;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

// crédito do cliente: saldo, lotes, extrato e lançamento manual (aba Financeiro do cadastro do cliente)
@RestController
@RequestMapping("/clientes/{clienteId}/credito")
@RequiredArgsConstructor
public class CreditoClienteController {

    private static final String CONSULTA = "hasAnyAuthority('CREDITO_CLIENTE_VISUALIZAR', 'CREDITO_CLIENTE_GERENCIAR', 'CREDITO_CLIENTE_APLICAR')";
    private static final String GERENCIAR = "hasAuthority('CREDITO_CLIENTE_GERENCIAR')";

    private final CreditoClienteService service;

    @GetMapping
    @PreAuthorize(CONSULTA)
    public ResponseEntity<CreditoClienteDTOs.Credito> credito(@PathVariable UUID clienteId) {
        return ResponseEntity.ok(service.credito(clienteId));
    }

    @GetMapping("/extrato")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<CreditoClienteDTOs.Extrato> extrato(@PathVariable UUID clienteId,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.extrato(clienteId, de, ate, page, size));
    }

    @PostMapping("/lancamentos")
    @PreAuthorize(GERENCIAR)
    public ResponseEntity<CreditoClienteDTOs.Lote> lancar(@PathVariable UUID clienteId, @RequestBody @Valid CreditoClienteDTOs.LancamentoRequest request) {
        return ResponseEntity.status(201).body(service.lancarManual(clienteId, request.valor(), request.motivo()));
    }

    @PostMapping("/lotes/{loteId}/desfazer")
    @PreAuthorize(GERENCIAR)
    public ResponseEntity<CreditoClienteDTOs.Lote> desfazer(@PathVariable UUID clienteId, @PathVariable Long loteId, @RequestBody @Valid CreditoClienteDTOs.MotivoRequest request) {
        return ResponseEntity.ok(service.desfazerManual(clienteId, loteId, request.motivo()));
    }
}
