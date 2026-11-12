package com.manager.app.shared;

import com.manager.app.shared.exception.ItemInvalidoException;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TextoUtils {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private TextoUtils(){}

    // data nas mensagens ao usuário: dd/MM/aaaa
    public static String data(LocalDate data){
        return data == null ? "" : DATA_BR.format(data);
    }

    // campo de texto que vai para coluna varchar: recusa NUL (o Postgres rejeita) e tamanho acima do limite da coluna,
    // com 400 e mensagem no padrão do projeto (antes estourava a coluna e virava 503)
    public static void validarTexto(String valor, String campo, int maximo){
        if (valor == null) return;
        if (valor.indexOf('\u0000') >= 0) {
            throw new ItemInvalidoException(campo + " contém caracteres inválidos.");
        }
        if (valor.length() > maximo) {
            throw new ItemInvalidoException(campo + " deve ter no máximo " + maximo + " caracteres.");
        }
    }

    // minúsculo, sem acento, pontuação/hífen/apóstrofo viram espaço e espaços colapsados - mesma normalização
    // gravada em cidades.nome_busca / nome_normalizado (V60)
    public static String normalizarBusca(String texto){
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
