package com.manager.app.rota;

import com.manager.app.rota.dto.RotaRequestDTO;
import com.manager.app.rota.dto.RotaResponseDTO;
import com.manager.app.rota.dto.RotaResumoDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/rotas")
public class RotaController {

    private final RotaService service;
    private final RotaMapper mapper;
    private final RotaXlsxService xlsxService;

    @GetMapping
    public ResponseEntity<Page<RotaResumoDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) com.manager.app.shared.ModoBusca modoBusca,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataFim,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAllResumo(busca, modoBusca, dataInicio, dataFim, pageable));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<RotaResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROTA_GERENCIAR')")
    public ResponseEntity<RotaResponseDTO> create(@RequestBody @Valid RotaRequestDTO request){
        Rota rota = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(rota.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(rota));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ROTA_GERENCIAR')")
    public ResponseEntity<RotaResponseDTO> update(@RequestBody @Valid RotaRequestDTO request){
        Rota rota = service.update(request);
        return ResponseEntity.ok(mapper.toResponseDTO(rota));
    }

    @GetMapping(value = "/{id}/xlsx")
    public ResponseEntity<byte[]> gerarXlsx(@PathVariable Long id){
        Rota rota = service.findById(id);
        byte[] arquivo = xlsxService.gerarXlsx(rota);
        String nomeArquivo = "rota-" + (rota.getNome() != null ? rota.getNome() : rota.getId()) + ".xlsx";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(arquivo);
    }
}
