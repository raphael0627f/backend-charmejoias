package com.manager.app.shared;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

public final class OrdenacaoUtils {

    private OrdenacaoUtils(){}

    // ?sort= com campo inexistente estourava no repositório (500 sem corpo): vira 400 com os campos permitidos.
    // "ocultos" são campos aceitos mas fora da mensagem (ex.: ordenação padrão interna do endpoint)
    public static void validar(Pageable pageable, Set<String> permitidos, Set<String> ocultos){
        if (pageable == null || pageable.getSort().isUnsorted()) return;
        for (Sort.Order ordem : pageable.getSort()) {
            String campo = ordem.getProperty();
            if (!permitidos.contains(campo) && !ocultos.contains(campo)) {
                throw new ItemInvalidoException("Campo de ordenação inválido: " + campo
                        + ". Campos permitidos: " + String.join(", ", new TreeSet<>(permitidos)));
            }
        }
    }
}
