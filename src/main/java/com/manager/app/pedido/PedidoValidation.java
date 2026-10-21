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

    // item avulso = sem produto nem joia cadastrados (só descrição/grupo digitados)
    public boolean itemAvulso(PedidoProduto item){
        return item.getProduto() == null && item.getJoia() == null;
    }

    // um item avulso precisa ser vinculado a um produto cadastrado antes de ser separado
    public void validarItemVinculado(Pedido pedido, PedidoProduto item){
        if (itemAvulso(item)) {
            throw new ItemInvalidoException("O item avulso \"" + item.getDescricao() + "\" do pedido " + pedido.getId()
                    + " precisa ser vinculado a um produto cadastrado antes de ser separado");
        }
    }

    // item que nasceu avulso e já foi vinculado a um produto: a descrição digitada continua guardada (item
    // cadastrado normal sempre tem descricao nula). Esse vínculo ainda pode ser trocado na separação
    public boolean itemVinculadoDeAvulso(PedidoProduto item){
        return item.getProduto() != null && item.getDescricao() != null && !item.getDescricao().isBlank();
    }

    // produtos dessa categoria (ex.: "variados") também aceitam vínculo com outro produto cadastrado na separação
    public static final long CATEGORIA_VINCULAVEL_ID = 15L;

    public static boolean produtoDeCategoriaVinculavel(PedidoProduto item){
        return item.getProduto() != null
                && item.getProduto().getCategoria() != null
                && Long.valueOf(CATEGORIA_VINCULAVEL_ID).equals(item.getProduto().getCategoria().getId());
    }

    // item que aceita vínculo (ou troca de vínculo): avulso, vinculado a partir de avulso ou de categoria vinculável
    public boolean itemAceitaVinculo(PedidoProduto item){
        return itemAvulso(item) || itemVinculadoDeAvulso(item) || produtoDeCategoriaVinculavel(item);
    }

    // o vínculo do item avulso com um produto só é feito (ou trocado) durante a separação
    public void validarVinculoProduto(Pedido pedido, PedidoProduto item){
        if (pedido.getStatus() != StatusPedido.SEPARACAO) {
            throw new ItemInvalidoException("O item só pode ser vinculado a um produto com o pedido em separação. id: " + pedido.getId());
        }
        if (pedido.getTipo() == TipoItem.JOIA) {
            throw new ItemInvalidoException("Pedidos do tipo JOIA não têm item avulso. id: " + pedido.getId());
        }
        if (!itemAceitaVinculo(item)) {
            throw new ItemInvalidoException("O item " + item.getId() + " é um produto cadastrado do pedido e não pode ser trocado na separação");
        }
    }

    // o pedido só vira SEPARADO quando todos os itens ainda disponíveis (não totalmente
    // cancelados) já foram separados - e nenhum item avulso fica sem vínculo com produto
    public void validarTodosSeparados(Pedido pedido){
        pedido.getProdutos().stream()
                .filter(item -> {
                    int cancelada = item.getQuantidadeCancelada() != null ? item.getQuantidadeCancelada() : 0;
                    return item.getQuantidade() - cancelada > 0;
                })
                .filter(this::itemAvulso)
                .findFirst()
                .ifPresent(item -> validarItemVinculado(pedido, item));

        boolean faltaSeparar = pedido.getProdutos().stream().anyMatch(item -> {
            int cancelada = item.getQuantidadeCancelada() != null ? item.getQuantidadeCancelada() : 0;
            boolean disponivel = item.getQuantidade() - cancelada > 0;
            return disponivel && !item.isSeparado();
        });
        if (faltaSeparar) {
            throw new ItemInvalidoException("O pedido " + pedido.getId() + " ainda tem itens não separados");
        }
    }

    // cliente é opcional (pedido avulso, como a consignação); com cliente, a data de entrega é obrigatória
    public void validarClienteEEntrega(UUID clienteId, java.time.LocalDate dataEntrega){
        if (clienteId != null && dataEntrega == null) {
            throw new ItemInvalidoException("Data de entrega é obrigatória para pedido com cliente");
        }
    }

    // pedido sem cliente ou sem data de entrega não pode ser enviado: esses dados vêm da montagem ao concluí-la
    public void validarEnvio(Pedido pedido){
        if (pedido.getCliente() == null || pedido.getDataEntrega() == null) {
            throw new ItemInvalidoException("O pedido " + pedido.getId()
                    + " não pode ser enviado sem cliente e data de entrega. Vincule-o a uma montagem e conclua a montagem para preenchê-los");
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
