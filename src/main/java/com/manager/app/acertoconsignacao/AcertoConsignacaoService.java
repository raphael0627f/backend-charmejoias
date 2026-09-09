package com.manager.app.acertoconsignacao;

import com.manager.app.acertoconsignacao.dto.AcertoConsignacaoProdutoRequestDTO;
import com.manager.app.acertoconsignacao.dto.AcertoConsignacaoRequestDTO;
import com.manager.app.acertoconsignacao.dto.ProdutoVendidoDTO;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoProduto;
import com.manager.app.consignacao.ConsignacaoRepository;
import com.manager.app.consignacao.ConsignacaoValidation;
import com.manager.app.joia.Joia;
import com.manager.app.joia.JoiaService;
import com.manager.app.produto.Produto;
import com.manager.app.produto.ProdutoService;
import com.manager.app.produto.ProdutoVariacao;
import com.manager.app.shared.TipoItem;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.persistence.criteria.JoinType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AcertoConsignacaoService {

    private final AcertoConsignacaoRepository repository;
    private final ConsignacaoRepository consignacaoRepository;
    private final ProdutoService produtoService;
    private final JoiaService joiaService;
    private final ConsignacaoValidation consignacaoValidation;

    public Page<AcertoConsignacao> findAll(String busca, Long consignacaoId, Boolean confirmado, LocalDate dataInicio, LocalDate dataFim, Pageable pageable){
        Specification<AcertoConsignacao> spec = comBusca(busca);

        if (consignacaoId != null) {
            Specification<AcertoConsignacao> consignacaoSpec = (root, query, cb) ->
                    cb.equal(root.get("consignacao").get("id"), consignacaoId);
            spec = spec == null ? consignacaoSpec : spec.and(consignacaoSpec);
        }

        if (confirmado != null) {
            Specification<AcertoConsignacao> confirmadoSpec = (root, query, cb) ->
                    cb.equal(root.get("confirmado"), confirmado);
            spec = spec == null ? confirmadoSpec : spec.and(confirmadoSpec);
        }

        if (dataInicio != null) {
            Specification<AcertoConsignacao> dataInicioSpec = (root, query, cb) ->
                    SpecificationUtils.criadoAPartirDe(cb, root.get("createdAt"), dataInicio);
            spec = spec == null ? dataInicioSpec : spec.and(dataInicioSpec);
        }

        if (dataFim != null) {
            Specification<AcertoConsignacao> dataFimSpec = (root, query, cb) ->
                    SpecificationUtils.criadoAte(cb, root.get("createdAt"), dataFim);
            spec = spec == null ? dataFimSpec : spec.and(dataFimSpec);
        }

        return repository.findAll(spec, pageable);
    }

    private Specification<AcertoConsignacao> comBusca(String busca){
        if (busca == null || busca.isBlank()) return null;
        Long comoNumero = SpecificationUtils.comoLong(busca);
        return (root, query, cb) -> {
            var consignacao = root.join("consignacao", JoinType.LEFT);
            var cliente = consignacao.join("cliente", JoinType.LEFT);
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>(List.of(
                    SpecificationUtils.likeIgnoreCase(cb, root.get("observacoes"), busca),
                    SpecificationUtils.likeIgnoreCase(cb, cliente.get("codigo"), busca),
                    SpecificationUtils.likeIgnoreCase(cb, cliente.get("nome"), busca)
            ));
            if (comoNumero != null) {
                predicates.add(cb.equal(root.get("id"), comoNumero));
                predicates.add(cb.equal(consignacao.get("id"), comoNumero));
            }
            return cb.or(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    public AcertoConsignacao findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Acerto de Consignação não encontrado. id: " + id));
    }

    public List<ProdutoVendidoDTO> produtosVendidos(Long id){
        AcertoConsignacao acerto = this.findById(id);
        Consignacao consignacao = acerto.getConsignacao();

        List<ProdutoVendidoDTO> vendidos = new ArrayList<>();

        for (ConsignacaoProduto item : consignacao.getProdutos()) {
            AcertoConsignacaoProduto devolvido = acerto.devolvidoDe(item);
            int quantidadeDevolvida = devolvido != null ? devolvido.getQuantidade() : 0;
            int quantidadeVendida = item.getQuantidade() - quantidadeDevolvida;

            if (quantidadeVendida > 0) {
                BigDecimal valorTotal = item.getPreco().multiply(BigDecimal.valueOf(quantidadeVendida));
                vendidos.add(new ProdutoVendidoDTO(item.getProduto(), item.getProdutoVariacao(), item.getJoia(), quantidadeVendida, item.getPreco(), valorTotal));
            }
        }

        return vendidos;
    }

    private Consignacao resolverConsignacao(Long consignacaoId){
        if (consignacaoId == null) {
            throw new ItemInvalidoException("consignacaoId é obrigatório");
        }
        return consignacaoRepository.findById(consignacaoId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Consignação não encontrada. id: " + consignacaoId));
    }

    private void validarConsignacaoDisponivel(Long consignacaoId){
        if (repository.existsByConsignacaoId(consignacaoId)) {
            throw new ObjetoExistenteException("Esta Consignação já possui um Acerto de Consignação. consignacaoId: " + consignacaoId);
        }
    }

    private ProdutoVariacao resolverProdutoVariacao(Long produtoVariacaoId, UUID produtoId){
        if (produtoVariacaoId == null) return null;
        ProdutoVariacao variacao = produtoService.findVariacaoById(produtoVariacaoId);
        if (!variacao.getProduto().getId().equals(produtoId)) {
            throw new ItemInvalidoException("A combinação informada não pertence ao produto selecionado");
        }
        return variacao;
    }

    private ConsignacaoProduto buscarItemNaConsignacao(Consignacao consignacao, Produto produto, ProdutoVariacao produtoVariacao, Joia joia){
        for (ConsignacaoProduto item : consignacao.getProdutos()) {
            Long variacaoItemId = item.getProdutoVariacao() != null ? item.getProdutoVariacao().getId() : null;
            Long variacaoNovaId = produtoVariacao != null ? produtoVariacao.getId() : null;
            boolean mesmoProduto = produto != null && item.getProduto() != null
                    && item.getProduto().getId().equals(produto.getId())
                    && Objects.equals(variacaoItemId, variacaoNovaId);
            boolean mesmaJoia = joia != null && item.getJoia() != null && item.getJoia().getId().equals(joia.getId());
            if (mesmoProduto || mesmaJoia) {
                return item;
            }
        }
        throw new ItemInvalidoException("Produto informado não faz parte desta Consignação.");
    }

    private void preencherItem(AcertoConsignacaoProduto item, AcertoConsignacaoProdutoRequestDTO produtoRequest, Consignacao consignacao){
        consignacaoValidation.validarItem(consignacao.getTipo(), produtoRequest.getProdutoId(), produtoRequest.getJoiaId(), produtoRequest.getProdutoVariacaoId());

        Produto produto = null;
        ProdutoVariacao produtoVariacao = null;
        Joia joia = null;

        if (consignacao.getTipo() == TipoItem.JOIA) {
            joia = joiaService.findByIdAndAtivo(produtoRequest.getJoiaId());
        } else {
            produto = produtoService.findByIdAndAtivo(produtoRequest.getProdutoId());
            produtoVariacao = resolverProdutoVariacao(produtoRequest.getProdutoVariacaoId(), produto.getId());
        }

        ConsignacaoProduto itemConsignacao = buscarItemNaConsignacao(consignacao, produto, produtoVariacao, joia);

        if (produtoRequest.getQuantidade() == null || produtoRequest.getQuantidade() <= 0) {
            throw new ItemInvalidoException("Quantidade devolvida deve ser maior que zero.");
        }
        if (produtoRequest.getQuantidade() > itemConsignacao.getQuantidade()) {
            throw new ItemInvalidoException("Quantidade devolvida maior que a quantidade da Consignação.");
        }

        item.setProduto(produto);
        item.setProdutoVariacao(produtoVariacao);
        item.setJoia(joia);
        item.setQuantidade(produtoRequest.getQuantidade());
    }

    @Transactional
    public AcertoConsignacao create(AcertoConsignacaoRequestDTO request){
        Consignacao consignacao = resolverConsignacao(request.getConsignacaoId());

        if (!Boolean.TRUE.equals(consignacao.getConfirmada())) {
            throw new ItemInvalidoException("A Consignação precisa estar confirmada para gerar um Acerto de Consignação. consignacaoId: " + consignacao.getId());
        }

        validarConsignacaoDisponivel(consignacao.getId());

        AcertoConsignacao acerto = new AcertoConsignacao();
        acerto.setConsignacao(consignacao);
        acerto.setDesconto(request.getDesconto());
        acerto.setObservacoes(request.getObservacoes());

        for (AcertoConsignacaoProdutoRequestDTO produtoRequest : request.getProdutos()) {
            AcertoConsignacaoProduto item = new AcertoConsignacaoProduto();
            preencherItem(item, produtoRequest, consignacao);
            acerto.adicionarProduto(item);
        }

        return repository.save(acerto);
    }

    @Transactional
    public AcertoConsignacao update(AcertoConsignacaoRequestDTO request){
        AcertoConsignacao acertoUpdate = this.findById(request.getId());

        if (Boolean.TRUE.equals(acertoUpdate.getConfirmado())) {
            throw new ObjetoEmUsoException("Acerto de Consignação confirmado não pode ser editado. id: " + acertoUpdate.getId());
        }

        Consignacao consignacao = acertoUpdate.getConsignacao();

        acertoUpdate.setDesconto(request.getDesconto());
        acertoUpdate.setObservacoes(request.getObservacoes());

        List<AcertoConsignacaoProduto> produtosAtuais = new ArrayList<>(acertoUpdate.getProdutos());

        for (AcertoConsignacaoProdutoRequestDTO produtoRequest : request.getProdutos()) {
            AcertoConsignacaoProduto produtoExistente = null;

            for (AcertoConsignacaoProduto produto : acertoUpdate.getProdutos()) {
                Long variacaoAtualId = produto.getProdutoVariacao() != null ? produto.getProdutoVariacao().getId() : null;
                boolean mesmaVariacao = Objects.equals(variacaoAtualId, produtoRequest.getProdutoVariacaoId());
                boolean mesmoProduto = produto.getProduto() != null && produto.getProduto().getId().equals(produtoRequest.getProdutoId()) && mesmaVariacao;
                boolean mesmaJoia = produto.getJoia() != null && produto.getJoia().getId().equals(produtoRequest.getJoiaId());
                if (mesmoProduto || mesmaJoia) {
                    produtoExistente = produto;
                    break;
                }
            }

            if (produtoExistente != null) {
                preencherItem(produtoExistente, produtoRequest, consignacao);
                produtosAtuais.remove(produtoExistente);
            } else {
                AcertoConsignacaoProduto novoProduto = new AcertoConsignacaoProduto();
                preencherItem(novoProduto, produtoRequest, consignacao);
                acertoUpdate.adicionarProduto(novoProduto);
            }
        }

        for (AcertoConsignacaoProduto removerProduto : produtosAtuais) {
            acertoUpdate.removerProduto(removerProduto);
        }

        return acertoUpdate;
    }

    @Transactional
    public AcertoConsignacao confirmar(Long id){
        AcertoConsignacao acerto = this.findById(id);

        if (Boolean.TRUE.equals(acerto.getConfirmado())) {
            throw new ItemInvalidoException("Acerto de Consignação já está confirmado. id: " + id);
        }

        acerto.setConfirmado(true);
        return acerto;
    }
}
