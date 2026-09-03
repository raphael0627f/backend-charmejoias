package com.manager.app.notafiscal;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.notafiscal.tabela.CfopReferencia;
import org.springframework.stereotype.Component;

@Component
public class NotaFiscalValidation {

    public void validarCfop(String cfop, String ufEmpresa, String ufCliente){
        CfopReferencia.Escopo escopo = CfopReferencia.escopoPara(ufEmpresa, ufCliente);
        if (!CfopReferencia.codigoCompativel(cfop, escopo)) {
            throw new ItemInvalidoException("CFOP " + cfop + " incompatível com a operação (" + escopo + ") entre a UF da empresa (" + ufEmpresa + ") e a UF do cliente (" + ufCliente + ")");
        }
    }
}
