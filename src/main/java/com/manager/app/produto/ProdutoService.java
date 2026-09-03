package com.manager.app.produto;

import com.manager.app.produto.dto.ProdutoRelacoesDTO;
import com.manager.app.grupo.Grupo;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.grupo.GrupoValidation;
import com.manager.app.shared.specification.SpecificationUtils;
import com.manager.app.grupo.GrupoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import com.manager.app.categoria.CategoriaService;
import com.manager.app.fornecedor.FornecedorService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository repository;
    private final GrupoRepository grupoRepository;
    private final FornecedorService fornecedorService;
    private final CategoriaService categoriaService;
    private final ProdutoValidation validation;
    private final GrupoValidation grupoValidation;

    private static final Pattern CODIGO_NUMERICO = Pattern.compile("^\\d{4}$");

    public Page<Produto> findAll(String busca, Boolean ativo, Long categoriaId, Long fornecedorId, Pageable pageable){
        Specification<Produto> spec = comBusca(busca);

        if (ativo != null) {
            Specification<Produto> ativoSpec = (root, query, cb) -> cb.equal(root.get("ativo"), ativo);
            spec = spec == null ? ativoSpec : spec.and(ativoSpec);
        }

        if (categoriaId != null) {
            Specification<Produto> categoriaSpec = (root, query, cb) -> cb.equal(root.get("categoria").get("id"), categoriaId);
            spec = spec == null ? categoriaSpec : spec.and(categoriaSpec);
        }

        if (fornecedorId != null) {
            Specification<Produto> fornecedorSpec = (root, query, cb) -> cb.equal(root.get("fornecedor").get("id"), fornecedorId);
            spec = spec == null ? fornecedorSpec : spec.and(fornecedorSpec);
        }

        return repository.findAll(spec, pageable);
    }

    private Specification<Produto> comBusca(String busca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> cb.or(
                SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("codigo"), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("referencia"), busca)
        );
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

    public String proximoCodigo(){
        Set<Integer> usados = new HashSet<>();
        for (String codigo : repository.findAllCodigos()) {
            if (codigo == null || codigo.length() < 4) continue;
            String prefixo = codigo.substring(0, 4);
            if (CODIGO_NUMERICO.matcher(prefixo).matches()) {
                usados.add(Integer.parseInt(prefixo));
            }
        }

        int proximo = 1;
        while (usados.contains(proximo)) {
            proximo++;
        }
        return String.format("%04d", proximo);
    }

    @Transactional
    public Produto create(Produto produto, ProdutoRelacoesDTO relacoes){
        validation.codigoExistenteCreate(produto.getCodigo());

        produto.setCategoria(categoriaService.findById(relacoes.getCategoriaId()));
        produto.setFornecedor(relacoes.getFornecedorId() != null
                ? fornecedorService.findById(relacoes.getFornecedorId())
                : null);

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
        produtoUpdate.setNcm(produto.getNcm());
        produtoUpdate.setOrigemProduto(produto.getOrigemProduto());
        produtoUpdate.setUnidadeComercial(produto.getUnidadeComercial());
        produtoUpdate.setCodigoSituacaoTributariaIcms(produto.getCodigoSituacaoTributariaIcms());
        produtoUpdate.setCodigoSituacaoTributariaPis(produto.getCodigoSituacaoTributariaPis());
        produtoUpdate.setCodigoSituacaoTributariaCofins(produto.getCodigoSituacaoTributariaCofins());
        produtoUpdate.setCategoria(categoriaService.findById(relacoes.getCategoriaId()));
        produtoUpdate.setFornecedor(relacoes.getFornecedorId() != null
                ? fornecedorService.findById(relacoes.getFornecedorId())
                : null);

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
