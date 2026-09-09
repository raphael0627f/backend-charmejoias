package com.manager.app.acertojoia;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AcertoJoiaValidation {
    public boolean AcertoConsignacaoNova(Long consignacaoId){
        return consignacaoId == null;
    }
}
