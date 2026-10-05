package com.manager.app.backup.dto;

import java.time.LocalDateTime;

public record BackupArquivoResponseDTO(String nome, long tamanhoBytes, LocalDateTime criadoEm, String origem) {
}
