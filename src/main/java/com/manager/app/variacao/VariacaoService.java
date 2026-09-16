package com.manager.app.variacao;

import com.manager.app.produto.ProdutoVariacaoItemRepository;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VariacaoService {

    private final VariacaoRepository repository;
    private final ProdutoVariacaoItemRepository produtoVariacaoItemRepository;
    private final VariacaoValidation validation;

    public Page<Variacao> findAll(String busca, ModoBusca modoBusca, Pageable pageable){
        return repository.findAll(comBusca(busca, modoBusca), pageable);
    }

    private Specification<Variacao> comBusca(String busca, ModoBusca modoBusca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca, modoBusca);
    }

    public Variacao findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Variação não encontrada. id: " + id));
    }

    @Transactional
    public Variacao create(Variacao variacao){
        validation.nomeExistenteCreate(variacao.getNome());
        validation.itensNomesDuplicados(variacao.getItens());
        variacao.getItens().forEach(item -> item.setVariacao(variacao));
        return repository.save(variacao);
    }

    @Transactional
    public Variacao update(Variacao variacao){
        validation.nomeExistenteUpdate(variacao.getNome(), variacao.getId());
        validation.itensNomesDuplicados(variacao.getItens());

        Variacao variacaoUpdate = repository.findById(variacao.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Variação não encontrada. id: " + variacao.getId()));
        variacaoUpdate.setNome(variacao.getNome());
        variacaoUpdate.setAtivo(variacao.isAtivo());

        Map<Long, ItemVariacao> existentesPorId = new HashMap<>();
        for (ItemVariacao existente : variacaoUpdate.getItens()) {
            existentesPorId.put(existente.getId(), existente);
        }

        for (ItemVariacao item : variacao.getItens()) {
            if (item.getId() != null && existentesPorId.containsKey(item.getId())) {
                ItemVariacao existente = existentesPorId.remove(item.getId());
                existente.setNome(item.getNome());
                existente.setValor(item.getValor());
                existente.setAtivo(item.isAtivo());
            } else {
                variacaoUpdate.adicionarItem(new ItemVariacao(null, item.getNome(), item.getValor(), item.isAtivo(), null));
            }
        }

        List<ItemVariacao> removidos = List.copyOf(existentesPorId.values());
        for (ItemVariacao item : removidos) {
            if (produtoVariacaoItemRepository.existsByItemVariacaoId(item.getId())) {
                throw new ObjetoEmUsoException("Item '" + item.getNome() + "' está em uso e não pode ser removido");
            }
        }
        removidos.forEach(variacaoUpdate::removerItem);

        return variacaoUpdate;
    }

    @Transactional
    public Variacao deleteItem(Long variacaoId, Long itemId){
        Variacao variacao = this.findById(variacaoId);
        ItemVariacao item = variacao.getItens().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Item não encontrado nesta variação. id: " + itemId));

        if (produtoVariacaoItemRepository.existsByItemVariacaoId(itemId)) {
            throw new ObjetoEmUsoException("Item '" + item.getNome() + "' está em uso e não pode ser removido");
        }

        variacao.removerItem(item);
        return variacao;
    }

    @Transactional
    public Variacao activate(Long id){
        Variacao variacao = this.findById(id);
        variacao.setAtivo(true);
        return variacao;
    }

    @Transactional
    public void deactivate(Long id){
        Variacao variacao = this.findById(id);
        boolean emUso = variacao.getItens().stream()
                .anyMatch(item -> produtoVariacaoItemRepository.existsByItemVariacao_VariacaoIdAndProdutoVariacao_AtivoTrue(item.getId()));
        if (emUso) {
            throw new ObjetoEmUsoException("Variação está em uso e não pode ser desativada");
        }
        variacao.setAtivo(false);
    }
}
