package com.manager.app.service;

import com.manager.app.dto.pedido.PedidoProdutoRequestDTO;
import com.manager.app.dto.pedido.PedidoRequestDTO;
import com.manager.app.dto.pedido.PedidoStatusRequestDTO;
import com.manager.app.entity.Pedido;
import com.manager.app.entity.PedidoProduto;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.infra.restEnum.StatusPedido;
import com.manager.app.infra.validation.PedidoValidation;
import com.manager.app.repository.PedidoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository repository;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final PedidoValidation validation;

    public List<Pedido> findAll(){
        return repository.findAll();
    }

    public Pedido findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Pedido não encontrada. id: " + id));
    }

    @Transactional
    public Pedido create(PedidoRequestDTO request){
        Pedido pedido = new Pedido();

        pedido.setObservacao(request.getObservacao());
        pedido.setDataEntrega(request.getDataEntrega());
        pedido.setStatus(request.getStatus());
        pedido.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));

        for (PedidoProdutoRequestDTO produtoRequest : request.getProdutosRequest()){
            PedidoProduto produto = new PedidoProduto();

            if(!validation.produtoNovo(produtoRequest.getProdutoId())){
                produto.setDescricao(null);
                produto.setProduto(produtoService.findByIdAndAtivo(produtoRequest.getProdutoId()));
            } else {
                produto.setDescricao(produtoRequest.getDescricao());
                produto.setProduto(null);
            }

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
        pedidoUpdate.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));

        List<PedidoProduto> produtosAtuais = new ArrayList<>(pedidoUpdate.getProdutos());

        for (PedidoProdutoRequestDTO produtoRequest : request.getProdutosRequest()){
            PedidoProduto produtoExistente = null;

            for (PedidoProduto produto : pedidoUpdate.getProdutos()){
                if (produto.getProduto() != null && produto.getProduto().getId().equals(produtoRequest.getProdutoId())) {
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

                if(!validation.produtoNovo(produtoRequest.getProdutoId())){
                    novoProduto.setDescricao(null);
                    novoProduto.setProduto(produtoService.findByIdAndAtivo(produtoRequest.getProdutoId()));
                } else {
                    novoProduto.setDescricao(produtoRequest.getDescricao());
                    novoProduto.setProduto(null);
                }

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
