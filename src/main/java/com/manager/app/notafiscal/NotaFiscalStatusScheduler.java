package com.manager.app.notafiscal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
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
            // ENVIADA sem contoraDocumentId: ou o envio está em andamento (rascunho ainda não
            // criado na Contora) ou ficou preso por uma falha - não há o que consultar
            if (notaFiscal.getContoraDocumentId() == null) {
                log.debug("Nota fiscal {} sem documento na Contora ainda, pulando a consulta de status", notaFiscal.getId());
                continue;
            }
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
            // erro pontual numa nota não pode travar a checagem das outras, mas precisa ficar
            // registrado - antes esse catch engolia a exceção em silêncio, então uma falha na
            // consulta (ex.: erro da API da Contora) deixava a nota presa em ENVIADA pra sempre
            // sem nenhum rastro do motivo
            log.error("Falha ao consultar status da nota fiscal {} na Contora", id, e);
        }
    }
}
