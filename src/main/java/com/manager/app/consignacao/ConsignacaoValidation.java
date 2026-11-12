package com.manager.app.consignacao;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.TipoItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConsignacaoValidation {

    public boolean clienteNovo(UUID clienteId){
        return clienteId == null;
    }

    public void validarItem(TipoItem tipo, UUID produtoId, UUID joiaId, Long produtoVariacaoId){
        if (tipo == TipoItem.JOIA) {
            if (joiaId == null) {
                throw new ItemInvalidoException("Informe a joia para consignações do tipo JOIA.");
            }
            if (produtoId != null) {
                throw new ItemInvalidoException("Não é possível informar produto em uma consignação do tipo JOIA.");
            }
            if (produtoVariacaoId != null) {
                throw new ItemInvalidoException("Não é possível informar variação de produto em uma consignação do tipo JOIA.");
            }
        } else {
            if (produtoId == null) {
                throw new ItemInvalidoException("Informe o produto para consignações do tipo PRODUTO.");
            }
            if (joiaId != null) {
                throw new ItemInvalidoException("Não é possível informar joia em uma consignação do tipo PRODUTO.");
            }
        }
    }

}
