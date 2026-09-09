package com.manager.app.acertoconsignacao;

import com.manager.app.acertoconsignacao.dto.AcertoConsignacaoRequestDTO;
import com.manager.app.acertoconsignacao.dto.AcertoConsignacaoResponseDTO;
import com.manager.app.acertoconsignacao.dto.ProdutoVendidoDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/acertos-consignacao")
public class AcertoConsignacaoController {

    private final AcertoConsignacaoService service;
    private final AcertoConsignacaoMapper mapper;

    @GetMapping
    public ResponseEntity<Page<AcertoConsignacaoResponseDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long consignacaoId,
            @RequestParam(required = false) Boolean confirmado,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataFim,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, consignacaoId, confirmado, dataInicio, dataFim, pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<AcertoConsignacaoResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<AcertoConsignacaoResponseDTO> create(@RequestBody @Valid AcertoConsignacaoRequestDTO request){
        AcertoConsignacao acerto = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(acerto.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(acerto));
    }

    @PutMapping
    public ResponseEntity<AcertoConsignacaoResponseDTO> update(@RequestBody @Valid AcertoConsignacaoRequestDTO request){
        AcertoConsignacao acerto = service.update(request);
        return ResponseEntity.ok(mapper.toResponseDTO(acerto));
    }

    @PutMapping(value = "/{id}/confirmar")
    public ResponseEntity<AcertoConsignacaoResponseDTO> confirmar(@PathVariable Long id){
        AcertoConsignacao acerto = service.confirmar(id);
        return ResponseEntity.ok(mapper.toResponseDTO(acerto));
    }

    @GetMapping(value = "/{id}/produtos-vendidos")
    public ResponseEntity<List<ProdutoVendidoDTO>> produtosVendidos(@PathVariable Long id){
        return ResponseEntity.ok(service.produtosVendidos(id));
    }
}
