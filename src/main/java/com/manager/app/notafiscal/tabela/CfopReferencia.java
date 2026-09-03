package com.manager.app.notafiscal.tabela;

import java.util.Arrays;
import java.util.List;

public enum CfopReferencia {

    VENDA_ESTADUAL("5102", "Venda de mercadoria adquirida ou recebida de terceiros", Escopo.ESTADUAL),
    VENDA_ESTADUAL_ST("5405", "Venda de mercadoria sujeita a ICMS-ST já retido anteriormente", Escopo.ESTADUAL),
    DEVOLUCAO_ESTADUAL("5202", "Devolução de compra para comercialização", Escopo.ESTADUAL),

    VENDA_INTERESTADUAL("6102", "Venda de mercadoria adquirida ou recebida de terceiros", Escopo.INTERESTADUAL),
    VENDA_INTERESTADUAL_ST("6404", "Venda de mercadoria sujeita a ICMS-ST já retido anteriormente", Escopo.INTERESTADUAL),
    DEVOLUCAO_INTERESTADUAL("6202", "Devolução de compra para comercialização", Escopo.INTERESTADUAL);

    private final String codigo;
    private final String descricao;
    private final Escopo escopo;

    CfopReferencia(String codigo, String descricao, Escopo escopo) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.escopo = escopo;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public Escopo getEscopo() {
        return escopo;
    }

    public enum Escopo {
        ESTADUAL,
        INTERESTADUAL
    }

    public static Escopo escopoPara(String ufEmpresa, String ufCliente) {
        if (ufEmpresa == null || ufCliente == null) {
            throw new IllegalArgumentException("UF da empresa e do cliente são obrigatórias para determinar o CFOP");
        }
        return ufEmpresa.equalsIgnoreCase(ufCliente) ? Escopo.ESTADUAL : Escopo.INTERESTADUAL;
    }

    public static List<CfopReferencia> disponiveisPara(Escopo escopo) {
        return Arrays.stream(values()).filter(cfop -> cfop.escopo == escopo).toList();
    }

    public static boolean codigoCompativel(String codigo, Escopo escopo) {
        return disponiveisPara(escopo).stream().anyMatch(cfop -> cfop.codigo.equals(codigo));
    }
}
