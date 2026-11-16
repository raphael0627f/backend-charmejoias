package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.DocumentoFinanceiroDTOs;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

// resumo financeiro e geração de títulos a receber a partir do pré-acerto e do acerto de consignação.
// As rotas ficam sob /pre-acertos-consignacao e /acertos-consignacao, mas só exigem permissões do Contas a Receber;
// FinanceiroAutorizacaoInterceptor aplica o @PreAuthorize antes da leitura do corpo (403 antes de 400).
@RestController
@RequiredArgsConstructor
public class TituloConsignacaoController {

    private static final String CONSULTA = "hasAnyAuthority('CONTA_RECEBER_VISUALIZAR', 'CONTA_RECEBER_GERENCIAR', 'CONTA_RECEBER_RECEBER')";
    private static final String GERENCIA = "hasAuthority('CONTA_RECEBER_GERENCIAR')";

    private final DocumentoFinanceiroService service;

    @GetMapping("/pre-acertos-consignacao/{id}/financeiro")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<DocumentoFinanceiroDTOs.Resumo> resumoPreAcerto(@PathVariable Long id) {
        return ResponseEntity.ok(service.resumoPreAcerto(id));
    }

    @GetMapping("/acertos-consignacao/{id}/financeiro")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<DocumentoFinanceiroDTOs.Resumo> resumoAcerto(@PathVariable Long id) {
        return ResponseEntity.ok(service.resumoAcerto(id));
    }

    @PostMapping("/pre-acertos-consignacao/{id}/titulos")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<DocumentoFinanceiroDTOs.GerarResposta> gerarDoPreAcerto(@PathVariable Long id,
                                                                                  @RequestBody(required = false) DocumentoFinanceiroDTOs.GerarRequest request) {
        exigirPermissaoDeRecebimento(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.gerarDoPreAcerto(id, request));
    }

    @PostMapping("/acertos-consignacao/{id}/titulos")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<DocumentoFinanceiroDTOs.GerarResposta> gerarDoAcerto(@PathVariable Long id,
                                                                               @RequestBody(required = false) DocumentoFinanceiroDTOs.GerarRequest request) {
        exigirPermissaoDeRecebimento(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.gerarDoAcerto(id, request));
    }

    // o recebimento no mesmo ato exige também CONTA_RECEBER_RECEBER (403 antes de qualquer validação do corpo)
    private static void exigirPermissaoDeRecebimento(DocumentoFinanceiroDTOs.GerarRequest request) {
        if (request == null || request.recebimento() == null) return;
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        boolean permitido = autenticacao != null && autenticacao.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("CONTA_RECEBER_RECEBER"));
        if (!permitido) throw new AccessDeniedException("Você não possui permissão para registrar o recebimento no mesmo ato.");
    }
}
