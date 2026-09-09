package com.manager.app.shared.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

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

    public static Long comoLong(String termo){
        try {
            return Long.valueOf(termo.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
