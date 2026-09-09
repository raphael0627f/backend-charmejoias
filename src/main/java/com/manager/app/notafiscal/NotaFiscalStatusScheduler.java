package com.manager.app.notafiscal;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class NotaFiscalStatusScheduler {

    private static final List<StatusNotaFiscal> STATUS_PENDENTES = List.of(
            StatusNotaFiscal.ENVIADA,
            StatusNotaFiscal.CANCELAMENTO_SOLICITADO
    );

    private final NotaFiscalRepository repository;
    private final NotaFiscalService service;

    // o servidor roda localmente, sem IP público - a Contora não consegue chamar nosso webhook,
    // então essa consulta periódica é o mecanismo principal de atualização de status
    @Scheduled(fixedDelayString = "${nfe.status-polling.fixed-delay:30000}")
    public void consultarNotasPendentes(){
        Set<Long> jaConsultadas = new HashSet<>();

        for (NotaFiscal notaFiscal : repository.findAllByStatusIn(STATUS_PENDENTES)) {
            jaConsultadas.add(notaFiscal.getId());
            atualizarStatusSemQuebrar(notaFiscal.getId());
        }

        // correção não muda o status da nota (ela continua AUTORIZADA), então precisa de uma
        // busca à parte pra achar notas com carta de correção ainda aguardando confirmação da SEFAZ
        for (NotaFiscal notaFiscal : repository.findAllComCorrecaoPendente()) {
            if (jaConsultadas.add(notaFiscal.getId())) {
                atualizarStatusSemQuebrar(notaFiscal.getId());
            }
        }
    }

    private void atualizarStatusSemQuebrar(Long id){
        try {
            service.atualizarStatus(id);
        } catch (Exception e) {
            // erro pontual numa nota não pode travar a checagem das outras
        }
    }
}
