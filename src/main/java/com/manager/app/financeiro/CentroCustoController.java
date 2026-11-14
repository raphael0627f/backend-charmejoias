package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.CadastroSimplesRequestDTO;
import com.manager.app.financeiro.dto.CadastroSimplesResponseDTO;
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
@RequestMapping("/centros-custo")
public class CentroCustoController {

    private static final String CONSULTA = "hasAnyAuthority('CONTA_PAGAR_VISUALIZAR', 'CONTA_PAGAR_GERENCIAR', 'CONTA_PAGAR_BAIXAR', 'CADASTRO_FINANCEIRO_GERENCIAR')";
    private static final String GERENCIA = "hasAuthority('CADASTRO_FINANCEIRO_GERENCIAR')";

    private final CentroCustoService service;

    @GetMapping
    @PreAuthorize(CONSULTA)
    public ResponseEntity<List<CadastroSimplesResponseDTO>> listar(@RequestParam(required = false) Boolean ativo,
                                                                   @RequestParam(required = false) String busca) {
        return ResponseEntity.ok(service.listar(ativo, busca));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<CadastroSimplesResponseDTO> obter(@PathVariable Long id) {
        return ResponseEntity.ok(service.obter(id));
    }

    @PostMapping
    @PreAuthorize(GERENCIA)
    public ResponseEntity<CadastroSimplesResponseDTO> criar(@RequestBody @Valid CadastroSimplesRequestDTO request) {
        CadastroSimplesResponseDTO centro = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(centro.id()).toUri();
        return ResponseEntity.created(location).body(centro);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<CadastroSimplesResponseDTO> atualizar(@PathVariable Long id, @RequestBody @Valid CadastroSimplesRequestDTO request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<CadastroSimplesResponseDTO> ativar(@PathVariable Long id) {
        return ResponseEntity.ok(service.ativar(id));
    }

    @PatchMapping("/{id}/inativar")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<CadastroSimplesResponseDTO> inativar(@PathVariable Long id) {
        return ResponseEntity.ok(service.inativar(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
