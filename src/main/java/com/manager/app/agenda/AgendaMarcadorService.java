package com.manager.app.agenda;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgendaMarcadorService {

    private final AgendaMarcadorRepository repository;
    private final AgendaMarcadorValidation validation;

    public List<AgendaMarcador> findByPeriodo(LocalDate inicio, LocalDate fim){
        validation.periodo(inicio, fim);
        return repository.findByPeriodo(inicio, fim);
    }

    public AgendaMarcador findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Marcador de agenda não encontrado. id: " + id));
    }

    @Transactional
    public AgendaMarcador create(AgendaMarcador marcador){
        marcador.setId(null);
        normalizar(marcador);
        validation.datasPorTipo(marcador);
        validation.dataExistenteCreate(marcador.getData());
        return repository.save(marcador);
    }

    @Transactional
    public AgendaMarcador update(AgendaMarcador marcador){
        if (marcador.getId() == null) {
            throw new ItemInvalidoException("Id do marcador é obrigatório");
        }
        AgendaMarcador marcadorUpdate = this.findById(marcador.getId());
        normalizar(marcador);
        validation.datasPorTipo(marcador);
        validation.dataExistenteUpdate(marcador.getData(), marcadorUpdate.getId());
        marcadorUpdate.setData(marcador.getData());
        marcadorUpdate.setNome(marcador.getNome());
        marcadorUpdate.setTipo(marcador.getTipo());
        marcadorUpdate.setDataConferencia(marcador.getDataConferencia());
        marcadorUpdate.setDataAcerto(marcador.getDataAcerto());
        return marcadorUpdate;
    }

    @Transactional
    public void delete(Long id){
        repository.delete(this.findById(id));
    }

    private void normalizar(AgendaMarcador marcador){
        if (marcador.getNome() != null) {
            marcador.setNome(marcador.getNome().trim());
        }
    }
}
