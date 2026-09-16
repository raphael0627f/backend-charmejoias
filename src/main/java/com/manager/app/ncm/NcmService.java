package com.manager.app.ncm;

import com.manager.app.produto.ProdutoRepository;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NcmService {

    private final NcmRepository repository;
    private final ProdutoRepository produtoRepository;

    public Page<Ncm> findAll(String busca, ModoBusca modoBusca, Pageable pageable){
        return repository.findAll(comBusca(busca, modoBusca), pageable);
    }

    private Specification<Ncm> comBusca(String busca, ModoBusca modoBusca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> cb.or(
                SpecificationUtils.likeIgnoreCase(cb, root.get("codigo"), busca, modoBusca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("descricao"), busca, modoBusca)
        );
    }

    public Ncm findById(String codigo){
        return repository.findById(codigo).orElseThrow(() -> new ObjetoNaoEncontradoException("NCM não encontrado. codigo: " + codigo));
    }

    @Transactional
    public Ncm create(Ncm ncm){
        if (repository.existsById(ncm.getCodigo())) {
            throw new ObjetoExistenteException("NCM já cadastrado. codigo: " + ncm.getCodigo());
        }
        return repository.save(ncm);
    }

    @Transactional
    public Ncm update(Ncm ncm){
        Ncm ncmUpdate = this.findById(ncm.getCodigo());
        ncmUpdate.setDescricao(ncm.getDescricao());
        ncmUpdate.setAtivo(ncm.isAtivo());
        ncmUpdate.setAliquotaAproximadaTributos(ncm.getAliquotaAproximadaTributos());
        return ncmUpdate;
    }

    @Transactional
    public Ncm activate(String codigo){
        Ncm ncm = this.findById(codigo);
        ncm.setAtivo(true);
        return ncm;
    }

    @Transactional
    public void deactivate(String codigo){
        Ncm ncm = this.findById(codigo);
        if (produtoRepository.existsByNcm(ncm)) {
            throw new ObjetoEmUsoException("NCM está em uso e não pode ser desativado");
        }
        ncm.setAtivo(false);
    }
}
