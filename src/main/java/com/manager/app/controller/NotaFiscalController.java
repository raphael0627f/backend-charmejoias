package com.manager.app.controller;

import com.manager.app.dto.notafiscal.CfopDisponivelDTO;
import com.manager.app.dto.notafiscal.NotaFiscalCancelamentoRequestDTO;
import com.manager.app.dto.notafiscal.NotaFiscalCorrecaoRequestDTO;
import com.manager.app.dto.notafiscal.NotaFiscalRequestDTO;
import com.manager.app.dto.notafiscal.NotaFiscalResponseDTO;
import com.manager.app.entity.NotaFiscal;
import com.manager.app.mapper.NotaFiscalMapper;
import com.manager.app.service.NotaFiscalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/notas-fiscais")
public class NotaFiscalController {

    private final NotaFiscalService service;
    private final NotaFiscalMapper mapper;

    @GetMapping
    public ResponseEntity<Page<NotaFiscalResponseDTO>> findAll(@PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<NotaFiscalResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @GetMapping(value = "/cfops-disponiveis")
    public ResponseEntity<List<CfopDisponivelDTO>> cfopsDisponiveis(
            @RequestParam Long empresaId,
            @RequestParam UUID clienteId){
        return ResponseEntity.ok(service.cfopsDisponiveis(empresaId, clienteId));
    }

    @PostMapping
    public ResponseEntity<NotaFiscalResponseDTO> create(@RequestBody @Valid NotaFiscalRequestDTO notaFiscalRequestDTO){
        NotaFiscal notaFiscal = service.create(notaFiscalRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(notaFiscal.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(notaFiscal));
    }

    @PostMapping(value = "/{id}/emitir")
    public ResponseEntity<NotaFiscalResponseDTO> emitir(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.emitir(id)));
    }

    @PostMapping(value = "/{id}/status")
    public ResponseEntity<NotaFiscalResponseDTO> atualizarStatus(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.atualizarStatus(id)));
    }

    @PostMapping(value = "/{id}/cancelar")
    public ResponseEntity<NotaFiscalResponseDTO> cancelar(@PathVariable Long id, @RequestBody @Valid NotaFiscalCancelamentoRequestDTO request){
        return ResponseEntity.ok(mapper.toResponseDTO(service.cancelar(id, request.getMotivo())));
    }

    @PostMapping(value = "/{id}/corrigir")
    public ResponseEntity<Void> corrigir(@PathVariable Long id, @RequestBody @Valid NotaFiscalCorrecaoRequestDTO request){
        service.corrigir(id, request.getTexto());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}/xml")
    public ResponseEntity<byte[]> baixarXml(@PathVariable Long id){
        byte[] xml = service.baixarArtefato(id, "xml_authorized");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml);
    }

    @GetMapping(value = "/{id}/danfe")
    public ResponseEntity<byte[]> baixarDanfe(@PathVariable Long id){
        byte[] pdf = service.baixarArtefato(id, "pdf_danfe");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }
}
