package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.BancoRequestDTO;
import com.manager.app.financeiro.dto.BancoResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bancos")
public class BancoController {

    private static final String CONSULTA = "hasAnyAuthority('CONTA_RECEBER_VISUALIZAR', 'CONTA_RECEBER_GERENCIAR', 'CONTA_RECEBER_RECEBER', 'CONTA_PAGAR_VISUALIZAR', 'CONTA_PAGAR_GERENCIAR', 'CONTA_PAGAR_PAGAR', "
            + "'CADASTRO_FINANCEIRO_GERENCIAR', 'MOVIMENTACAO_VISUALIZAR', 'MOVIMENTACAO_GERENCIAR')";
    private static final String GERENCIA = "hasAuthority('CADASTRO_FINANCEIRO_GERENCIAR')";

    private final BancoService service;

    @GetMapping
    @PreAuthorize(CONSULTA)
    public ResponseEntity<List<BancoResponseDTO>> listar(@RequestParam(required = false) Boolean ativo,
                                                         @RequestParam(required = false) String busca,
                                                         @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(service.listar(ativo, busca, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<BancoResponseDTO> obter(@PathVariable Long id) {
        return ResponseEntity.ok(service.obter(id));
    }

    @PostMapping
    @PreAuthorize(GERENCIA)
    public ResponseEntity<BancoResponseDTO> criar(@RequestBody @Valid BancoRequestDTO request) {
        BancoResponseDTO banco = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(banco.id()).toUri();
        return ResponseEntity.created(location).body(banco);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<BancoResponseDTO> atualizar(@PathVariable Long id, @RequestBody @Valid BancoRequestDTO request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<BancoResponseDTO> ativar(@PathVariable Long id) {
        return ResponseEntity.ok(service.ativar(id));
    }

    @PatchMapping("/{id}/inativar")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<BancoResponseDTO> inativar(@PathVariable Long id) {
        return ResponseEntity.ok(service.inativar(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
