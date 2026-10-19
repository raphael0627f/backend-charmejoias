package com.manager.app.montagem;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MontagemValidation {

    public static final int MOTIVO_CANCELAMENTO_TAMANHO_MINIMO = 5;

    // só ABERTA e EM_MONTAGEM aceitam alteração; CONCLUIDA e CANCELADA são somente leitura
    public void validarEditavel(MontagemMostruario montagem){
        if (montagem.getStatus() == StatusMontagem.CONCLUIDA || montagem.getStatus() == StatusMontagem.CANCELADA) {
            throw new ItemInvalidoException("Montagem " + montagem.getId() + " está " + montagem.getStatus() + " e não pode ser alterada");
        }
    }

    // o cliente só troca enquanto a montagem não foi iniciada e não tem nenhum vínculo
    // (pedidos e consignações vinculados precisam ser do cliente da montagem)
    public void validarTrocaCliente(MontagemMostruario montagem, UUID novoClienteId){
        if (montagem.getCliente().getId().equals(novoClienteId)) return;
        if (montagem.getStatus() != StatusMontagem.ABERTA) {
            throw new ItemInvalidoException("O cliente só pode ser alterado enquanto a montagem está ABERTA. id: " + montagem.getId());
        }
        if (temVinculos(montagem)) {
            throw new ItemInvalidoException("Remova os pedidos e consignações vinculados antes de trocar o cliente da montagem. id: " + montagem.getId());
        }
    }

    public void validarExclusao(MontagemMostruario montagem){
        if (montagem.getStatus() != StatusMontagem.ABERTA) {
            throw new ItemInvalidoException("Só é possível excluir uma montagem ABERTA; use o cancelamento. id: " + montagem.getId());
        }
        if (temVinculos(montagem)) {
            throw new ObjetoEmUsoException("Montagem possui pedidos ou consignações vinculados e não pode ser excluída. id: " + montagem.getId());
        }
    }

    public void validarIniciar(MontagemMostruario montagem){
        if (montagem.getStatus() != StatusMontagem.ABERTA) {
            throw new ItemInvalidoException("Só é possível iniciar uma montagem ABERTA. Status atual: " + montagem.getStatus());
        }
    }

    public void validarConcluir(MontagemMostruario montagem){
        if (montagem.getStatus() != StatusMontagem.EM_MONTAGEM) {
            throw new ItemInvalidoException("Só é possível concluir uma montagem EM_MONTAGEM. Status atual: " + montagem.getStatus());
        }
        if (montagem.getConsignacoes().isEmpty()) {
            throw new ItemInvalidoException("Para concluir a montagem é necessário ao menos uma consignação vinculada");
        }
    }

    public void validarCancelar(MontagemMostruario montagem, String motivo){
        if (motivo == null || motivo.trim().length() < MOTIVO_CANCELAMENTO_TAMANHO_MINIMO) {
            throw new ItemInvalidoException("O motivo do cancelamento deve ter ao menos " + MOTIVO_CANCELAMENTO_TAMANHO_MINIMO + " caracteres");
        }
        if (montagem.getStatus() != StatusMontagem.ABERTA && montagem.getStatus() != StatusMontagem.EM_MONTAGEM) {
            throw new ItemInvalidoException("Só é possível cancelar uma montagem ABERTA ou EM_MONTAGEM. Status atual: " + montagem.getStatus());
        }
    }

    private boolean temVinculos(MontagemMostruario montagem){
        return !montagem.getConsignacoes().isEmpty() || !montagem.getPedidos().isEmpty();
    }
}
