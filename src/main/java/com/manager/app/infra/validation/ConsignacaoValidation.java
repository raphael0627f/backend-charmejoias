package com.manager.app.infra.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConsignacaoValidation {

    public boolean clienteNovo(UUID clienteId){
        return clienteId == null;
    }

}
