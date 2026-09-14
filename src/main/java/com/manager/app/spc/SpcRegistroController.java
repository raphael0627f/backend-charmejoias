package com.manager.app.spc;

import com.manager.app.spc.dto.SpcExclusaoRequestDTO;
import com.manager.app.spc.dto.SpcInclusaoRequestDTO;
import com.manager.app.spc.dto.SpcRegistroResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/spc")
public class SpcRegistroController {

    private final SpcRegistroService service;

    @PostMapping("/inclusoes")
    @PreAuthorize("hasAuthority('SPC_REGISTRAR')")
    public ResponseEntity<SpcRegistroResponseDTO> incluir(@RequestBody @Valid SpcInclusaoRequestDTO request){
        return ResponseEntity.ok(service.incluir(request));
    }

    @PostMapping("/exclusoes")
    @PreAuthorize("hasAuthority('SPC_REGISTRAR')")
    public ResponseEntity<SpcRegistroResponseDTO> excluir(@RequestBody @Valid SpcExclusaoRequestDTO request){
        return ResponseEntity.ok(service.excluir(request));
    }

    @GetMapping("/registros")
    public ResponseEntity<Page<SpcRegistroResponseDTO>> findAll(
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) TipoOperacaoSpc tipoOperacao,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable){
        return ResponseEntity.ok(service.findAll(clienteId, tipoOperacao, pageable));
    }
}
