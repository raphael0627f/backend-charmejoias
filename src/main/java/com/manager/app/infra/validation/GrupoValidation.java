package com.manager.app.infra.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GrupoValidation {

    public boolean gruposEmpty(List<Long> gruposIds){
        return gruposIds == null || gruposIds.isEmpty();
    }

}
