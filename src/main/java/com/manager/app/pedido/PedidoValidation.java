package com.manager.app.pedido;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.TipoItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PedidoValidation {

    // Encomenda não passa por Produção; as demais categorias nascem em Produção
    private static final Map<StatusPedido, Set<StatusPedido>> TRANSICOES = Map.of(
            StatusPedido.PRODUCAO, EnumSet.of(StatusPedido.PENDENTE, StatusPedido.SEPARACAO, StatusPedido.CANCELADO),
            StatusPedido.PENDENTE, EnumSet.of(StatusPedido.SEPARACAO, StatusPedido.SEPARADO),
            StatusPedido.SEPARACAO, EnumSet.of(StatusPedido.SEPARADO, StatusPedido.CANCELADO),
            StatusPedido.SEPARADO, EnumSet.of(StatusPedido.ENVIADO, StatusPedido.CANCELADO),
            StatusPedido.ENVIADO, EnumSet.noneOf(StatusPedido.class),
            StatusPedido.CANCELADO, EnumSet.noneOf(StatusPedido.class)
    );

    public StatusPedido statusInicial(CategoriaPedido categoria){
        return categoria == CategoriaPedido.ENCOMENDA ? StatusPedido.PENDENTE : StatusPedido.PRODUCAO;
    }

    public void validarTransicao(Long pedidoId, StatusPedido atual, StatusPedido novo){
        if (!TRANSICOES.getOrDefault(atual, Set.of()).contains(novo)) {
            throw new ItemInvalidoException("Não é possível mudar o pedido " + pedidoId + " de " + atual + " para " + novo);
        }
    }

    // só dá pra marcar/desmarcar item como separado enquanto o pedido está em separação
    public void validarMarcacaoSeparado(Pedido pedido){
        if (pedido.getStatus() != StatusPedido.SEPARACAO) {
            throw new ItemInvalidoException("Itens só podem ser marcados como separados com o pedido em separação. id: " + pedido.getId());
        }
    }

    // o pedido só vira SEPARADO quando todos os itens ainda disponíveis (não totalmente
    // cancelados) já foram separados
    public void validarTodosSeparados(Pedido pedido){
        boolean faltaSeparar = pedido.getProdutos().stream().anyMatch(item -> {
            int cancelada = item.getQuantidadeCancelada() != null ? item.getQuantidadeCancelada() : 0;
            boolean disponivel = item.getQuantidade() - cancelada > 0;
            return disponivel && !item.isSeparado();
        });
        if (faltaSeparar) {
            throw new ItemInvalidoException("O pedido " + pedido.getId() + " ainda tem itens não separados");
        }
    }

    // itens/dados só podem ser editados antes de a separação começar
    public void validarEdicao(Pedido pedido){
        if (pedido.getStatus() != StatusPedido.PRODUCAO && pedido.getStatus() != StatusPedido.PENDENTE) {
            throw new ItemInvalidoException("Pedido " + pedido.getId() + " não pode ser editado no status " + pedido.getStatus());
        }
    }

    public boolean produtoNovo(UUID produtoId){
        return produtoId == null;
    }

    public void validarItem(TipoItem tipo, UUID produtoId, UUID joiaId, Long produtoVariacaoId){
        if (tipo == TipoItem.JOIA) {
            if (joiaId == null) {
                throw new ItemInvalidoException("joiaId é obrigatório para pedidos do tipo JOIA");
            }
            if (produtoId != null) {
                throw new ItemInvalidoException("Não é possível informar produtoId em um pedido do tipo JOIA");
            }
            if (produtoVariacaoId != null) {
                throw new ItemInvalidoException("Não é possível informar produtoVariacaoId em um pedido do tipo JOIA");
            }
        } else {
            if (joiaId != null) {
                throw new ItemInvalidoException("Não é possível informar joiaId em um pedido do tipo PRODUTO");
            }
            if (produtoVariacaoId != null && produtoNovo(produtoId)) {
                throw new ItemInvalidoException("produtoId é obrigatório para informar produtoVariacaoId");
            }
        }
    }
}
