package com.manager.app.infinitepay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
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
        agenda.scheduleWithFixedDelay(this::verificar, 70, 60, TimeUnit.SECONDS); // confere a cada minuto se já passou o intervalo configurado
    }

    // a cada minuto: se passou o intervalo configurado desde a última busca do agendador, busca de novo (mudar o intervalo vale em até 1 minuto)
    void verificar() {
        try {
            if (deveExecutar(LocalDateTime.now(), ultimaBusca, automatico.intervaloFilaMinutos())) executar();
        } catch (RuntimeException e) {
            log.warn("Verificação do agendador da fila de pagamentos falhou: {}", e.getClass().getSimpleName());
        }
    }

    static boolean deveExecutar(LocalDateTime agora, LocalDateTime ultima, int intervaloMinutos) {
        return ultima == null || !ultima.plusMinutes(Math.max(1, intervaloMinutos)).isAfter(agora);
    }

    void executar() {
        ultimaBusca = LocalDateTime.now();
        try {
            servico.sincronizar();
        } catch (RuntimeException e) {
            log.warn("Ciclo da fila de pagamentos falhou: {}", e.getClass().getSimpleName());
        }
    }

    private volatile LocalDateTime ultimaBusca;

    @PreDestroy
    void parar() {
        agenda.shutdownNow();
    }
}
