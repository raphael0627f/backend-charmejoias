package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.ContaReceberAcoesDTO;
import com.manager.app.financeiro.dto.ContaReceberCriacaoResponseDTO;
import com.manager.app.financeiro.dto.ContaReceberDetalheDTO;
import com.manager.app.financeiro.dto.ContaReceberListaResponseDTO;
import com.manager.app.financeiro.dto.ContaReceberRequestDTO;
import com.manager.app.financeiro.dto.EstornoRequestDTO;
import com.manager.app.financeiro.dto.RecebimentoDTOs;
import com.manager.app.financeiro.dto.ContaReceberAnexoDTO;
import com.manager.app.financeiro.dto.SituacaoClienteDTOs;
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
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/contas-receber")
public class ContaReceberController {

    private static final String CONSULTA = "hasAnyAuthority('CONTA_RECEBER_VISUALIZAR', 'CONTA_RECEBER_GERENCIAR', 'CONTA_RECEBER_RECEBER')";
    private static final String RECEBIMENTO = "hasAuthority('CONTA_RECEBER_RECEBER')";
    private static final String ESTORNO = "hasAuthority('CONTA_RECEBER_ESTORNAR')";
    private static final String GERENCIA = "hasAuthority('CONTA_RECEBER_GERENCIAR')";

    private final ContaReceberService service;
    private final ContaReceberRecebimentoService recebimentoService;
    private final ContaReceberAnexoService anexoService;
    private final SituacaoClienteService situacaoClienteService;

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
            @RequestParam(required = false) String tipoPagador,
            @RequestParam(required = false) String ordenar,
            @RequestParam(required = false) String direcao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ContaReceberFiltro filtro = new ContaReceberFiltro(vencimentoDe, vencimentoAte, emissaoDe, emissaoAte, recebimentoDe, recebimentoAte,
                situacao, busca, clienteId, categoriaId, centroCustoId, formaPrevistaId, bancoId, valorMin, valorMax, tipoPagador);
        return ResponseEntity.ok(service.listar(filtro, ordenar, direcao, page, size));
    }

    // seletor de clientes do formulário: não exige permissão de cliente
    @GetMapping("/clientes")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<List<ContaReceberAcoesDTO.Cliente>> clientes(@RequestParam(required = false) String busca) {
        return ResponseEntity.ok(service.buscarClientes(busca));
    }

    // situação financeira do cliente (não exige permissão de cliente)
    @GetMapping("/clientes/{clienteId}/situacao")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<SituacaoClienteDTOs.Situacao> situacaoDoCliente(@PathVariable UUID clienteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recebimentoDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recebimentoAte) {
        return ResponseEntity.ok(situacaoClienteService.situacao(clienteId, recebimentoDe, recebimentoAte));
    }

    @GetMapping("/clientes/situacao")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<SituacaoClienteDTOs.Geral> situacaoGeral(@RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "false") boolean somenteVencidos, @RequestParam(required = false) String ordenar,
            @RequestParam(required = false) String direcao, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(situacaoClienteService.geral(busca, somenteVencidos, ordenar, direcao, page, size));
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

    @PostMapping("/{id}/recebimentos")
    @PreAuthorize(RECEBIMENTO)
    public ResponseEntity<RecebimentoDTOs.Resultado> receber(@PathVariable Long id, @Valid @RequestBody RecebimentoDTOs.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recebimentoService.receber(id, request));
    }

    @PostMapping("/recebimentos/{recebimentoId}/estornar")
    @PreAuthorize(ESTORNO)
    public ResponseEntity<RecebimentoDTOs.Resultado> estornarRecebimento(@PathVariable Long recebimentoId, @RequestBody(required = false) EstornoRequestDTO request) {
        return ResponseEntity.ok(recebimentoService.estornar(recebimentoId, request));
    }

    @PostMapping("/recebimentos-lote")
    @PreAuthorize(RECEBIMENTO)
    public ResponseEntity<RecebimentoDTOs.ResultadoLote> receberLote(@RequestBody(required = false) RecebimentoDTOs.Lote request) {
        return ResponseEntity.ok(recebimentoService.receberLote(request));
    }

    // ---- anexos ----

    @PostMapping(value = "/{id}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(GERENCIA)
    public ResponseEntity<ContaReceberAnexoDTO> anexar(@PathVariable Long id, @RequestPart("arquivo") MultipartFile arquivo, @RequestParam("tipo") TipoAnexo tipo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(anexoService.anexarAoTitulo(id, tipo, arquivo));
    }

    @PostMapping(value = "/recebimentos/{recebimentoId}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(RECEBIMENTO)
    public ResponseEntity<ContaReceberAnexoDTO> anexarComprovante(@PathVariable Long recebimentoId, @RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(anexoService.anexarComprovante(recebimentoId, arquivo));
    }

    @GetMapping("/anexos/{id}")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<byte[]> baixarAnexo(@PathVariable Long id) {
        ContaReceberAnexoService.Download d = anexoService.baixar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.tipoConteudo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.nome(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(d.conteudo());
    }

    @DeleteMapping("/anexos/{id}")
    @PreAuthorize("hasAnyAuthority('CONTA_RECEBER_GERENCIAR', 'CONTA_RECEBER_RECEBER')")
    public ResponseEntity<Void> removerAnexo(@PathVariable Long id, Authentication authentication) {
        anexoService.excluir(id, authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
        return ResponseEntity.noContent().build();
    }
}
