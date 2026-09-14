package com.manager.app.spc;

import com.manager.app.spc.dto.SpcConsultaRequestDTO;
import com.manager.app.spc.dto.SpcConsultaResponseDTO;
import com.manager.app.spc.dto.SpcConsultaResumoDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/spc")
public class SpcConsultaController {

    private final SpcConsultaService service;

    @PostMapping("/consultas")
    @PreAuthorize("hasAuthority('SPC_CONSULTAR')")
    public ResponseEntity<SpcConsultaResponseDTO> consultar(@RequestBody @Valid SpcConsultaRequestDTO request){
        return ResponseEntity.ok(service.consultar(request));
    }

    @GetMapping("/consultas/{id}")
    public ResponseEntity<SpcConsultaResponseDTO> buscarPorId(@PathVariable UUID id){
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @GetMapping("/consultas")
    public ResponseEntity<Page<SpcConsultaResumoDTO>> findAll(
            @RequestParam(required = false) UUID clienteId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable){
        return ResponseEntity.ok(service.findAll(clienteId, pageable));
    }

    @GetMapping("/consultas/{id}/relatorio")
    public ResponseEntity<byte[]> gerarRelatorio(@PathVariable UUID id){
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=relatorio-spc-consulta.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.gerarRelatorio(id));
    }
}
