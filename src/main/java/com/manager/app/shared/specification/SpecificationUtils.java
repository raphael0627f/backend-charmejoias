package com.manager.app.shared.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

public final class SpecificationUtils {

    private SpecificationUtils(){}

    public static Predicate likeIgnoreCase(CriteriaBuilder cb, Expression<String> path, String termo){
        return cb.like(cb.lower(path), "%" + termo.toLowerCase() + "%");
    }
}
