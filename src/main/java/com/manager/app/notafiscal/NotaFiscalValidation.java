package com.manager.app.notafiscal;

import com.manager.app.notafiscal.cfop.CfopRepository;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.notafiscal.tabela.CfopReferencia;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotaFiscalValidation {

    private final CfopRepository cfopRepository;

    public void validarCfop(String cfop, String ufEmpresa, String ufCliente){
        CfopReferencia.Escopo escopo = CfopReferencia.escopoPara(ufEmpresa, ufCliente);
        boolean compativel = cfop != null
                && cfop.startsWith(escopo.getPrefixoCfop())
                && cfopRepository.existsById(cfop);
        if (!compativel) {
            throw new ItemInvalidoException("CFOP " + cfop + " incompatível com a operação (" + escopo + ") entre a UF da empresa (" + ufEmpresa + ") e a UF do cliente (" + ufCliente + ")");
        }
    }
}
