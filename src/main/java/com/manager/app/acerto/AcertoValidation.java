package com.manager.app.acerto;

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
