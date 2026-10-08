package com.manager.app.pedido;

import com.manager.app.consignacao.dto.ConsignacaoRequestDTO;
import com.manager.app.consignacao.dto.ConsignacaoResponseDTO;
import com.manager.app.pedido.dto.PedidoCancelamentoRequestDTO;
import com.manager.app.pedido.dto.PedidoItemCancelamentoRequestDTO;
import com.manager.app.pedido.dto.PedidoItemSeparadoRequestDTO;
import com.manager.app.pedido.dto.PedidoRequestDTO;
import com.manager.app.pedido.dto.PedidoResponseDTO;
import com.manager.app.pedido.dto.PedidoStatusRequestDTO;
import com.manager.app.consignacao.Consignacao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.manager.app.pedido.dto.PedidoContagemDTO;
import com.manager.app.pedido.dto.PedidoResumoDTO;
import com.manager.app.pedido.dto.PedidoStatusLoteRequestDTO;
import com.manager.app.pedido.dto.PedidoStatusLoteResultadoDTO;
import com.manager.app.shared.ModoBusca;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping(value="/pedidos")
public class PedidoController {

    private final PedidoService service;
    private final PedidoRelatorioService relatorioService;
    private final PedidoMapper mapper;

    @GetMapping
    public ResponseEntity<Page<PedidoResumoDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) ModoBusca modoBusca,
            @RequestParam(required = false) List<StatusPedido> status,
            @RequestParam(required = false) List<CategoriaPedido> categoria,
            @RequestParam(required = false) Long numero,
            @RequestParam(required = false) String clienteCodigo,
            @RequestParam(required = false) String clienteNome,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataEntregaInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataEntregaFim,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate criadoInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate criadoFim,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable){
        PedidoFiltro filtro = new PedidoFiltro(busca, modoBusca, status, categoria, numero, clienteCodigo, clienteNome, cidade,
                dataEntregaInicio, dataEntregaFim, criadoInicio, criadoFim);
        return ResponseEntity.ok(service.findAllResumo(filtro, pageable));
    }

    // contagem por status e por categoria (respeita os demais filtros) - alimenta o topo da tela e os grupos
    @GetMapping(value = "/contagem")
    public ResponseEntity<PedidoContagemDTO> contar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) ModoBusca modoBusca,
            @RequestParam(required = false) List<StatusPedido> status,
            @RequestParam(required = false) List<CategoriaPedido> categoria,
            @RequestParam(required = false) Long numero,
            @RequestParam(required = false) String clienteCodigo,
            @RequestParam(required = false) String clienteNome,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataEntregaInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataEntregaFim,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate criadoInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate criadoFim){
        PedidoFiltro filtro = new PedidoFiltro(busca, modoBusca, status, categoria, numero, clienteCodigo, clienteNome, cidade,
                dataEntregaInicio, dataEntregaFim, criadoInicio, criadoFim);
        return ResponseEntity.ok(service.contar(filtro));
    }

    // PDF único: relPedidoFinal (totais por cliente) + relPedidoGrupo (itens por grupo), sobre os pedidos
    // selecionados. Marca os pedidos como impressos (marcarImpresso=false desliga)
    @GetMapping(value = "/relatorios/separacao")
    public ResponseEntity<byte[]> relatorioSeparacao(@RequestParam List<Long> ids,
                                                     @RequestParam(defaultValue = "CLIENTE") PedidoRelatorioService.OrdemSeparacao ordem,
                                                     @RequestParam(defaultValue = "true") boolean marcarImpresso){
        return pdf("relatorio-pedidos.pdf", relatorioService.gerarGrupoEFinal(ids, ordem, marcarImpresso));
    }

    // relPedidoIndividual: uma página por pedido, todos os selecionados num PDF só. Marca como impressos
    // (marcarImpresso=false desliga)
    @GetMapping(value = "/relatorios/individual")
    public ResponseEntity<byte[]> relatorioIndividual(@RequestParam List<Long> ids,
                                                      @RequestParam(defaultValue = "true") boolean marcarImpresso){
        return pdf("pedido-individual.pdf", relatorioService.gerarIndividual(ids, marcarImpresso));
    }

    private ResponseEntity<byte[]> pdf(String nome, byte[] conteudo){
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=" + nome)
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(conteudo);
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<PedidoResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<PedidoResponseDTO> create(@RequestBody @Valid PedidoRequestDTO request){
        Pedido pedido = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(pedido.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(pedido));
    }

    @PutMapping
    public ResponseEntity<PedidoResponseDTO> update(@RequestBody @Valid PedidoRequestDTO request){
        Pedido pedido = service.update(request);
        return ResponseEntity.ok(mapper.toResponseDTO(pedido));
    }

    @PutMapping(value = "status/lote")
    public ResponseEntity<List<PedidoStatusLoteResultadoDTO>> updateStatusLote(@RequestBody @Valid PedidoStatusLoteRequestDTO request){
        return ResponseEntity.ok(service.updateStatusLote(request));
    }

    @PutMapping(value = "status")
    public ResponseEntity<PedidoResponseDTO> updateStatus(@RequestBody @Valid PedidoStatusRequestDTO request){
        Pedido pedido = service.updateStatus(request);
        return ResponseEntity.ok(mapper.toResponseDTO(pedido));
    }

    @PostMapping(value = "/{id}/cancelar")
    public ResponseEntity<PedidoResponseDTO> cancelar(@PathVariable Long id, @RequestBody @Valid PedidoCancelamentoRequestDTO request){
        Pedido pedido = service.cancelar(id, request.getMotivo());
        return ResponseEntity.ok(mapper.toResponseDTO(pedido));
    }

    @PutMapping(value = "/{id}/itens/{itemId}/separado")
    public ResponseEntity<PedidoResponseDTO> marcarItemSeparado(@PathVariable Long id, @PathVariable Long itemId,
                                                                 @RequestBody @Valid PedidoItemSeparadoRequestDTO request){
        Pedido pedido = service.marcarItemSeparado(id, itemId, request.getSeparado());
        return ResponseEntity.ok(mapper.toResponseDTO(pedido));
    }

    @PutMapping(value = "/{id}/itens/{itemId}/cancelar")
    public ResponseEntity<PedidoResponseDTO> cancelarItem(@PathVariable Long id, @PathVariable Long itemId,
                                                           @RequestBody @Valid PedidoItemCancelamentoRequestDTO request){
        Pedido pedido = service.cancelarItem(id, itemId, request.getQuantidade(), request.getMotivo());
        return ResponseEntity.ok(mapper.toResponseDTO(pedido));
    }
}
