package com.manager.app.shared;

import com.manager.app.shared.exception.ItemInvalidoException;

import java.util.Locale;
import java.util.Set;

// parâmetro "sort" (campo[,asc|desc]) das listas que ordenam por "ordenar"/"direcao": campo inexistente vira 400 formal, como nas demais listas paginadas
public final class OrdenacaoParametro {

    private OrdenacaoParametro() {}

    public record Resolvida(String ordenar, String direcao) {
    }

    public static Resolvida resolver(String ordenar, String direcao, String sort, Set<String> camposAceitos) {
        if (sort == null || sort.isBlank()) return new Resolvida(ordenar, direcao);
        String[] partes = sort.split(",", 2);
        String campo = partes[0].trim().toLowerCase(Locale.ROOT);
        if (!camposAceitos.contains(campo)) throw new ItemInvalidoException("Campo de ordenação inválido.");
        String dir = partes.length > 1 && !partes[1].isBlank() ? partes[1].trim() : direcao;
        return new Resolvida(ordenar != null && !ordenar.isBlank() ? ordenar : campo, direcao != null && !direcao.isBlank() ? direcao : dir);
    }
}
