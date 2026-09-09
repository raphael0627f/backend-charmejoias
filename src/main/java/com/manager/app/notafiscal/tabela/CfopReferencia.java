package com.manager.app.notafiscal.tabela;

// Determina o escopo da operação (mesmo estado x interestadual) a partir da UF da empresa e do
// cliente. Os CFOPs propriamente ditos (todos os códigos dos grupos 5.xxx/6.xxx) vêm da tabela
// com.manager.app.notafiscal.cfop.Cfop; cada escopo corresponde ao prefixo do código (5 ou 6).
public class CfopReferencia {

    private CfopReferencia() {
    }

    public enum Escopo {
        ESTADUAL("5"),
        INTERESTADUAL("6");

        private final String prefixoCfop;

        Escopo(String prefixoCfop) {
            this.prefixoCfop = prefixoCfop;
        }

        public String getPrefixoCfop() {
            return prefixoCfop;
        }
    }

    public static Escopo escopoPara(String ufEmpresa, String ufCliente) {
        if (ufEmpresa == null || ufCliente == null) {
            throw new IllegalArgumentException("UF da empresa e do cliente são obrigatórias para determinar o CFOP");
        }
        return ufEmpresa.equalsIgnoreCase(ufCliente) ? Escopo.ESTADUAL : Escopo.INTERESTADUAL;
    }
}
