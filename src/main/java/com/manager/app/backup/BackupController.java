package com.manager.app.backup;

import com.manager.app.backup.dto.BackupArquivoResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

// só quem tem BACKUP_GERENCIAR (perfil ADMIN) - o dump contém todos os dados do sistema
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/backups")
@PreAuthorize("hasAuthority('BACKUP_GERENCIAR')")
public class BackupController {

    private final BackupService service;

    @GetMapping
    public ResponseEntity<List<BackupArquivoResponseDTO>> listar(){
        return ResponseEntity.ok(service.listar());
    }

    // gera um backup agora (manual)
    @PostMapping
    public ResponseEntity<BackupArquivoResponseDTO> gerar(){
        return ResponseEntity.ok(service.executar(BackupService.ORIGEM_MANUAL));
    }

    @GetMapping(value = "/{arquivo}")
    public ResponseEntity<Resource> baixar(@PathVariable String arquivo){
        Path caminho = service.caminhoDoBackup(arquivo);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + caminho.getFileName() + "\"")
                .body(new FileSystemResource(caminho));
    }
}
