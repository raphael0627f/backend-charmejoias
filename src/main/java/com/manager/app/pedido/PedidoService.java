package com.manager.app.pedido;

import com.manager.app.pedido.dto.PedidoProdutoRequestDTO;
import com.manager.app.pedido.dto.PedidoRequestDTO;
import com.manager.app.pedido.dto.PedidoStatusRequestDTO;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.TipoItem;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.manager.app.cliente.ClienteService;
import com.manager.app.joia.JoiaService;
import com.manager.app.produto.ProdutoService;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository repository;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final JoiaService joiaService;
    private final PedidoValidation validation;

    public List<Pedido> findAll(){
        return repository.findAll();
    }

    public Pedido findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Pedido não encontrada. id: " + id));
    }

    private void preencherItem(PedidoProduto produto, PedidoProdutoRequestDTO produtoRequest, TipoItem tipo){
        validation.validarItem(tipo, produtoRequest.getProdutoId(), produtoRequest.getJoiaId());

        if (tipo == TipoItem.JOIA){
            produto.setDescricao(null);
            produto.setJoia(joiaService.findByIdAndAtivo(produtoRequest.getJoiaId()));
            produto.setProduto(null);
        } else if (!validation.produtoNovo(produtoRequest.getProdutoId())){
            produto.setDescricao(null);
            produto.setProduto(produtoService.findByIdAndAtivo(produtoRequest.getProdutoId()));
            produto.setJoia(null);
        } else {
            produto.setDescricao(produtoRequest.getDescricao());
            produto.setProduto(null);
            produto.setJoia(null);
        }
    }

    @Transactional
    public Pedido create(PedidoRequestDTO request){
        Pedido pedido = new Pedido();

        pedido.setObservacao(request.getObservacao());
        pedido.setDataEntrega(request.getDataEntrega());
        pedido.setStatus(request.getStatus());
        pedido.setTipo(request.getTipo());
        pedido.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));

        for (PedidoProdutoRequestDTO produtoRequest : request.getProdutosRequest()){
            PedidoProduto produto = new PedidoProduto();

            preencherItem(produto, produtoRequest, request.getTipo());

            produto.setVariacao(produtoRequest.getVariacao());
            produto.setQuantidade(produtoRequest.getQuantidade());
            produto.setPreco(produtoRequest.getPreco());
            pedido.adicionarProduto(produto);
        }

        return repository.save(pedido);
    }

    @Transactional
    public Pedido update(PedidoRequestDTO request){
        Pedido pedidoUpdate = this.findById(request.getId());

        pedidoUpdate.setObservacao(request.getObservacao());
        pedidoUpdate.setDataEntrega(request.getDataEntrega());
        pedidoUpdate.setStatus(request.getStatus());
        pedidoUpdate.setTipo(request.getTipo());
        pedidoUpdate.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));

        List<PedidoProduto> produtosAtuais = new ArrayList<>(pedidoUpdate.getProdutos());

        for (PedidoProdutoRequestDTO produtoRequest : request.getProdutosRequest()){
            PedidoProduto produtoExistente = null;

            for (PedidoProduto produto : pedidoUpdate.getProdutos()){
                boolean mesmoProduto = produto.getProduto() != null && produto.getProduto().getId().equals(produtoRequest.getProdutoId());
                boolean mesmaJoia = produto.getJoia() != null && produto.getJoia().getId().equals(produtoRequest.getJoiaId());
                if (mesmoProduto || mesmaJoia) {
                    produtoExistente = produto;
                    break;
                }
            }

            if(produtoExistente != null){
                produtoExistente.setDescricao(null);

                if (!produtoExistente.getVariacao().equals(produtoRequest.getVariacao())) {
                    produtoExistente.setVariacao(produtoRequest.getVariacao());
                }

                if (!produtoExistente.getQuantidade().equals(produtoRequest.getQuantidade())) {
                    produtoExistente.setQuantidade(produtoRequest.getQuantidade());
                }

                if (produtoExistente.getPreco().compareTo(produtoRequest.getPreco()) != 0) {
                    produtoExistente.setPreco(produtoRequest.getPreco());
                }

                produtosAtuais.remove(produtoExistente);
            } else {
                PedidoProduto novoProduto = new PedidoProduto();

                preencherItem(novoProduto, produtoRequest, request.getTipo());

                novoProduto.setVariacao(produtoRequest.getVariacao());
                novoProduto.setQuantidade(produtoRequest.getQuantidade());
                novoProduto.setPreco(produtoRequest.getPreco());
                pedidoUpdate.adicionarProduto(novoProduto);
            }
        }

        for (PedidoProduto removerProduto : produtosAtuais) {
            pedidoUpdate.removerProduto(removerProduto);
        }
        return pedidoUpdate;
    }

    @Transactional
    public Pedido updateStatus(PedidoStatusRequestDTO statusRequest){
        Pedido pedido = this.findById(statusRequest.getId());
        pedido.setStatus(statusRequest.getStatus());
        return pedido;
    }
}
