package com.manager.app.fluxocaixa;

import com.manager.app.fluxocaixa.FluxoCaixaCalculo.Visao;
import com.manager.app.fluxocaixa.dto.FluxoCaixaDTOs;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// fluxo de caixa (somente leitura): resumo, série por período, detalhe dos valores e exportação
@RestController
@RequestMapping("/fluxo-caixa")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('FLUXO_CAIXA_VISUALIZAR')")
public class FluxoCaixaController {

    private final FluxoCaixaService service;
    private final FluxoCaixaExportacaoService exportacaoService;

    private static FluxoCaixaFiltro filtro(LocalDate inicio, LocalDate fim, List<Long> bancoIds, List<Long> categoriaReceitaIds, List<Long> categoriaDespesaIds,
                                           List<Long> centroCustoIds, UUID clienteId, String favorecido) {
        return new FluxoCaixaFiltro(inicio, fim, bancoIds, categoriaReceitaIds, categoriaDespesaIds, centroCustoIds, clienteId, favorecido);
    }

    @GetMapping
    public ResponseEntity<FluxoCaixaDTOs.Resposta> resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String visao,
            @RequestParam(required = false) List<Long> bancoIds,
            @RequestParam(required = false) List<Long> categoriaReceitaIds,
            @RequestParam(required = false) List<Long> categoriaDespesaIds,
            @RequestParam(required = false) List<Long> centroCustoIds,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) String favorecido) {
        return ResponseEntity.ok(service.resumo(filtro(inicio, fim, bancoIds, categoriaReceitaIds, categoriaDespesaIds, centroCustoIds, clienteId, favorecido), Visao.de(visao)));
    }

    @GetMapping("/detalhe")
    public ResponseEntity<FluxoCaixaDTOs.Detalhe> detalhe(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String visao,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String situacao,
            @RequestParam(required = false) List<Long> bancoIds,
            @RequestParam(required = false) List<Long> categoriaReceitaIds,
            @RequestParam(required = false) List<Long> categoriaDespesaIds,
            @RequestParam(required = false) List<Long> centroCustoIds,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) String favorecido,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.detalhe(filtro(inicio, fim, bancoIds, categoriaReceitaIds, categoriaDespesaIds, centroCustoIds, clienteId, favorecido),
                Visao.de(visao), periodo, tipo, situacao, sort, page, size));
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String visao,
            @RequestParam(required = false) String formato,
            @RequestParam(required = false) List<Long> bancoIds,
            @RequestParam(required = false) List<Long> categoriaReceitaIds,
            @RequestParam(required = false) List<Long> categoriaDespesaIds,
            @RequestParam(required = false) List<Long> centroCustoIds,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) String favorecido) {
        FluxoCaixaExportacaoService.Arquivo arquivo = exportacaoService.exportar(
                filtro(inicio, fim, bancoIds, categoriaReceitaIds, categoriaDespesaIds, centroCustoIds, clienteId, favorecido), Visao.de(visao), formato);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(arquivo.nome()).build().toString())
                .contentType(MediaType.parseMediaType(arquivo.tipo()))
                .body(arquivo.conteudo());
    }
}
