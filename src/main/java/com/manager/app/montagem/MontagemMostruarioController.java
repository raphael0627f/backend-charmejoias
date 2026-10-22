package com.manager.app.montagem;

import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoMapper;
import com.manager.app.consignacao.dto.ConsignacaoRequestDTO;
import com.manager.app.consignacao.dto.ConsignacaoResponseDTO;
import com.manager.app.pedido.Pedido;
import com.manager.app.pedido.PedidoMapper;
import com.manager.app.pedido.dto.PedidoRequestDTO;
import com.manager.app.pedido.dto.PedidoResponseDTO;
import com.manager.app.montagem.dto.MontagemCancelamentoRequestDTO;
import com.manager.app.montagem.dto.MontagemRequestDTO;
import com.manager.app.montagem.dto.MontagemResponseDTO;
import com.manager.app.montagem.dto.MontagemResumoDTO;
import com.manager.app.shared.ModoBusca;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/montagens")
public class MontagemMostruarioController {

    private final MontagemMostruarioService service;
    private final MontagemMostruarioMapper mapper;
    private final PedidoMapper pedidoMapper;
    private final ConsignacaoMapper consignacaoMapper;

    @GetMapping
    public ResponseEntity<Page<MontagemResumoDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) ModoBusca modoBusca,
            @RequestParam(required = false) List<StatusMontagem> status,
            @RequestParam(required = false) List<Long> mostruarioId,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataEntregaDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataEntregaAte,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        Page<MontagemMostruario> pagina = service.findAll(busca, modoBusca, status, mostruarioId, clienteId, dataEntregaDe, dataEntregaAte, pageable);
        // listagem enxuta: quantidades e andamento por montagem em consultas de agregação (sem carregar as listas de cada uma)
        Page<MontagemResumoDTO> resumos = pagina.map(mapper::toResumoDTO);
        service.preencherResumos(resumos.getContent());
        return ResponseEntity.ok(resumos);
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<MontagemResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    // conferência dos produtos dos pedidos vs. consignações da montagem (calculada a cada chamada)
    @GetMapping(value = "/{id}/conferencia")
    public ResponseEntity<com.manager.app.montagem.dto.MontagemConferenciaDTO> conferencia(@PathVariable Long id){
        return ResponseEntity.ok(service.conferir(id));
    }

    // adiciona na consignação escolhida os produtos dos pedidos que ainda não estão nas consignações da montagem
    @PostMapping(value = "/{id}/consignacoes/{consignacaoId}/adicionar-pendentes")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<com.manager.app.montagem.dto.MontagemConferenciaDTO> adicionarPendentes(@PathVariable Long id, @PathVariable Long consignacaoId){
        return ResponseEntity.ok(service.adicionarPendentes(id, consignacaoId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> create(@RequestBody @Valid MontagemRequestDTO request){
        MontagemMostruario montagem = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(montagem.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(montagem));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> update(@RequestBody @Valid MontagemRequestDTO request){
        return ResponseEntity.ok(mapper.toResponseDTO(service.update(request)));
    }

    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/consignacoes/{consignacaoId}")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> vincularConsignacao(@PathVariable Long id, @PathVariable Long consignacaoId){
        return ResponseEntity.ok(mapper.toResponseDTO(service.vincularConsignacao(id, consignacaoId)));
    }

    @DeleteMapping(value = "/{id}/consignacoes/{consignacaoId}")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> desvincularConsignacao(@PathVariable Long id, @PathVariable Long consignacaoId){
        return ResponseEntity.ok(mapper.toResponseDTO(service.desvincularConsignacao(id, consignacaoId)));
    }

    @PostMapping(value = "/{id}/pedidos/{pedidoId}")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> vincularPedido(@PathVariable Long id, @PathVariable Long pedidoId){
        return ResponseEntity.ok(mapper.toResponseDTO(service.vincularPedido(id, pedidoId)));
    }

    @DeleteMapping(value = "/{id}/pedidos/{pedidoId}")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> desvincularPedido(@PathVariable Long id, @PathVariable Long pedidoId){
        return ResponseEntity.ok(mapper.toResponseDTO(service.desvincularPedido(id, pedidoId)));
    }

    // cria o pedido/consignação já vinculado à montagem; body igual ao de POST /pedidos e POST /consignacoes
    // (o clienteId é sempre o da montagem; o mostruário vem só do body, não é herdado da montagem)
    @PostMapping(value = "/{id}/pedidos")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<PedidoResponseDTO> criarPedido(@PathVariable Long id, @RequestBody PedidoRequestDTO request){
        Pedido pedido = service.criarPedido(id, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath().path("/pedidos/{id}").buildAndExpand(pedido.getId()).toUri();
        return ResponseEntity.created(location).body(pedidoMapper.toResponseDTO(pedido));
    }

    @PostMapping(value = "/{id}/consignacoes")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<ConsignacaoResponseDTO> criarConsignacao(@PathVariable Long id, @RequestBody ConsignacaoRequestDTO request){
        Consignacao consignacao = service.criarConsignacao(id, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath().path("/consignacoes/{id}").buildAndExpand(consignacao.getId()).toUri();
        return ResponseEntity.created(location).body(consignacaoMapper.toResponseDTO(consignacao));
    }

    // marca/desmarca uma instrução da montagem como feita
    @PatchMapping(value = "/{id}/instrucoes/{instrucaoId}")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> marcarInstrucao(@PathVariable Long id, @PathVariable Long instrucaoId,
                                                               @RequestBody @Valid com.manager.app.montagem.dto.MontagemInstrucaoMarcacaoRequestDTO request){
        return ResponseEntity.ok(mapper.toResponseDTO(service.marcarInstrucao(id, instrucaoId, request.getConcluida())));
    }

    @PatchMapping(value = "/{id}/iniciar")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> iniciar(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.iniciar(id)));
    }

    @PatchMapping(value = "/{id}/concluir")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> concluir(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.concluir(id)));
    }

    @PatchMapping(value = "/{id}/cancelar")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> cancelar(@PathVariable Long id, @RequestBody @Valid MontagemCancelamentoRequestDTO request){
        return ResponseEntity.ok(mapper.toResponseDTO(service.cancelar(id, request.getMotivo())));
    }
}
