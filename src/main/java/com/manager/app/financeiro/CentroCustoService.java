package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.CadastroSimplesRequestDTO;
import com.manager.app.financeiro.dto.CadastroSimplesResponseDTO;
import com.manager.app.shared.TextoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CentroCustoService {

    private final CentroCustoRepository repository;
    private final FinanceiroMapper mapper;

    @Transactional(readOnly = true)
    public List<CadastroSimplesResponseDTO> listar(Boolean ativo, String busca) {
        String termo = busca == null ? "" : BancoService.semAcento(busca.trim());
        return repository.findAll().stream()
                .filter(c -> ativo == null || c.isAtivo() == ativo)
                .filter(c -> termo.isEmpty() || BancoService.semAcento(c.getNome()).contains(termo))
                .sorted((a, b) -> a.getNome().compareToIgnoreCase(b.getNome()))
                .map(mapper::centroCusto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CadastroSimplesResponseDTO obter(Long id) {
        return mapper.centroCusto(buscar(id));
    }

    @Transactional
    public CadastroSimplesResponseDTO criar(CadastroSimplesRequestDTO request) {
        String nome = nomeValido(request.nome());
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new ObjetoExistenteException("Já existe um centro de custo com o nome \"" + nome + "\".");
        }
        CentroCusto centro = new CentroCusto();
        centro.setNome(nome);
        centro.setAtivo(true);
        return mapper.centroCusto(repository.save(centro));
    }

    @Transactional
    public CadastroSimplesResponseDTO atualizar(Long id, CadastroSimplesRequestDTO request) {
        CentroCusto centro = buscar(id);
        String nome = nomeValido(request.nome());
        if (repository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new ObjetoExistenteException("Já existe um centro de custo com o nome \"" + nome + "\".");
        }
        centro.setNome(nome);
        return mapper.centroCusto(repository.save(centro));
    }

    @Transactional
    public CadastroSimplesResponseDTO ativar(Long id) {
        CentroCusto centro = buscar(id);
        centro.setAtivo(true);
        return mapper.centroCusto(repository.save(centro));
    }

    @Transactional
    public CadastroSimplesResponseDTO inativar(Long id) {
        CentroCusto centro = buscar(id);
        centro.setAtivo(false);
        return mapper.centroCusto(repository.save(centro));
    }

    // a chave estrangeira dos títulos (fase 2) impede excluir centro usado: o handler devolve 409
    @Transactional
    public void excluir(Long id) {
        repository.delete(buscar(id));
        repository.flush();
    }

    public CentroCusto buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Centro de custo não encontrado."));
    }

    private String nomeValido(String nome) {
        String n = nome == null ? "" : nome.trim();
        if (n.isEmpty()) throw new ItemInvalidoException("O nome é obrigatório.");
        TextoUtils.validarTexto(n, "O nome", 100);
        return n;
    }
}
