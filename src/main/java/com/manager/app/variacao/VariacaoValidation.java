package com.manager.app.variacao;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class VariacaoValidation {

    private final VariacaoRepository repository;

    public void nomeExistenteCreate(String nome){
        if(repository.existsByNomeIgnoreCase(nome)){
            throw new ObjetoExistenteException("Variação existente com este nome: " + nome);
        }
    }

    public void nomeExistenteUpdate(String nome, Long id){
        if(repository.existsByNomeIgnoreCaseAndIdNot(nome, id)){
            throw new ObjetoExistenteException("Variação existente com este nome: " + nome);
        }
    }

    public void itensNomesDuplicados(List<ItemVariacao> itens){
        Set<String> nomes = new HashSet<>();
        for (ItemVariacao item : itens) {
            if (!nomes.add(item.getNome().toLowerCase())) {
                throw new ItemInvalidoException("Nome de item duplicado nesta variação: " + item.getNome());
            }
        }
    }
}
