package com.manager.app.notafiscal;

import com.manager.app.notafiscal.dto.CfopDisponivelDTO;
import com.manager.app.notafiscal.dto.NotaFiscalCancelamentoRequestDTO;
import com.manager.app.notafiscal.dto.NotaFiscalCorrecaoRequestDTO;
import com.manager.app.notafiscal.dto.NotaFiscalRequestDTO;
import com.manager.app.notafiscal.dto.NotaFiscalResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/notas-fiscais")
public class NotaFiscalController {

    private final NotaFiscalService service;
    private final NotaFiscalMapper mapper;

    @GetMapping
    public ResponseEntity<Page<NotaFiscalResponseDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusNotaFiscal status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataFim,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, status, dataInicio, dataFim, pageable).map(mapper::toResponseDTO));
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

    @PutMapping(value = "/{id}")
    public ResponseEntity<NotaFiscalResponseDTO> update(@PathVariable Long id, @RequestBody @Valid NotaFiscalRequestDTO notaFiscalRequestDTO){
        return ResponseEntity.ok(mapper.toResponseDTO(service.update(id, notaFiscalRequestDTO)));
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
    public ResponseEntity<NotaFiscalResponseDTO> corrigir(@PathVariable Long id, @RequestBody @Valid NotaFiscalCorrecaoRequestDTO request){
        return ResponseEntity.ok(mapper.toResponseDTO(service.corrigir(id, request.getTexto())));
    }

    @GetMapping(value = "/{id}/xml")
    public ResponseEntity<byte[]> baixarXml(@PathVariable Long id){
        byte[] xml = service.baixarArtefato(id, "xml_authorized");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml);
    }

    @GetMapping(value = "/{id}/xml-correcao")
    public ResponseEntity<byte[]> baixarXmlCorrecao(@PathVariable Long id){
        byte[] xml = service.baixarArtefato(id, "xml_correction");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml);
    }

    @GetMapping(value = "/{id}/danfe")
    public ResponseEntity<byte[]> baixarDanfe(@PathVariable Long id){
        byte[] pdf = service.baixarArtefato(id, "pdf_danfe");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }
}
