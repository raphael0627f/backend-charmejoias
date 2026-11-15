package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.ContaReceberAcoesDTO;
import com.manager.app.financeiro.dto.ContaReceberCriacaoResponseDTO;
import com.manager.app.financeiro.dto.ContaReceberDetalheDTO;
import com.manager.app.financeiro.dto.ContaReceberListaResponseDTO;
import com.manager.app.financeiro.dto.ContaReceberRequestDTO;
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
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/contas-receber")
public class ContaReceberController {

    private static final String CONSULTA = "hasAnyAuthority('CONTA_RECEBER_VISUALIZAR', 'CONTA_RECEBER_GERENCIAR', 'CONTA_RECEBER_RECEBER')";
    private static final String GERENCIA = "hasAuthority('CONTA_RECEBER_GERENCIAR')";

    private final ContaReceberService service;

    @PostMapping
    @PreAuthorize(GERENCIA)
    public ResponseEntity<ContaReceberCriacaoResponseDTO> criar(@RequestBody @Valid ContaReceberRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request));
    }

    @GetMapping
    @PreAuthorize(CONSULTA)
    public ResponseEntity<ContaReceberListaResponseDTO> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoAte,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate emissaoDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate emissaoAte,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recebimentoDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recebimentoAte,
            @RequestParam(required = false) List<SituacaoExibicaoContaReceber> situacao,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) UUID clienteId,
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
        ContaReceberFiltro filtro = new ContaReceberFiltro(vencimentoDe, vencimentoAte, emissaoDe, emissaoAte, recebimentoDe, recebimentoAte,
                situacao, busca, clienteId, categoriaId, centroCustoId, formaPrevistaId, bancoId, valorMin, valorMax);
        return ResponseEntity.ok(service.listar(filtro, ordenar, direcao, page, size));
    }

    // seletor de clientes do formulário: não exige permissão de cliente
    @GetMapping("/clientes")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<List<ContaReceberAcoesDTO.Cliente>> clientes(@RequestParam(required = false) String busca) {
        return ResponseEntity.ok(service.buscarClientes(busca));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<ContaReceberDetalheDTO> obter(@PathVariable Long id) {
        return ResponseEntity.ok(service.obter(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<ContaReceberDetalheDTO> atualizar(@PathVariable Long id, @RequestBody @Valid ContaReceberRequestDTO request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @PatchMapping("/{id}/vencimento")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<ContaReceberDetalheDTO> alterarVencimento(@PathVariable Long id, @RequestBody @Valid ContaReceberAcoesDTO.Vencimento request) {
        return ResponseEntity.ok(service.alterarVencimento(id, request));
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<ContaReceberDetalheDTO> cancelar(@PathVariable Long id, @RequestBody(required = false) ContaReceberAcoesDTO.Cancelar request) {
        return ResponseEntity.ok(service.cancelar(id, request));
    }

    @PostMapping("/cancelar-lote")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<ContaReceberAcoesDTO.ResultadoLote> cancelarLote(@RequestBody(required = false) ContaReceberAcoesDTO.CancelarLote request) {
        return ResponseEntity.ok(service.cancelarLote(request));
    }
}
