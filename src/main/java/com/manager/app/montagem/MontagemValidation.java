package com.manager.app.montagem;

import com.manager.app.consignacao.Consignacao;
import com.manager.app.pedido.CategoriaPedido;
import com.manager.app.pedido.Pedido;
import com.manager.app.pedido.StatusPedido;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.montagem.dto.MontagemConferenciaDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import java.util.Optional;
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
        validarConsignacaoOuOrdemServico(montagem);
    }

    // a montagem precisa ter ao menos uma consignação OU ao menos um pedido de O.S. (não cancelado). Se só tiver pedidos
    // de O.S., pode ser concluída sem consignação; havendo qualquer pedido que não seja O.S., a consignação é obrigatória
    private void validarConsignacaoOuOrdemServico(MontagemMostruario montagem){
        if (!montagem.getConsignacoes().isEmpty()) return;
        List<Pedido> ativos = montagem.getPedidos().stream()
                .filter(p -> p.getStatus() != StatusPedido.CANCELADO)
                .toList();
        boolean temOrdemServico = ativos.stream().anyMatch(p -> p.getCategoria() == CategoriaPedido.ORDEM_SERVICO);
        boolean temOutroTipo = ativos.stream().anyMatch(p -> p.getCategoria() != CategoriaPedido.ORDEM_SERVICO);
        if (temOutroTipo) {
            throw new ItemInvalidoException("Esta montagem tem pedidos que não são O.S.: é necessário ao menos uma consignação vinculada para concluí-la");
        }
        if (!temOrdemServico) {
            throw new ItemInvalidoException("Para concluir a montagem é necessário ao menos uma consignação ou um pedido do tipo O.S.");
        }
    }

    // só conclui com todos os pedidos (não cancelados) separados, sem item avulso pendente de vínculo e com
    // todos os produtos dos pedidos já nas consignações vinculadas
    public void validarConferencia(MontagemConferenciaDTO conferencia){
        if (conferencia.ok()) return;
        List<String> motivos = new ArrayList<>();
        if (!conferencia.pedidosNaoSeparados().isEmpty()) {
            motivos.add("pedidos ainda não separados: " + conferencia.pedidosNaoSeparados().stream()
                    .map(p -> String.valueOf(p.pedidoId())).collect(Collectors.joining(", ")));
        }
        if (!conferencia.avulsosSemVinculo().isEmpty()) {
            motivos.add("itens avulsos sem produto vinculado nos pedidos: " + conferencia.avulsosSemVinculo().stream()
                    .map(a -> String.valueOf(a.pedidoId())).distinct().collect(Collectors.joining(", ")));
        }
        if (!conferencia.instrucoesPendentes().isEmpty()) {
            motivos.add("instruções ainda não marcadas como feitas: " + String.join(", ", conferencia.instrucoesPendentes()));
        }
        List<String> faltantes = conferencia.produtos().stream()
                .filter(l -> l.faltando() > 0)
                .map(l -> l.nome() + " (faltam " + l.faltando() + ")")
                .toList();
        if (!faltantes.isEmpty()) {
            motivos.add("produtos dos pedidos que não estão nas consignações: " + String.join(", ", faltantes));
        }
        throw new ItemInvalidoException("Não é possível concluir a montagem - " + String.join("; ", motivos));
    }

    public void validarCancelar(MontagemMostruario montagem, String motivo){
        if (motivo == null || motivo.trim().length() < MOTIVO_CANCELAMENTO_TAMANHO_MINIMO) {
            throw new ItemInvalidoException("O motivo do cancelamento deve ter ao menos " + MOTIVO_CANCELAMENTO_TAMANHO_MINIMO + " caracteres");
        }
        if (montagem.getStatus() != StatusMontagem.ABERTA && montagem.getStatus() != StatusMontagem.EM_MONTAGEM) {
            throw new ItemInvalidoException("Só é possível cancelar uma montagem ABERTA ou EM_MONTAGEM. Status atual: " + montagem.getStatus());
        }
    }

    // item (consignação/pedido) a vincular: do mesmo cliente da montagem e ainda livre
    public void validarVinculoConsignacao(MontagemMostruario montagem, Consignacao consignacao, Optional<MontagemMostruario> vinculoAtual){
        validarEditavel(montagem);
        if (consignacao.getCliente() == null || !consignacao.getCliente().getId().equals(montagem.getCliente().getId())) {
            throw new ItemInvalidoException("A consignação " + consignacao.getId() + " não pertence ao cliente da montagem");
        }
        validarSemOutraMontagem(montagem, "Consignação " + consignacao.getId(), vinculoAtual);
    }

    public void validarVinculoPedido(MontagemMostruario montagem, Pedido pedido, Optional<MontagemMostruario> vinculoAtual){
        validarEditavel(montagem);
        // pedido sem cliente pode ser vinculado: recebe o cliente (e a data de entrega) da montagem ao concluí-la
        if (pedido.getCliente() != null && !pedido.getCliente().getId().equals(montagem.getCliente().getId())) {
            throw new ItemInvalidoException("O pedido " + pedido.getId() + " não pertence ao cliente da montagem");
        }
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new ItemInvalidoException("O pedido " + pedido.getId() + " está cancelado e não pode ser vinculado à montagem");
        }
        validarSemOutraMontagem(montagem, "Pedido " + pedido.getId(), vinculoAtual);
    }

    private void validarSemOutraMontagem(MontagemMostruario montagem, String item, Optional<MontagemMostruario> vinculoAtual){
        vinculoAtual.ifPresent(outra -> {
            if (outra.getId().equals(montagem.getId())) {
                throw new ItemInvalidoException(item + " já está vinculado(a) a esta montagem");
            }
            throw new ObjetoEmUsoException(item + " já está vinculado(a) à montagem " + outra.getId());
        });
    }

    private boolean temVinculos(MontagemMostruario montagem){
        return !montagem.getConsignacoes().isEmpty() || !montagem.getPedidos().isEmpty();
    }
}
