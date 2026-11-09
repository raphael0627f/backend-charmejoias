package com.manager.app.planejadorrota;

import com.manager.app.planejadorrota.dto.PlanejadorRotaRequestDTO;
import com.manager.app.planejadorrota.dto.PlanejadorRotaResponseDTO;
import com.manager.app.planejadorrota.dto.PlanejadorRotaResumoDTO;
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
@RequestMapping(value = "/planejador-rotas")
public class PlanejadorRotaController {

    private final PlanejadorRotaService service;
    private final PlanejadorRotaMapper mapper;
    private final PlanejadorRotaXlsxService xlsxService;

    @GetMapping
    public ResponseEntity<Page<PlanejadorRotaResumoDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) com.manager.app.shared.ModoBusca modoBusca,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataFim,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAllResumo(busca, modoBusca, dataInicio, dataFim, pageable));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<PlanejadorRotaResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PLANEJADOR_ROTA_GERENCIAR')")
    public ResponseEntity<PlanejadorRotaResponseDTO> create(@RequestBody @Valid PlanejadorRotaRequestDTO request){
        PlanejadorRota rota = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(rota.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(rota));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PLANEJADOR_ROTA_GERENCIAR')")
    public ResponseEntity<PlanejadorRotaResponseDTO> update(@RequestBody @Valid PlanejadorRotaRequestDTO request){
        PlanejadorRota rota = service.update(request);
        return ResponseEntity.ok(mapper.toResponseDTO(rota));
    }

    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('PLANEJADOR_ROTA_GERENCIAR')")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}/xlsx")
    public ResponseEntity<byte[]> gerarXlsx(@PathVariable Long id){
        PlanejadorRota rota = service.findById(id);
        byte[] arquivo = xlsxService.gerarXlsx(rota);
        String nomeArquivo = "planejador-rota-" + (rota.getNome() != null ? rota.getNome() : rota.getId()) + ".xlsx";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(arquivo);
    }
}
