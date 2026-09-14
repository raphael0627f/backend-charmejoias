package com.manager.app.spc;

import com.manager.app.spc.dto.SpcInsumoOpcionalDTO;
import com.manager.app.spc.dto.SpcProdutoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/spc")
public class SpcCatalogoController {

    private final SpcCatalogoService service;

    @GetMapping("/naturezas-inclusao")
    public ResponseEntity<List<SpcNaturezaInclusao>> naturezasInclusao(){
        return ResponseEntity.ok(service.naturezasInclusao());
    }

    @GetMapping("/motivos-exclusao")
    public ResponseEntity<List<SpcMotivoExclusao>> motivosExclusao(){
        return ResponseEntity.ok(service.motivosExclusao());
    }

    @GetMapping("/produtos")
    public ResponseEntity<List<SpcProdutoDTO>> produtos(@RequestParam TipoPessoaSpc tipoPessoa){
        return ResponseEntity.ok(service.produtos(tipoPessoa));
    }

    @GetMapping("/produtos/{codigo}/insumos")
    public ResponseEntity<List<SpcInsumoOpcionalDTO>> insumosOpcionais(@PathVariable Integer codigo){
        return ResponseEntity.ok(service.insumosOpcionais(codigo));
    }
}
