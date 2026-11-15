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
public class FormaPagamentoService {

    private final FormaPagamentoRepository repository;
    private final FinanceiroMapper mapper;

    @Transactional(readOnly = true)
    public List<CadastroSimplesResponseDTO> listar(Boolean ativo, String busca) {
        String termo = busca == null ? "" : BancoService.semAcento(busca.trim());
        return repository.findAll().stream()
                .filter(f -> ativo == null || f.isAtivo() == ativo)
                .filter(f -> termo.isEmpty() || BancoService.semAcento(f.getNome()).contains(termo))
                .sorted((a, b) -> a.getNome().compareToIgnoreCase(b.getNome()))
                .map(mapper::formaPagamento)
                .toList();
    }

    @Transactional(readOnly = true)
    public CadastroSimplesResponseDTO obter(Long id) {
        return mapper.formaPagamento(buscar(id));
    }

    @Transactional
    public CadastroSimplesResponseDTO criar(CadastroSimplesRequestDTO request) {
        String nome = nomeValido(request.nome());
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new ObjetoExistenteException("Já existe uma forma de pagamento com o nome \"" + nome + "\".");
        }
        FormaPagamento forma = new FormaPagamento();
        forma.setNome(nome);
        forma.setAtivo(true);
        return mapper.formaPagamento(repository.save(forma));
    }

    @Transactional
    public CadastroSimplesResponseDTO atualizar(Long id, CadastroSimplesRequestDTO request) {
        FormaPagamento forma = buscar(id);
        String nome = nomeValido(request.nome());
        if (repository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new ObjetoExistenteException("Já existe uma forma de pagamento com o nome \"" + nome + "\".");
        }
        forma.setNome(nome);
        return mapper.formaPagamento(repository.save(forma));
    }

    @Transactional
    public CadastroSimplesResponseDTO ativar(Long id) {
        FormaPagamento forma = buscar(id);
        forma.setAtivo(true);
        return mapper.formaPagamento(repository.save(forma));
    }

    @Transactional
    public CadastroSimplesResponseDTO inativar(Long id) {
        FormaPagamento forma = buscar(id);
        forma.setAtivo(false);
        return mapper.formaPagamento(repository.save(forma));
    }

    // a chave estrangeira dos pagamentos (fase 2) impede excluir forma usada: o handler devolve 409
    @Transactional
    public void excluir(Long id) {
        repository.delete(buscar(id));
        repository.flush();
    }

    public FormaPagamento buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Forma de pagamento não encontrada."));
    }

    private String nomeValido(String nome) {
        String n = nome == null ? "" : nome.trim();
        if (n.isEmpty()) throw new ItemInvalidoException("O nome é obrigatório.");
        TextoUtils.validarTexto(n, "O nome", 100);
        return n;
    }
}
