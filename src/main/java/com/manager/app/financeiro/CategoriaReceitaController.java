package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.CategoriaReceitaRequestDTO;
import com.manager.app.financeiro.dto.CategoriaReceitaResponseDTO;
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
@RequestMapping("/categorias-receita")
public class CategoriaReceitaController {

    private static final String CONSULTA = "hasAnyAuthority('CONTA_RECEBER_VISUALIZAR', 'CONTA_RECEBER_GERENCIAR', 'CONTA_RECEBER_RECEBER', "
            + "'CONTA_PAGAR_VISUALIZAR', 'CONTA_PAGAR_GERENCIAR', 'CONTA_PAGAR_PAGAR', 'CADASTRO_FINANCEIRO_GERENCIAR')";
    private static final String GERENCIA = "hasAuthority('CADASTRO_FINANCEIRO_GERENCIAR')";

    private final CategoriaReceitaService service;

    // árvore: grupos com suas subcategorias
    @GetMapping
    @PreAuthorize(CONSULTA)
    public ResponseEntity<List<CategoriaReceitaResponseDTO>> arvore(@RequestParam(required = false) Boolean ativo) {
        return ResponseEntity.ok(service.arvore(ativo));
    }

    @GetMapping("/plana")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<List<CategoriaReceitaResponseDTO>> plana(@RequestParam(required = false) Boolean ativo,
                                                                   @RequestParam(required = false) String busca) {
        return ResponseEntity.ok(service.plana(ativo, busca));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<CategoriaReceitaResponseDTO> obter(@PathVariable Long id) {
        return ResponseEntity.ok(service.obter(id));
    }

    @PostMapping
    @PreAuthorize(GERENCIA)
    public ResponseEntity<CategoriaReceitaResponseDTO> criar(@RequestBody @Valid CategoriaReceitaRequestDTO request) {
        CategoriaReceitaResponseDTO categoria = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(categoria.id()).toUri();
        return ResponseEntity.created(location).body(categoria);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<CategoriaReceitaResponseDTO> atualizar(@PathVariable Long id, @RequestBody @Valid CategoriaReceitaRequestDTO request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<CategoriaReceitaResponseDTO> ativar(@PathVariable Long id) {
        return ResponseEntity.ok(service.ativar(id));
    }

    @PatchMapping("/{id}/inativar")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<CategoriaReceitaResponseDTO> inativar(@PathVariable Long id) {
        return ResponseEntity.ok(service.inativar(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(GERENCIA)
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
