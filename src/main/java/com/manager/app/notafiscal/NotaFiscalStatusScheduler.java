package com.manager.app.notafiscal;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotaFiscalStatusScheduler {

    private final NotaFiscalRepository repository;
    private final NotaFiscalService service;

    // o servidor roda localmente, sem IP público - a Contora não consegue chamar nosso webhook,
    // então essa consulta periódica é o mecanismo principal de atualização de status
    @Scheduled(fixedDelayString = "${nfe.status-polling.fixed-delay:30000}")
    public void consultarNotasPendentes(){
        for (NotaFiscal notaFiscal : repository.findAllByStatus(StatusNotaFiscal.ENVIADA)) {
            try {
                service.atualizarStatus(notaFiscal.getId());
            } catch (Exception e) {
                // erro pontual numa nota não pode travar a checagem das outras
            }
        }
    }
}
