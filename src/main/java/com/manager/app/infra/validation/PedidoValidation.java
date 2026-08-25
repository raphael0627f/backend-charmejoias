package com.manager.app.infra.validation;

import com.manager.app.infra.exception.ItemInvalidoException;
import com.manager.app.infra.restEnum.TipoItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PedidoValidation {
    public boolean produtoNovo(UUID produtoId){
        return produtoId == null;
    }

    public void validarItem(TipoItem tipo, UUID produtoId, UUID joiaId){
        if (tipo == TipoItem.JOIA) {
            if (joiaId == null) {
                throw new ItemInvalidoException("joiaId é obrigatório para pedidos do tipo JOIA");
            }
            if (produtoId != null) {
                throw new ItemInvalidoException("Não é possível informar produtoId em um pedido do tipo JOIA");
            }
        } else if (joiaId != null) {
            throw new ItemInvalidoException("Não é possível informar joiaId em um pedido do tipo PRODUTO");
        }
    }
}
