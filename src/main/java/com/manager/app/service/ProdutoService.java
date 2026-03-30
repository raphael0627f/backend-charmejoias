package com.manager.app.service;

import com.manager.app.dto.ProdutoRelacoesDTO;
import com.manager.app.entity.Categoria;
import com.manager.app.entity.Cliente;
import com.manager.app.entity.Grupo;
import com.manager.app.entity.Produto;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.infra.validation.GrupoValidation;
import com.manager.app.infra.validation.ProdutoValidation;
import com.manager.app.repository.FornecedorRepository;
import com.manager.app.repository.GrupoRepository;
import com.manager.app.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository repository;
    private final GrupoRepository grupoRepository;
    private final FornecedorService fornecedorService;
    private final CategoriaService categoriaService;
    private final ProdutoValidation validation;
    private final GrupoValidation grupoValidation;

    public List<Produto> findAll(){
        return repository.findAll();
    }

    public Produto findById(UUID id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Produto não encontrado. id: " + id));
    }

    public Produto findByIdAndAtivo(UUID id){
        return repository.findByIdAndAtivoTrue(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Produto não encontrado ou inativo. id: " + id));
    }

    public Produto findByCodigo(String codigo){
        return repository.findByCodigoAndAtivoTrue(codigo).orElseThrow(() -> new ObjetoNaoEncontradoException("Produto não encontrado. codigo: " + codigo));
    }

    @Transactional
    public Produto create(Produto produto, ProdutoRelacoesDTO relacoes){
        validation.codigoExistenteCreate(produto.getCodigo());

        produto.setCategoria(categoriaService.findById(relacoes.getCategoriaId()));
        produto.setFornecedor(fornecedorService.findById(relacoes.getFornecedorId()));

        if(!grupoValidation.gruposEmpty(relacoes.getGruposIds())){
            List<Grupo> grupos = grupoRepository.findAllById(relacoes.getGruposIds());
            if (grupos.size() != relacoes.getGruposIds().size()) {
                throw new ObjetoNaoEncontradoException("Um ou mais grupos não encontrados");
            }
            produto.setGrupos(grupos);
        }
        return repository.save(produto);
    }

    @Transactional
    public Produto update(Produto produto, ProdutoRelacoesDTO relacoes){
        validation.codigoExistenteUpdate(produto.getCodigo(), produto.getId());
        Produto produtoUpdate = repository.findById(produto.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Produto não encontrado. id: " + produto.getId()));

        produtoUpdate.setCodigo(produto.getCodigo());
        produtoUpdate.setReferencia(produto.getReferencia());
        produtoUpdate.setNome(produto.getNome());
        produtoUpdate.setPrecoCusto(produto.getPrecoCusto());
        produtoUpdate.setPrecoVenda(produto.getPrecoVenda());
        produtoUpdate.setAtivo(produto.isAtivo());
        produtoUpdate.setObservacoes(produto.getObservacoes());
        produtoUpdate.setCategoria(categoriaService.findById(relacoes.getCategoriaId()));
        produtoUpdate.setFornecedor(fornecedorService.findById(relacoes.getFornecedorId()));

        if(!grupoValidation.gruposEmpty(relacoes.getGruposIds())){
            List<Grupo> grupos = grupoRepository.findAllById(relacoes.getGruposIds());
            if (grupos.size() != relacoes.getGruposIds().size()) {
                throw new ObjetoNaoEncontradoException("Um ou mais grupos não encontrados");
            }
            produtoUpdate.setGrupos(grupos);
        }
        return produtoUpdate;
    }

    @Transactional
    public Produto activate(UUID id){
        Produto produto = this.findById(id);
        produto.setAtivo(true);
        return produto;
    }

    @Transactional
    public void deactivate(UUID id){
        Produto produto = this.findById(id);
        produto.setAtivo(false);
    }
}
