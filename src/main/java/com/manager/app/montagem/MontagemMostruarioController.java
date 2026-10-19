package com.manager.app.montagem;

import com.manager.app.montagem.dto.MontagemCancelamentoRequestDTO;
import com.manager.app.montagem.dto.MontagemRequestDTO;
import com.manager.app.montagem.dto.MontagemResponseDTO;
import com.manager.app.shared.ModoBusca;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/montagens")
public class MontagemMostruarioController {

    private final MontagemMostruarioService service;
    private final MontagemMostruarioMapper mapper;

    @GetMapping
    public ResponseEntity<Page<MontagemResponseDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) ModoBusca modoBusca,
            @RequestParam(required = false) StatusMontagem status,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataEntregaDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataEntregaAte,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, modoBusca, status, clienteId, dataEntregaDe, dataEntregaAte, pageable)
                .map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<MontagemResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> create(@RequestBody @Valid MontagemRequestDTO request){
        MontagemMostruario montagem = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(montagem.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(montagem));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> update(@RequestBody @Valid MontagemRequestDTO request){
        return ResponseEntity.ok(mapper.toResponseDTO(service.update(request)));
    }

    @DeleteMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(value = "/{id}/iniciar")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> iniciar(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.iniciar(id)));
    }

    @PatchMapping(value = "/{id}/concluir")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> concluir(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.concluir(id)));
    }

    @PatchMapping(value = "/{id}/cancelar")
    @PreAuthorize("hasAuthority('MONTAGEM_GERENCIAR')")
    public ResponseEntity<MontagemResponseDTO> cancelar(@PathVariable Long id, @RequestBody @Valid MontagemCancelamentoRequestDTO request){
        return ResponseEntity.ok(mapper.toResponseDTO(service.cancelar(id, request.getMotivo())));
    }
}
