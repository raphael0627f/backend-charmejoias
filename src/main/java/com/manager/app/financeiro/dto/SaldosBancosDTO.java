package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.TipoBanco;

import java.math.BigDecimal;
import java.util.List;

public record SaldosBancosDTO(List<SaldoBancoDTO> bancos, BigDecimal total) {

    public record SaldoBancoDTO(Long bancoId, String nome, TipoBanco tipo, boolean ativo, BigDecimal saldoAtual) {
    }
}
