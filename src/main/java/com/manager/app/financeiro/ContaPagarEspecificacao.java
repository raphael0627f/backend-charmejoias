package com.manager.app.financeiro;

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
public final class ContaPagarEspecificacao {

    private ContaPagarEspecificacao() {}

    public static Specification<ContaPagar> de(ContaPagarFiltro f, LocalDate vencimentoDe, LocalDate vencimentoAte, LocalDate hoje) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (vencimentoDe != null) p.add(cb.greaterThanOrEqualTo(root.get("vencimento"), vencimentoDe));
            if (vencimentoAte != null) p.add(cb.lessThanOrEqualTo(root.get("vencimento"), vencimentoAte));
            if (f.emissaoDe() != null) p.add(cb.greaterThanOrEqualTo(root.get("emissao"), f.emissaoDe()));
            if (f.emissaoAte() != null) p.add(cb.lessThanOrEqualTo(root.get("emissao"), f.emissaoAte()));
            if (f.valorMin() != null) p.add(cb.greaterThanOrEqualTo(root.get("valor"), f.valorMin()));
            if (f.valorMax() != null) p.add(cb.lessThanOrEqualTo(root.get("valor"), f.valorMax()));
            if (f.centroCustoId() != null) p.add(cb.equal(root.get("centroCusto").get("id"), f.centroCustoId()));
            if (f.formaPrevistaId() != null) p.add(cb.equal(root.get("formaPrevista").get("id"), f.formaPrevistaId()));
            if (f.categoriaId() != null) {
                // inclui as subcategorias quando a categoria informada é um grupo
                Join<ContaPagar, CategoriaDespesa> categoria = root.join("categoria", JoinType.INNER);
                Join<CategoriaDespesa, CategoriaDespesa> pai = categoria.join("pai", JoinType.LEFT);
                p.add(cb.or(cb.equal(categoria.get("id"), f.categoriaId()), cb.equal(pai.get("id"), f.categoriaId())));
            }
            if (f.temBusca()) p.add(busca(f.busca().trim(), root, cb));
            if (f.temSituacao()) {
                List<Predicate> ou = new ArrayList<>();
                for (SituacaoExibicaoContaPagar s : f.situacao()) {
                    if (s == SituacaoExibicaoContaPagar.VENCIDO) {
                        ou.add(cb.and(root.get("situacao").in(SituacaoContaPagar.ABERTO, SituacaoContaPagar.PAGO_PARCIAL),
                                cb.lessThan(root.get("vencimento"), hoje)));
                    } else {
                        ou.add(cb.equal(root.get("situacao"), SituacaoContaPagar.valueOf(s.name())));
                    }
                }
                p.add(cb.or(ou.toArray(new Predicate[0])));
            }
            if (f.pagamentoDe() != null || f.pagamentoAte() != null || f.bancoId() != null) {
                // pagamentos ativos (não estornados) do título
                Subquery<Long> sub = query.subquery(Long.class);
                Root<ContaPagarPagamento> pagamento = sub.from(ContaPagarPagamento.class);
                List<Predicate> b = new ArrayList<>();
                b.add(cb.equal(pagamento.get("contaPagar"), root));
                b.add(cb.isFalse(pagamento.get("estornada")));
                if (f.pagamentoDe() != null) b.add(cb.greaterThanOrEqualTo(pagamento.get("dataPagamento"), f.pagamentoDe()));
                if (f.pagamentoAte() != null) b.add(cb.lessThanOrEqualTo(pagamento.get("dataPagamento"), f.pagamentoAte()));
                if (f.bancoId() != null) b.add(cb.equal(pagamento.get("banco").get("id"), f.bancoId()));
                sub.select(pagamento.get("id")).where(b.toArray(new Predicate[0]));
                p.add(cb.exists(sub));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private static Predicate busca(String termo, Root<ContaPagar> root, CriteriaBuilder cb) {
        List<Predicate> ou = new ArrayList<>();
        ou.add(SpecificationUtils.likeIgnoreCase(cb, root.get("descricao"), termo, ModoBusca.MEIO));
        ou.add(SpecificationUtils.likeIgnoreCase(cb, root.get("documento"), termo, ModoBusca.MEIO));
        ou.add(SpecificationUtils.likeIgnoreCase(cb, root.get("favorecido"), termo, ModoBusca.MEIO));
        String numero = termo.startsWith("#") ? termo.substring(1) : termo;
        if (!numero.isEmpty() && numero.length() <= 15 && numero.chars().allMatch(Character::isDigit)) {
            ou.add(cb.equal(root.get("numero"), Long.parseLong(numero)));
        }
        return cb.or(ou.toArray(new Predicate[0]));
    }
}
