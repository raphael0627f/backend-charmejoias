package com.manager.app.agenda;

import com.manager.app.cidade.Cidade;
import com.manager.app.cidade.CidadeService;
import com.manager.app.shared.OrdenacaoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.SqlErros;
import org.springframework.dao.DataIntegrityViolationException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.specification.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AgendaRotaService {

    private final AgendaRotaRepository repository;
    private final AgendaRotaValidation validation;
    private final CidadeService cidadeService;
    private final AgendaMarcadorRepository marcadorRepository;

    private static final Set<String> CAMPOS_ORDENACAO = Set.of("id", "nome", "createdAt", "updatedAt");

    public Page<AgendaRota> findAll(String nome, Pageable pageable){
        OrdenacaoUtils.validar(pageable, CAMPOS_ORDENACAO, Set.of());
        Specification<AgendaRota> spec = null;
        if (nome != null && !nome.isBlank()) {
            String termo = nome.trim();
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), termo));
        }
        return repository.findAll(spec, pageable);
    }

    public AgendaRota findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Rota da agenda não encontrada. Id: " + id));
    }

    @Transactional
    public AgendaRota create(AgendaRota rota, List<Long> cidadeIds){
        rota.setId(null);
        normalizar(rota);
        validation.nome(rota.getNome());
        validation.nomeExistenteCreate(rota.getNome());
        validation.cidadesObrigatorias(cidadeIds);
        rota.setCidades(cidadeService.findAllOrdenado(cidadeIds));
        return gravar(() -> repository.saveAndFlush(rota), rota.getNome());
    }

    @Transactional
    public AgendaRota update(AgendaRota rota, List<Long> cidadeIds){
        if (rota.getId() == null) {
            throw new ItemInvalidoException("O Id da rota é obrigatório.");
        }
        AgendaRota rotaUpdate = this.findById(rota.getId());
        normalizar(rota);
        validation.nome(rota.getNome());
        validation.nomeExistenteUpdate(rota.getNome(), rotaUpdate.getId());
        validation.cidadesObrigatorias(cidadeIds);
        List<Cidade> novas = cidadeService.findAllOrdenado(cidadeIds);
        rotaUpdate.setNome(rota.getNome());
        // limpa e grava antes de repor: a ordem muda e (rota_id, cidade_id) é único
        rotaUpdate.getCidades().clear();
        gravar(() -> { repository.flush(); return null; }, rota.getNome());
        rotaUpdate.getCidades().addAll(novas);
        gravar(() -> { repository.flush(); return null; }, rota.getNome());
        tirarDosExtrasAsCidadesDaRota(rotaUpdate.getId(), novas);
        return rotaUpdate;
    }

    // uma cidade não pode ser da rota e extra do mesmo marcador: ao salvar a rota, as cidades que passaram a pertencer
    // a ela saem dos extras dos marcadores dessa rota (mesma transação)
    private void tirarDosExtrasAsCidadesDaRota(Long rotaId, List<Cidade> cidadesDaRota){
        Set<Long> idsDaRota = cidadesDaRota.stream().map(Cidade::getId).collect(Collectors.toSet());
        for (AgendaMarcador marcador : marcadorRepository.findByRota_Id(rotaId)) {
            List<Cidade> extras = marcador.getCidadesExtras();
            if (extras.stream().noneMatch(c -> idsDaRota.contains(c.getId()))) continue;
            List<Cidade> restantes = extras.stream().filter(c -> !idsDaRota.contains(c.getId())).toList();
            // limpa e grava antes de repor: o índice da ordem muda e (marcador_id, cidade_id) é único
            extras.clear();
            marcadorRepository.flush();
            extras.addAll(restantes);
        }
        marcadorRepository.flush();
    }

    @Transactional
    public void delete(Long id){
        AgendaRota rota = this.findById(id);
        validation.podeExcluir(id);
        try {
            repository.delete(rota);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            // marcador criado em paralelo entre a checagem e a exclusão
            if (SqlErros.FOREIGN_KEY.equals(SqlErros.sqlState(e))) {
                throw new ObjetoEmUsoException("Não é possível excluir a rota, pois ela está vinculada a um marcador da agenda.");
            }
            throw e;
        }
    }

    // corrida: a checagem de nome repetido passa para duas requisições ao mesmo tempo e o índice único barra a segunda
    private <T> T gravar(Supplier<T> gravacao, String nome){
        try {
            return gravacao.get();
        } catch (DataIntegrityViolationException e) {
            if (SqlErros.UNIQUE.equals(SqlErros.sqlState(e))) {
                throw new ObjetoExistenteException("Já existe uma rota com o nome \"" + nome + "\".");
            }
            throw e;
        }
    }

    private void normalizar(AgendaRota rota){
        if (rota.getNome() != null) {
            rota.setNome(rota.getNome().trim());
        }
    }
}
