package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.ExcedenteDTOs;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/excedentes-acerto")
@RequiredArgsConstructor
public class ExcedenteAcertoController {

    private static final String CONSULTA = "hasAnyAuthority('CREDITO_CLIENTE_VISUALIZAR', 'CREDITO_CLIENTE_GERENCIAR', 'CREDITO_CLIENTE_APLICAR')";

    private final ExcedenteAcertoService service;

    @GetMapping
    @PreAuthorize(CONSULTA)
    public ResponseEntity<ExcedenteDTOs.Lista> listar(@RequestParam(required = false) String situacao, @RequestParam(required = false) String busca,
                                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listar(situacao, busca, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<ExcedenteDTOs.Detalhe> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalhe(id));
    }
}
