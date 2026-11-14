package com.manager.app.financeiro;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "forma_pagamento")
@EqualsAndHashCode(of = "id", callSuper = false)
@Getter
@Setter
@NoArgsConstructor
public class FormaPagamento extends AuditoriaFinanceira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private boolean ativo = true;
}
