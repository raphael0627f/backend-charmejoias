package com.manager.app.infra.validation;

import com.manager.app.infra.exception.ItemInvalidoException;
import com.manager.app.infra.nfe.CfopReferencia;
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
