package com.manager.app.backup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// backup automático diário (padrão: meio-dia, horário de São Paulo)
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupService service;

    @Scheduled(cron = "${backup.cron:0 0 12 * * *}", zone = "${backup.zone:America/Sao_Paulo}")
    public void backupDiario(){
        try {
            service.executar(BackupService.ORIGEM_AUTOMATICO);
        } catch (RuntimeException e) {
            // não deixa a exceção subir: o log é o único registro de que o backup do dia falhou
            log.error("Falha no backup automático diário", e);
        }
    }
}
