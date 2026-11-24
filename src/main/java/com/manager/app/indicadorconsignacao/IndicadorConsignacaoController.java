package com.manager.app.indicadorconsignacao;

import com.manager.app.indicadorconsignacao.dto.IndicadorDTOs;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

// indicadores de consignação (somente leitura)
@RestController
@RequestMapping("/indicadores-consignacao")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('INDICADOR_CONSIGNACAO_VISUALIZAR')")
public class IndicadorConsignacaoController {

    private final IndicadorConsignacaoService service;

    @GetMapping("/resumo")
    public ResponseEntity<IndicadorDTOs.Resumo> resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) UUID clienteId, @RequestParam(required = false) Long cidadeId, @RequestParam(required = false) Long rotaId) {
        return ResponseEntity.ok(service.resumo(service.filtro(inicio, fim, clienteId, cidadeId, rotaId)));
    }

    @GetMapping("/ranking-clientes")
    public ResponseEntity<IndicadorDTOs.Ranking> ranking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) UUID clienteId, @RequestParam(required = false) Long cidadeId, @RequestParam(required = false) Long rotaId,
            @RequestParam(required = false) String ordenar, @RequestParam(required = false) String direcao, @RequestParam(defaultValue = "10") int top) {
        return ResponseEntity.ok(service.ranking(service.filtro(inicio, fim, clienteId, cidadeId, rotaId), ordenar, direcao, top));
    }

    @GetMapping("/evolucao-mensal")
    public ResponseEntity<IndicadorDTOs.Evolucao> evolucao(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) UUID clienteId, @RequestParam(required = false) Long cidadeId, @RequestParam(required = false) Long rotaId) {
        return ResponseEntity.ok(service.evolucaoMensal(service.filtro(inicio, fim, clienteId, cidadeId, rotaId)));
    }

    @GetMapping("/concentracao")
    public ResponseEntity<IndicadorDTOs.Concentracao> concentracao(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) UUID clienteId, @RequestParam(required = false) Long cidadeId, @RequestParam(required = false) Long rotaId) {
        return ResponseEntity.ok(service.concentracao(service.filtro(inicio, fim, clienteId, cidadeId, rotaId)));
    }

    @GetMapping("/produtos-categorias")
    public ResponseEntity<IndicadorDTOs.ProdutosCategorias> produtosCategorias(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) UUID clienteId, @RequestParam(required = false) Long cidadeId, @RequestParam(required = false) Long rotaId,
            @RequestParam(defaultValue = "5") int top) {
        return ResponseEntity.ok(service.produtosCategorias(service.filtro(inicio, fim, clienteId, cidadeId, rotaId), top));
    }

    @GetMapping("/meios-pagamento")
    public ResponseEntity<IndicadorDTOs.MeiosPagamento> meiosPagamento(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) UUID clienteId, @RequestParam(required = false) Long cidadeId, @RequestParam(required = false) Long rotaId) {
        return ResponseEntity.ok(service.meiosPagamento(service.filtro(inicio, fim, clienteId, cidadeId, rotaId)));
    }
}
