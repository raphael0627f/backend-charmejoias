package com.manager.app.agenda;

import com.manager.app.shared.TextoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AgendaRotaValidation {

    private final AgendaRotaRepository repository;
    private final AgendaMarcadorRepository marcadorRepository;

    public void nomeExistenteCreate(String nome){
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new ObjetoExistenteException("Já existe uma rota com o nome \"" + nome + "\"");
        }
    }

    public void nomeExistenteUpdate(String nome, Long id){
        if (repository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new ObjetoExistenteException("Já existe uma rota com o nome \"" + nome + "\"");
        }
    }

    public void nome(String nome){
        TextoUtils.validarTexto(nome, "Nome da rota", 255);
    }

    public void cidadesObrigatorias(List<Long> cidadeIds){
        if (cidadeIds == null || cidadeIds.isEmpty()) {
            throw new ItemInvalidoException("A rota precisa ter ao menos uma cidade");
        }
    }

    public void podeExcluir(Long id){
        long usos = marcadorRepository.countByRota_Id(id);
        if (usos > 0) {
            throw new ObjetoEmUsoException("Não é possível excluir a rota: ela está em uso em " + usos
                    + (usos == 1 ? " marcador da agenda" : " marcadores da agenda"));
        }
    }
}
