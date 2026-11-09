package com.manager.app.agenda;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.manager.app.cidade.Cidade;
import com.manager.app.cidade.CidadeService;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.SqlErros;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgendaMarcadorService {

    private final AgendaMarcadorRepository repository;
    private final AgendaMarcadorValidation validation;
    private final AgendaRotaService rotaService;
    private final CidadeService cidadeService;

    public List<AgendaMarcador> findByPeriodo(LocalDate inicio, LocalDate fim){
        validation.periodo(inicio, fim);
        return repository.findByPeriodo(inicio, fim);
    }

    public AgendaMarcador findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Marcador de agenda não encontrado. id: " + id));
    }

    @Transactional
    public AgendaMarcador create(AgendaMarcador marcador, Long rotaId, List<Long> cidadeExtraIds){
        marcador.setId(null);
        normalizar(marcador);
        validation.camposPorTipo(marcador, rotaId, cidadeExtraIds);
        validation.datasPorTipo(marcador);
        validation.dataExistenteCreate(marcador.getData());
        aplicarRotaECidades(marcador, rotaId, cidadeExtraIds);
        return gravar(() -> repository.saveAndFlush(marcador), marcador.getData());
    }

    @Transactional
    public AgendaMarcador update(AgendaMarcador marcador, Long rotaId, List<Long> cidadeExtraIds){
        if (marcador.getId() == null) {
            throw new ItemInvalidoException("Id do marcador é obrigatório");
        }
        AgendaMarcador marcadorUpdate = this.findById(marcador.getId());
        normalizar(marcador);
        validation.camposPorTipo(marcador, rotaId, cidadeExtraIds);
        validation.datasPorTipo(marcador);
        validation.dataExistenteUpdate(marcador.getData(), marcadorUpdate.getId());
        // resolve rota e extras antes de mexer na entidade gerenciada (erro de validação não deixa alteração pela metade)
        aplicarRotaECidades(marcador, rotaId, cidadeExtraIds);
        marcadorUpdate.getCidadesExtras().clear();
        gravar(() -> { repository.flush(); return null; }, marcador.getData());
        marcadorUpdate.setData(marcador.getData());
        marcadorUpdate.setNome(marcador.getNome());
        marcadorUpdate.setTipo(marcador.getTipo());
        marcadorUpdate.setRota(marcador.getRota());
        marcadorUpdate.getCidadesExtras().addAll(marcador.getCidadesExtras());
        marcadorUpdate.setDataPreAcerto(marcador.getDataPreAcerto());
        marcadorUpdate.setDataAcerto(marcador.getDataAcerto());
        gravar(() -> { repository.flush(); return null; }, marcador.getData());
        return marcadorUpdate;
    }

    @Transactional
    public void delete(Long id){
        repository.delete(this.findById(id));
    }

    // corrida: a checagem de data repetida passa para duas requisições ao mesmo tempo e o índice único barra a segunda;
    // FK: a rota foi excluída entre a leitura e a gravação
    private <T> T gravar(Supplier<T> gravacao, LocalDate data){
        try {
            return gravacao.get();
        } catch (DataIntegrityViolationException e) {
            String sqlState = SqlErros.sqlState(e);
            if (SqlErros.UNIQUE.equals(sqlState)) {
                throw new ObjetoExistenteException("Já existe um marcador na data " + data);
            }
            if (SqlErros.FOREIGN_KEY.equals(sqlState)) {
                throw new ObjetoNaoEncontradoException("Rota ou cidade do marcador não encontrada (removida durante a operação)");
            }
            throw e;
        }
    }

    // TRABALHA: carrega a rota e as cidades extras (uma consulta); NAO_TRABALHA: sem rota nem extras
    private void aplicarRotaECidades(AgendaMarcador marcador, Long rotaId, List<Long> cidadeExtraIds){
        if (marcador.getTipo() == AgendaMarcadorTipo.TRABALHA) {
            AgendaRota rota = rotaService.findById(rotaId);
            List<Cidade> extras = cidadeService.findAllOrdenado(cidadeExtraIds);
            validation.extrasForaDaRota(rota, extras);
            marcador.setRota(rota);
            marcador.setCidadesExtras(extras);
            marcador.setNome(null);
        } else {
            marcador.setRota(null);
            marcador.setCidadesExtras(new ArrayList<>());
        }
    }

    private void normalizar(AgendaMarcador marcador){
        if (marcador.getNome() != null) {
            marcador.setNome(marcador.getNome().trim());
        }
    }
}
