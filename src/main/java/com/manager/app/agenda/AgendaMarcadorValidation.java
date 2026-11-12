package com.manager.app.agenda;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.manager.app.cidade.Cidade;
import com.manager.app.shared.TextoUtils;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AgendaMarcadorValidation {

    private final AgendaMarcadorRepository repository;

    public void dataExistenteCreate(LocalDate data){
        if (repository.existsByData(data)) {
            throw new ObjetoExistenteException("Já existe um marcador na data " + data + ".");
        }
    }

    public void dataExistenteUpdate(LocalDate data, Long id){
        if (repository.existsByDataAndIdNot(data, id)) {
            throw new ObjetoExistenteException("Já existe um marcador na data " + data + ".");
        }
    }

    // campos que dependem do tipo: folga exige nome e não aceita rota/cidades extras; trabalho exige rota
    public void camposPorTipo(AgendaMarcador marcador, Long rotaId, List<Long> cidadeExtraIds){
        boolean temExtras = cidadeExtraIds != null && !cidadeExtraIds.isEmpty();
        if (marcador.getTipo() == AgendaMarcadorTipo.TRABALHA) {
            if (rotaId == null) {
                throw new ItemInvalidoException("Marcador do tipo TRABALHA exige uma rota.");
            }
        } else {
            if (marcador.getNome() == null || marcador.getNome().isBlank()) {
                throw new ItemInvalidoException("Nome do marcador é obrigatório para o tipo NAO_TRABALHA.");
            }
            TextoUtils.validarTexto(marcador.getNome(), "Nome do marcador", 255);
            if (rotaId != null) {
                throw new ItemInvalidoException("Marcador do tipo NAO_TRABALHA não pode ter rota.");
            }
            if (temExtras) {
                throw new ItemInvalidoException("Marcador do tipo NAO_TRABALHA não pode ter cidades extras.");
            }
        }
    }

    public void extrasForaDaRota(AgendaRota rota, List<Cidade> extras){
        Set<Long> idsDaRota = rota.getCidades().stream().map(Cidade::getId).collect(Collectors.toSet());
        for (Cidade extra : extras) {
            if (idsDaRota.contains(extra.getId())) {
                throw new ItemInvalidoException("A cidade " + extra.getNome() + " - " + extra.getUf() + " já pertence à rota e não pode ser informada como cidade extra.");
            }
        }
    }

    public void datasPorTipo(AgendaMarcador marcador){
        if (marcador.getTipo() == AgendaMarcadorTipo.TRABALHA) {
            if (marcador.getDataPreAcerto() == null || marcador.getDataAcerto() == null) {
                throw new ItemInvalidoException("Marcador do tipo TRABALHA exige data do pré-acerto e data do acerto.");
            }
            if (marcador.getDataPreAcerto().isBefore(marcador.getData())) {
                throw new ItemInvalidoException("A data do pré-acerto não pode ser anterior à data do marcador.");
            }
            if (marcador.getDataAcerto().isBefore(marcador.getDataPreAcerto())) {
                throw new ItemInvalidoException("A data do acerto não pode ser anterior à data do pré-acerto.");
            }
        } else if (marcador.getDataPreAcerto() != null || marcador.getDataAcerto() != null) {
            throw new ItemInvalidoException("Marcador do tipo NAO_TRABALHA não pode ter data do pré-acerto nem data do acerto.");
        }
    }

    public void periodo(LocalDate inicio, LocalDate fim){
        if (inicio == null || fim == null) {
            throw new ItemInvalidoException("O período é obrigatório. Informe as datas inicial e final no formato aaaa-mm-dd.");
        }
        if (fim.isBefore(inicio)) {
            throw new ItemInvalidoException("A data final não pode ser anterior à data inicial.");
        }
    }
}
