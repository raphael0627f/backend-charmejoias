package com.manager.app.notafiscal;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// isolado do NotaFiscalService pra rodar em transação própria (REQUIRES_NEW): commita o avanço
// da tentativa de envio (e o status ENVIADA) ANTES de chamar a Contora, pra nunca reutilizar a
// mesma Idempotency-Key numa nova tentativa se o envio anterior falhou no meio do caminho e a
// transação principal de emitir() deu rollback (o rollback desfazia o incremento junto, então o
// retry gerava a mesma chave "nota-{id}-{tentativa}-create" com um payload diferente -> 409 na Contora)
@Service
@RequiredArgsConstructor
public class NotaFiscalTentativaEnvioService {

    private final NotaFiscalRepository repository;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int iniciarNovaTentativa(Long id){
        NotaFiscal notaFiscal = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Nota fiscal não encontrada. id: " + id));
        if (notaFiscal.getStatus() != StatusNotaFiscal.RASCUNHO) {
            throw new ItemInvalidoException("Nota fiscal já foi enviada. id: " + id + ", status atual: " + notaFiscal.getStatus());
        }
        notaFiscal.setStatus(StatusNotaFiscal.ENVIADA);
        int tentativa = notaFiscal.getNumeroTentativaEnvio() + 1;
        notaFiscal.setNumeroTentativaEnvio(tentativa);
        return tentativa;
    }

    // chamado quando a comunicação com a Contora falha depois que a tentativa já foi commitada:
    // devolve a nota pra RASCUNHO (permitindo reenvio) sem resetar numeroTentativaEnvio, pra a
    // próxima tentativa sempre gerar uma Idempotency-Key nova
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void reverterParaRascunho(Long id){
        repository.findByIdForUpdate(id).ifPresent(notaFiscal -> {
            if (notaFiscal.getStatus() == StatusNotaFiscal.ENVIADA) {
                notaFiscal.setStatus(StatusNotaFiscal.RASCUNHO);
            }
        });
    }
}
