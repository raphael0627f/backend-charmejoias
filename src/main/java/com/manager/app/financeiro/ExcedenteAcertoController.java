package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.CreditoClienteDTOs;
import com.manager.app.financeiro.dto.ExcedenteDTOs;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/excedentes-acerto")
@RequiredArgsConstructor
public class ExcedenteAcertoController {

    private static final String CONSULTA = "hasAnyAuthority('CREDITO_CLIENTE_VISUALIZAR', 'CREDITO_CLIENTE_GERENCIAR', 'CREDITO_CLIENTE_APLICAR')";

    private final ExcedenteAcertoService service;
    private final CreditoClienteService creditoService;

    @GetMapping
    @PreAuthorize(CONSULTA)
    public ResponseEntity<ExcedenteDTOs.Lista> listar(@RequestParam(required = false) String situacao, @RequestParam(required = false) String busca,
                                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listar(situacao, busca, page, size));
    }

    @PostMapping("/{id}/destinar")
    @PreAuthorize("hasAuthority('CREDITO_CLIENTE_GERENCIAR')")
    public ResponseEntity<ExcedenteDTOs.Detalhe> destinar(@PathVariable Long id, @RequestBody @jakarta.validation.Valid CreditoClienteDTOs.DestinarRequest request) {
        creditoService.destinar(id, request.destino(), request.vencimento());
        return ResponseEntity.ok(service.detalhe(id));
    }

    @PostMapping("/{id}/desfazer-destino")
    @PreAuthorize("hasAuthority('CREDITO_CLIENTE_GERENCIAR')")
    public ResponseEntity<ExcedenteDTOs.Detalhe> desfazerDestino(@PathVariable Long id, @RequestBody @jakarta.validation.Valid CreditoClienteDTOs.MotivoRequest request) {
        creditoService.desfazerDestino(id, request.motivo());
        return ResponseEntity.ok(service.detalhe(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<ExcedenteDTOs.Detalhe> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalhe(id));
    }
}
