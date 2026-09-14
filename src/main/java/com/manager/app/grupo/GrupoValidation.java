package com.manager.app.grupo;

import com.manager.app.shared.exception.ItemInvalidoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GrupoValidation {

    public boolean gruposEmpty(List<Long> gruposIds){
        return gruposIds == null || gruposIds.isEmpty();
    }

    public void validarTipo(List<Grupo> grupos, TipoGrupo tipoEsperado){
        boolean algumIncompativel = grupos.stream().anyMatch(grupo -> grupo.getTipo() != tipoEsperado);
        if (algumIncompativel) {
            throw new ItemInvalidoException("Um ou mais grupos informados não são do tipo " + tipoEsperado);
        }
    }

}
