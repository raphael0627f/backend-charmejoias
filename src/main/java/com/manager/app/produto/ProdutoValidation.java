package com.manager.app.produto;

import com.manager.app.shared.exception.ObjetoExistenteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProdutoValidation {

    private final ProdutoRepository repository;
    private final ProdutoVariacaoRepository produtoVariacaoRepository;

    public void skuExistenteCreate(String sku){
        if(repository.existsBySku(sku) || produtoVariacaoRepository.existsBySku(sku)){
            throw new ObjetoExistenteException("Produto existente com este SKU: " + sku);
        }
    }
    public void skuExistenteUpdate(String sku, UUID id){
        if(repository.existsBySkuAndIdNot(sku, id) || produtoVariacaoRepository.existsBySku(sku)){
            throw new ObjetoExistenteException("Produto existente com este SKU: " + sku);
        }
    }
}
