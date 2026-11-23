package com.manager.app.infinitepay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// agenda a busca da fila do receptor: uma busca completa ao ligar o servidor e depois a cada N minutos (configurável em Sistema > InfinitePay),
// sempre que o servidor estiver ligado (sem janela de horário). Uma chamada curta por ciclo, em segundo plano; o intervalo é relido a cada ciclo.
// Receptor não configurado = ciclo sem efeito. Pode ser desligado por propriedade (app.infinitepay.fila-agendador-enabled=false).
@Slf4j
@Component
public class BaixaAutomaticaAgendador {

    private final BaixaAutomaticaService servico;
    private final InfinitePayAutomaticoService automatico;
    private final boolean habilitado;
    private final ScheduledExecutorService agenda = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "infinitepay-agenda");
        t.setDaemon(true);
        return t;
    });

    public BaixaAutomaticaAgendador(BaixaAutomaticaService servico, InfinitePayAutomaticoService automatico,
                                    @Value("${app.infinitepay.fila-agendador-enabled:true}") boolean habilitado) {
        this.servico = servico;
        this.automatico = automatico;
        this.habilitado = habilitado;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        if (!habilitado) {
            log.info("Agendador da fila de pagamentos desabilitado por propriedade.");
            return;
        }
        agenda.schedule(this::executar, 10, TimeUnit.SECONDS); // busca completa ao ligar
    }

    void executar() {
        long proximoMinutos = 2;
        try {
            servico.sincronizar();
        } catch (RuntimeException e) {
            log.warn("Ciclo da fila de pagamentos falhou: {}", e.getClass().getSimpleName());
        }
        try {
            proximoMinutos = Math.max(1, automatico.intervaloFilaMinutos());
        } catch (RuntimeException e) {
            log.warn("Não foi possível ler o intervalo da fila; usando 2 minutos.");
        }
        agenda.schedule(this::executar, proximoMinutos, TimeUnit.MINUTES);
    }

    @PreDestroy
    void parar() {
        agenda.shutdownNow();
    }
}
