package com.manager.app.infra.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PedidoValidation {
    public boolean produtoNovo(UUID produtoId){
        return produtoId == null;
    }
}
