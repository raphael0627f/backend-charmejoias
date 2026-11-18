package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.AnexoDTO;
import com.manager.app.financeiro.dto.PagamentoDTOs;
import com.manager.app.financeiro.dto.LoteLancamentoDTOs;
import com.manager.app.financeiro.dto.RecorrenciaDTOs;
import com.manager.app.financeiro.dto.ContaPagarAcoesDTO;
import com.manager.app.financeiro.dto.EstornoRequestDTO;
import com.manager.app.financeiro.dto.ContaPagarCriacaoResponseDTO;
import com.manager.app.financeiro.dto.ContaPagarDetalheDTO;
import com.manager.app.financeiro.dto.ContaPagarListaResponseDTO;
import com.manager.app.financeiro.dto.ContaPagarRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/contas-pagar")
public class ContaPagarController {

    private static final String CONSULTA = "hasAnyAuthority('CONTA_PAGAR_VISUALIZAR', 'CONTA_PAGAR_GERENCIAR', 'CONTA_PAGAR_PAGAR')";
    private static final String PAGAMENTO = "hasAuthority('CONTA_PAGAR_PAGAR')";
    private static final String ESTORNO = "hasAuthority('CONTA_PAGAR_ESTORNAR')";
    private static final String GERENCIA = "hasAuthority('CONTA_PAGAR_GERENCIAR')";

    private final ContaPagarService service;
    private final ContaPagarPagamentoService pagamentoService;
    private final ContaPagarRecorrenciaService recorrenciaService;
    private final ContaPagarAnexoService anexoService;
    private final ContaPagarLoteService loteService;

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
            @RequestParam(required = false) Boolean estimado,
            @RequestParam(required = false) java.util.UUID clienteId,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) String ordenar,
            @RequestParam(required = false) String direcao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ContaPagarFiltro filtro = new ContaPagarFiltro(vencimentoDe, vencimentoAte, emissaoDe, emissaoAte, pagamentoDe, pagamentoAte, situacao,
                busca, categoriaId, centroCustoId, formaPrevistaId, bancoId, valorMin, valorMax, estimado, clienteId, origem);
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

    @PostMapping("/{id}/pagamentos")
    @PreAuthorize(PAGAMENTO)
    public ResponseEntity<PagamentoDTOs.Resultado> pagar(@PathVariable Long id, @Valid @RequestBody PagamentoDTOs.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagamentoService.pagar(id, request));
    }

    @PostMapping("/pagamentos/{pagamentoId}/estornar")
    @PreAuthorize(ESTORNO)
    public ResponseEntity<PagamentoDTOs.Resultado> estornarPagamento(@PathVariable Long pagamentoId, @RequestBody(required = false) EstornoRequestDTO request) {
        return ResponseEntity.ok(pagamentoService.estornar(pagamentoId, request));
    }

    @PostMapping("/pagamentos-lote")
    @PreAuthorize(PAGAMENTO)
    public ResponseEntity<PagamentoDTOs.ResultadoLote> pagarLote(@RequestBody(required = false) PagamentoDTOs.Lote request) {
        return ResponseEntity.ok(pagamentoService.pagarLote(request));
    }

    @PostMapping("/lote")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<LoteLancamentoDTOs.Resultado> lancarLote(@RequestBody(required = false) LoteLancamentoDTOs.Request request) {
        return ResponseEntity.ok(loteService.lancar(request));
    }

    // ---- recorrência ----

    @GetMapping("/recorrencias/{id}")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<RecorrenciaDTOs.Regra> obterRecorrencia(@PathVariable Long id) {
        return ResponseEntity.ok(recorrenciaService.obter(id));
    }

    @PutMapping("/recorrencias/{id}")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<RecorrenciaDTOs.ResultadoReajuste> reajustarRecorrencia(@PathVariable Long id, @Valid @RequestBody RecorrenciaDTOs.Reajuste request) {
        return ResponseEntity.ok(recorrenciaService.reajustar(id, request));
    }

    @PostMapping("/recorrencias/{id}/encerrar")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<RecorrenciaDTOs.ResultadoEncerramento> encerrarRecorrencia(@PathVariable Long id, @RequestBody(required = false) RecorrenciaDTOs.Encerramento request) {
        return ResponseEntity.ok(recorrenciaService.encerrar(id, request));
    }

    // ---- anexos ----

    @PostMapping(value = "/{id}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(GERENCIA)
    public ResponseEntity<AnexoDTO> anexar(@PathVariable Long id, @RequestPart("arquivo") MultipartFile arquivo, @RequestParam("tipo") TipoAnexo tipo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(anexoService.anexarAoTitulo(id, tipo, arquivo));
    }

    @PostMapping(value = "/pagamentos/{pagamentoId}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(PAGAMENTO)
    public ResponseEntity<AnexoDTO> anexarComprovante(@PathVariable Long pagamentoId, @RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(anexoService.anexarComprovante(pagamentoId, arquivo));
    }

    @GetMapping("/anexos/{id}")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<byte[]> baixarAnexo(@PathVariable Long id) {
        ContaPagarAnexoService.Download d = anexoService.baixar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.tipoConteudo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.nome(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(d.conteudo());
    }

    @DeleteMapping("/anexos/{id}")
    @PreAuthorize("hasAnyAuthority('CONTA_PAGAR_GERENCIAR', 'CONTA_PAGAR_PAGAR')")
    public ResponseEntity<Void> removerAnexo(@PathVariable Long id, Authentication authentication) {
        anexoService.excluir(id, authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cancelar-lote")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<ContaPagarAcoesDTO.ResultadoLote> cancelarLote(@RequestBody(required = false) ContaPagarAcoesDTO.CancelarLote request) {
        return ResponseEntity.ok(service.cancelarLote(request));
    }
}
