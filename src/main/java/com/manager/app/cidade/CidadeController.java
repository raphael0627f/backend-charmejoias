package com.manager.app.cidade;

import com.manager.app.cidade.dto.CidadeResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// catálogo IBGE somente leitura; quem usa é a Agenda (rotas e cidades extras), por isso a permissão da agenda
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/cidades")
@PreAuthorize("hasAnyAuthority('AGENDA_VISUALIZAR', 'AGENDA_GERENCIAR')")
public class CidadeController {

    private final CidadeService service;
    private final CidadeMapper mapper;

    @GetMapping
    public ResponseEntity<Page<CidadeResponseDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) CidadeTipo tipo,
            @PageableDefault(size = 20, sort = {"nomeBusca", "uf", "ibge"}) Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, uf, tipo, pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<CidadeResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }
}
