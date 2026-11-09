package com.manager.app.shared;

import java.text.Normalizer;
import java.util.Locale;

public final class TextoUtils {

    private TextoUtils(){}

    // minúsculo, sem acento e com espaços colapsados - mesma normalização gravada em cidades.nome_busca
    public static String normalizarBusca(String texto){
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
