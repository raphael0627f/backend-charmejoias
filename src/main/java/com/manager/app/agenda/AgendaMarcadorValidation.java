package com.manager.app.agenda;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class AgendaMarcadorValidation {

    private final AgendaMarcadorRepository repository;

    public void dataExistenteCreate(LocalDate data){
        if (repository.existsByData(data)) {
            throw new ObjetoExistenteException("Já existe um marcador na data " + data);
        }
    }

    public void dataExistenteUpdate(LocalDate data, Long id){
        if (repository.existsByDataAndIdNot(data, id)) {
            throw new ObjetoExistenteException("Já existe um marcador na data " + data);
        }
    }

    public void datasPorTipo(AgendaMarcador marcador){
        if (marcador.getTipo() == AgendaMarcadorTipo.TRABALHA) {
            if (marcador.getDataPreAcerto() == null || marcador.getDataAcerto() == null) {
                throw new ItemInvalidoException("Marcador do tipo TRABALHA exige data do pré-acerto e data do acerto");
            }
            if (marcador.getDataPreAcerto().isBefore(marcador.getData())) {
                throw new ItemInvalidoException("A data do pré-acerto não pode ser anterior à data do marcador");
            }
            if (marcador.getDataAcerto().isBefore(marcador.getDataPreAcerto())) {
                throw new ItemInvalidoException("A data do acerto não pode ser anterior à data do pré-acerto");
            }
        } else if (marcador.getDataPreAcerto() != null || marcador.getDataAcerto() != null) {
            throw new ItemInvalidoException("Marcador do tipo NAO_TRABALHA não pode ter data do pré-acerto nem data do acerto");
        }
    }

    public void periodo(LocalDate inicio, LocalDate fim){
        if (inicio == null || fim == null) {
            throw new ItemInvalidoException("Período obrigatório: informe inicio e fim (yyyy-MM-dd)");
        }
        if (fim.isBefore(inicio)) {
            throw new ItemInvalidoException("Data final não pode ser anterior à data inicial");
        }
    }
}
