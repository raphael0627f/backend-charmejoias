package com.manager.app.financeiro;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

// rotina diária que completa a janela de 12 meses das recorrências ativas (idempotente).
// Desativável com app.financeiro.recorrencia.enabled=false; horário em app.financeiro.recorrencia.cron (padrão 03:30, America/Sao_Paulo)
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.financeiro.recorrencia.enabled", havingValue = "true", matchIfMissing = true)
public class ContaPagarRecorrenciaScheduler {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final ContaPagarRecorrenciaService service;

    @Scheduled(cron = "${app.financeiro.recorrencia.cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    public void completarJanela() {
        int gerados = service.completarJanela(LocalDate.now(FUSO));
        log.info("Rotina de recorrências concluída: {} título(s) gerado(s).", gerados);
    }
}
