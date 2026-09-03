package com.manager.app.etiqueta;

import com.manager.app.etiqueta.dto.EtiquetaRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping(value="/etiquetas")
public class EtiquetaController {

    private final EtiquetaService service;

    @PostMapping(value = "/produtos")
    public ResponseEntity<byte[]> gerarRelProduto(@RequestBody EtiquetaRequestDTO request) {
        return ResponseEntity.ok().header("Content-Disposition", "inline; filename=relatorio-etiquetas.pdf").contentType(MediaType.APPLICATION_PDF).body(service.gerarRelatorio(request));

    }
}
