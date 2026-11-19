package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.CategoriaDespesaRequestDTO;
import com.manager.app.financeiro.dto.CategoriaDespesaResponseDTO;
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
@RequestMapping("/categorias-despesa")
public class CategoriaDespesaController {

    private static final String CONSULTA = "hasAnyAuthority('CONTA_PAGAR_VISUALIZAR', 'CONTA_PAGAR_GERENCIAR', 'CONTA_PAGAR_PAGAR', 'CADASTRO_FINANCEIRO_GERENCIAR')";
    private static final String GERENCIA = "hasAuthority('CADASTRO_FINANCEIRO_GERENCIAR')";

    private final CategoriaDespesaService service;

    // árvore: grupos com suas subcategorias
    @GetMapping
    @PreAuthorize(CONSULTA)
    public ResponseEntity<List<CategoriaDespesaResponseDTO>> arvore(@RequestParam(required = false) Boolean ativo,
                                                                    @RequestParam(required = false, defaultValue = "false") boolean paraLancamento) {
        return ResponseEntity.ok(service.arvore(ativo, paraLancamento));
    }

    @GetMapping("/plana")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<List<CategoriaDespesaResponseDTO>> plana(@RequestParam(required = false) Boolean ativo,
                                                                   @RequestParam(required = false) String busca,
                                                                   @RequestParam(required = false, defaultValue = "false") boolean paraLancamento) {
        return ResponseEntity.ok(service.plana(ativo, busca, paraLancamento));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<CategoriaDespesaResponseDTO> obter(@PathVariable Long id) {
        return ResponseEntity.ok(service.obter(id));
    }

    @PostMapping
    @PreAuthorize(GERENCIA)
    public ResponseEntity<CategoriaDespesaResponseDTO> criar(@RequestBody @Valid CategoriaDespesaRequestDTO request) {
        CategoriaDespesaResponseDTO categoria = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(categoria.id()).toUri();
        return ResponseEntity.created(location).body(categoria);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<CategoriaDespesaResponseDTO> atualizar(@PathVariable Long id, @RequestBody @Valid CategoriaDespesaRequestDTO request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<CategoriaDespesaResponseDTO> ativar(@PathVariable Long id) {
        return ResponseEntity.ok(service.ativar(id));
    }

    @PatchMapping("/{id}/inativar")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<CategoriaDespesaResponseDTO> inativar(@PathVariable Long id) {
        return ResponseEntity.ok(service.inativar(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
