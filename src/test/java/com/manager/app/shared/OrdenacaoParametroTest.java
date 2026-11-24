package com.manager.app.shared;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrdenacaoParametroTest {

    private static final Set<String> CAMPOS = Set.of("vencimento", "valor");

    @Test
    void semSortMantemOsParametrosOriginais() {
        var r = OrdenacaoParametro.resolver("valor", "desc", null, CAMPOS);
        assertEquals("valor", r.ordenar());
        assertEquals("desc", r.direcao());
    }

    @Test
    void sortValidoViraOrdenarEDirecao() {
        var r = OrdenacaoParametro.resolver(null, null, "Valor,desc", CAMPOS);
        assertEquals("valor", r.ordenar());
        assertEquals("desc", r.direcao());
    }

    @Test
    void sortComCampoInexistenteEh400Formal() {
        ItemInvalidoException e = assertThrows(ItemInvalidoException.class, () -> OrdenacaoParametro.resolver(null, null, "xyz", CAMPOS));
        assertEquals("Campo de ordenação inválido.", e.getMessage());
    }

    @Test
    void listaDeOrdemFixaRecusaQualquerSort() {
        OrdenacaoParametro.ordemFixa(null);
        OrdenacaoParametro.ordemFixa(" ");
        assertThrows(ItemInvalidoException.class, () -> OrdenacaoParametro.ordemFixa("xpto"));
    }
}
