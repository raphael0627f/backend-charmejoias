package com.manager.app.financeiro;

import com.manager.app.cliente.Cliente;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// monta o filtro da lista (usado também nos totais). VENCIDO = aberto/parcial com vencimento anterior a hoje.
public final class ContaReceberEspecificacao {

    private ContaReceberEspecificacao() {}

    public static Specification<ContaReceber> de(ContaReceberFiltro f, LocalDate vencimentoDe, LocalDate vencimentoAte, LocalDate hoje) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (vencimentoDe != null) p.add(cb.greaterThanOrEqualTo(root.get("vencimento"), vencimentoDe));
            if (vencimentoAte != null) p.add(cb.lessThanOrEqualTo(root.get("vencimento"), vencimentoAte));
            if (f.emissaoDe() != null) p.add(cb.greaterThanOrEqualTo(root.get("emissao"), f.emissaoDe()));
            if (f.emissaoAte() != null) p.add(cb.lessThanOrEqualTo(root.get("emissao"), f.emissaoAte()));
            if (f.valorMin() != null) p.add(cb.greaterThanOrEqualTo(root.get("valor"), f.valorMin()));
            if (f.valorMax() != null) p.add(cb.lessThanOrEqualTo(root.get("valor"), f.valorMax()));
            if (f.clienteId() != null) p.add(cb.equal(root.get("cliente").get("id"), f.clienteId()));
            if (f.consignacaoId() != null) p.add(cb.equal(root.get("consignacao").get("id"), f.consignacaoId()));
            if (f.origem() != null && !f.origem().isBlank()) {
                p.add(f.origem().trim().equalsIgnoreCase("PRE_ACERTO") ? cb.isNotNull(root.get("preAcerto")) : cb.isNotNull(root.get("acerto")));
            }
            if (f.tipoPagador() != null && !f.tipoPagador().isBlank()) {
                p.add(f.tipoPagador().trim().equalsIgnoreCase("AVULSO") ? cb.isNull(root.get("cliente")) : cb.isNotNull(root.get("cliente")));
            }
            if (f.centroCustoId() != null) p.add(cb.equal(root.get("centroCusto").get("id"), f.centroCustoId()));
            if (f.formaPrevistaId() != null) p.add(cb.equal(root.get("formaPrevista").get("id"), f.formaPrevistaId()));
            if (f.categoriaId() != null) {
                // inclui as subcategorias quando a categoria informada é um grupo
                Join<ContaReceber, CategoriaReceita> categoria = root.join("categoria", JoinType.INNER);
                Join<CategoriaReceita, CategoriaReceita> pai = categoria.join("pai", JoinType.LEFT);
                p.add(cb.or(cb.equal(categoria.get("id"), f.categoriaId()), cb.equal(pai.get("id"), f.categoriaId())));
            }
            if (f.temBusca()) p.add(busca(f.busca().trim(), root, cb));
            if (f.temSituacao()) {
                List<Predicate> ou = new ArrayList<>();
                for (SituacaoExibicaoContaReceber s : f.situacao()) {
                    if (s == SituacaoExibicaoContaReceber.VENCIDO) {
                        ou.add(cb.and(root.get("situacao").in(SituacaoContaReceber.ABERTO, SituacaoContaReceber.RECEBIDO_PARCIAL),
                                cb.lessThan(root.get("vencimento"), hoje)));
                    } else {
                        ou.add(cb.equal(root.get("situacao"), SituacaoContaReceber.valueOf(s.name())));
                    }
                }
                p.add(cb.or(ou.toArray(new Predicate[0])));
            }
            if (f.temFiltroDeRecebimento()) {
                // recebimentos ativos (não estornados) do título
                Subquery<Long> sub = query.subquery(Long.class);
                Root<ContaReceberRecebimento> recebimento = sub.from(ContaReceberRecebimento.class);
                List<Predicate> b = new ArrayList<>();
                b.add(cb.equal(recebimento.get("contaReceber"), root));
                b.add(cb.isFalse(recebimento.get("estornada")));
                // filtros de recebimento (período/banco) tratam de caixa: aplicação de crédito do cliente nunca entra
                b.add(cb.equal(recebimento.get("origem"), ContaReceberRecebimento.Origem.BANCO));
                if (f.recebimentoDe() != null) b.add(cb.greaterThanOrEqualTo(recebimento.get("dataRecebimento"), f.recebimentoDe()));
                if (f.recebimentoAte() != null) b.add(cb.lessThanOrEqualTo(recebimento.get("dataRecebimento"), f.recebimentoAte()));
                if (f.bancoId() != null) b.add(cb.equal(recebimento.get("banco").get("id"), f.bancoId()));
                sub.select(recebimento.get("id")).where(b.toArray(new Predicate[0]));
                p.add(cb.exists(sub));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    // ordena pelo nome de exibição (cliente ou pagador avulso) sem diferenciar caixa; desempate pelo número.
    // Só aplica a ordem na consulta de dados (a de contagem da página não pode ter ORDER BY).
    public static Specification<ContaReceber> ordenadaPorPagador(Specification<ContaReceber> base, boolean desc) {
        return (root, query, cb) -> {
            Class<?> tipo = query.getResultType();
            if (tipo != Long.class && tipo != long.class) {
                Join<ContaReceber, Cliente> cliente = root.join("cliente", JoinType.LEFT);
                var nome = cb.lower(cb.coalesce(cliente.<String>get("nome"), root.<String>get("pagadorNome")));
                query.orderBy(desc ? cb.desc(nome) : cb.asc(nome), cb.asc(root.get("numero")));
            }
            return base.toPredicate(root, query, cb);
        };
    }

    private static Predicate busca(String termo, Root<ContaReceber> root, CriteriaBuilder cb) {
        Join<ContaReceber, Cliente> cliente = root.join("cliente", JoinType.LEFT);
        List<Predicate> ou = new ArrayList<>();
        ou.add(SpecificationUtils.likeIgnoreCase(cb, root.get("descricao"), termo, ModoBusca.MEIO));
        ou.add(SpecificationUtils.likeIgnoreCase(cb, root.get("documento"), termo, ModoBusca.MEIO));
        ou.add(SpecificationUtils.likeIgnoreCase(cb, cliente.get("codigo"), termo, ModoBusca.MEIO));
        ou.add(SpecificationUtils.likeIgnoreCase(cb, cliente.get("nome"), termo, ModoBusca.MEIO));
        ou.add(SpecificationUtils.likeIgnoreCase(cb, cliente.get("cpf"), termo, ModoBusca.MEIO));
        ou.add(SpecificationUtils.likeIgnoreCase(cb, cliente.get("documento"), termo, ModoBusca.MEIO));
        ou.add(SpecificationUtils.likeIgnoreCase(cb, root.get("pagadorNome"), termo, ModoBusca.MEIO));
        ou.add(SpecificationUtils.likeIgnoreCase(cb, root.get("pagadorDocumento"), termo, ModoBusca.MEIO));
        String digitos = BuscaDocumento.digitos(termo);
        if (digitos != null) {
            ou.add(SpecificationUtils.likeIgnoreCase(cb, root.get("pagadorDocumento"), digitos, ModoBusca.MEIO));
            ou.add(SpecificationUtils.likeIgnoreCase(cb, cliente.get("cpf"), digitos, ModoBusca.MEIO));
            ou.add(SpecificationUtils.likeIgnoreCase(cb, cliente.get("documento"), digitos, ModoBusca.MEIO));
        }
        String numero = termo.startsWith("#") ? termo.substring(1) : termo;
        if (!numero.isEmpty() && numero.length() <= 15 && numero.chars().allMatch(Character::isDigit)) {
            ou.add(cb.equal(root.get("numero"), Long.parseLong(numero)));
        }
        return cb.or(ou.toArray(new Predicate[0]));
    }
}
