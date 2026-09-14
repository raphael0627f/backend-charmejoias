package com.manager.app.shared.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SpecificationUtils {

    private SpecificationUtils(){}

    public static Predicate likeIgnoreCase(CriteriaBuilder cb, Expression<String> path, String termo){
        return cb.like(cb.lower(path), "%" + termo.toLowerCase() + "%");
    }

    public static Predicate criadoAPartirDe(CriteriaBuilder cb, Expression<LocalDateTime> path, LocalDate data){
        return cb.greaterThanOrEqualTo(path, data.atStartOfDay());
    }

    public static Predicate criadoAte(CriteriaBuilder cb, Expression<LocalDateTime> path, LocalDate data){
        return cb.lessThan(path, data.plusDays(1).atStartOfDay());
    }

    // pra filtros de range sobre uma coluna LocalDate (dataEntrega, dataPreAcertoConsignacao,
    // dataAcertoConsignacao, etc.) - diferente de criadoAPartirDe/criadoAte, que lidam com LocalDateTime
    public static Predicate apartirDe(CriteriaBuilder cb, Expression<LocalDate> path, LocalDate data){
        return cb.greaterThanOrEqualTo(path, data);
    }

    public static Predicate ate(CriteriaBuilder cb, Expression<LocalDate> path, LocalDate data){
        return cb.lessThanOrEqualTo(path, data);
    }

    // combina specs opcionais sem precisar repetir "spec == null ? novo : spec.and(novo)" em todo filtro
    public static <T> Specification<T> and(Specification<T> atual, Specification<T> novo){
        return atual == null ? novo : atual.and(novo);
    }

    public static Long comoLong(String termo){
        try {
            return Long.valueOf(termo.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
