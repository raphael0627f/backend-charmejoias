package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.ContaPagarAcoesDTO;
import com.manager.app.financeiro.dto.ContaPagarCriacaoResponseDTO;
import com.manager.app.financeiro.dto.ContaPagarDetalheDTO;
import com.manager.app.financeiro.dto.ContaPagarListaResponseDTO;
import com.manager.app.financeiro.dto.ContaPagarRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/contas-pagar")
public class ContaPagarController {

    private static final String CONSULTA = "hasAnyAuthority('CONTA_PAGAR_VISUALIZAR', 'CONTA_PAGAR_GERENCIAR', 'CONTA_PAGAR_BAIXAR')";
    private static final String GERENCIA = "hasAuthority('CONTA_PAGAR_GERENCIAR')";

    private final ContaPagarService service;

    @PostMapping
    @PreAuthorize(GERENCIA)
    public ResponseEntity<ContaPagarCriacaoResponseDTO> criar(@RequestBody @Valid ContaPagarRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request));
    }

    @GetMapping
    @PreAuthorize(CONSULTA)
    public ResponseEntity<ContaPagarListaResponseDTO> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoAte,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate emissaoDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate emissaoAte,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pagamentoDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pagamentoAte,
            @RequestParam(required = false) List<SituacaoExibicaoContaPagar> situacao,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long centroCustoId,
            @RequestParam(required = false) Long formaPrevistaId,
            @RequestParam(required = false) Long bancoId,
            @RequestParam(required = false) BigDecimal valorMin,
            @RequestParam(required = false) BigDecimal valorMax,
            @RequestParam(required = false) String ordenar,
            @RequestParam(required = false) String direcao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ContaPagarFiltro filtro = new ContaPagarFiltro(vencimentoDe, vencimentoAte, emissaoDe, emissaoAte, pagamentoDe, pagamentoAte, situacao,
                busca, categoriaId, centroCustoId, formaPrevistaId, bancoId, valorMin, valorMax);
        return ResponseEntity.ok(service.listar(filtro, ordenar, direcao, page, size));
    }

    @GetMapping("/favorecidos")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<List<String>> favorecidos(@RequestParam(required = false) String busca) {
        return ResponseEntity.ok(service.favorecidos(busca));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<ContaPagarDetalheDTO> obter(@PathVariable Long id) {
        return ResponseEntity.ok(service.obter(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<ContaPagarDetalheDTO> atualizar(@PathVariable Long id, @RequestBody @Valid ContaPagarRequestDTO request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @PatchMapping("/{id}/vencimento")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<ContaPagarDetalheDTO> alterarVencimento(@PathVariable Long id, @RequestBody @Valid ContaPagarAcoesDTO.Vencimento request) {
        return ResponseEntity.ok(service.alterarVencimento(id, request));
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<ContaPagarDetalheDTO> cancelar(@PathVariable Long id, @RequestBody(required = false) ContaPagarAcoesDTO.Cancelar request) {
        return ResponseEntity.ok(service.cancelar(id, request));
    }

    @PostMapping("/cancelar-lote")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<ContaPagarAcoesDTO.ResultadoLote> cancelarLote(@RequestBody(required = false) ContaPagarAcoesDTO.CancelarLote request) {
        return ResponseEntity.ok(service.cancelarLote(request));
    }
}
