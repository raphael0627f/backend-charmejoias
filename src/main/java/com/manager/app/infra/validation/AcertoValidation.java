package com.manager.app.infra.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AcertoValidation {
    public boolean AcertoConsignacaoNova(Long consignacaoId){
        return consignacaoId == null;
    }
}
