package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.BancoRequestDTO;
import com.manager.app.financeiro.dto.BancoResponseDTO;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.TextoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BancoService {

    private final BancoRepository repository;
    private final MovimentacaoBancoRepository movimentacaoRepository;
    private final UsuarioAuditorAware auditor;
    private final FinanceiroMapper mapper;

    @Transactional(readOnly = true)
    public List<BancoResponseDTO> listar(Boolean ativo, String busca) {
        Map<Long, BigDecimal> saldos = saldos();
        String termo = busca == null ? "" : semAcento(busca.trim());
        return repository.findAll().stream()
                .filter(b -> ativo == null || b.isAtivo() == ativo)
                .filter(b -> termo.isEmpty() || semAcento(b.getNome()).contains(termo))
                .sorted((a, b) -> a.getNome().compareToIgnoreCase(b.getNome()))
                .map(b -> mapper.banco(b, saldos.getOrDefault(b.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public BancoResponseDTO obter(Long id) {
        Banco banco = buscar(id);
        return mapper.banco(banco, movimentacaoRepository.saldoDoBanco(id));
    }

    @Transactional
    public BancoResponseDTO criar(BancoRequestDTO request) {
        Banco banco = new Banco();
        preencher(banco, request);
        if (repository.existsByNomeIgnoreCase(banco.getNome())) {
            throw new ObjetoExistenteException("Já existe um banco cadastrado com o nome \"" + banco.getNome() + "\".");
        }
        banco.setSaldoInicial(CalculoFinanceiro.arredondar(request.saldoInicial()));
        banco.setDataSaldoInicial(request.dataSaldoInicial() == null ? LocalDate.now() : request.dataSaldoInicial());
        CalculoFinanceiro.validarDataNaoFutura(banco.getDataSaldoInicial(), LocalDate.now());
        banco.setAtivo(true);
        repository.save(banco);
        MovimentacaoBanco inicial = CalculoFinanceiro.movimentoSaldoInicial(banco, usuarioAtual());
        if (inicial != null) movimentacaoRepository.save(inicial);
        return obter(banco.getId());
    }

    @Transactional
    public BancoResponseDTO atualizar(Long id, BancoRequestDTO request) {
        Banco banco = buscarComLock(id);
        preencher(banco, request);
        if (repository.existsByNomeIgnoreCaseAndIdNot(banco.getNome(), id)) {
            throw new ObjetoExistenteException("Já existe um banco cadastrado com o nome \"" + banco.getNome() + "\".");
        }
        // saldo inicial omitido no PUT = mantém o atual
        BigDecimal novoSaldo = request.saldoInicial() == null
                ? CalculoFinanceiro.arredondar(banco.getSaldoInicial()) : CalculoFinanceiro.arredondar(request.saldoInicial());
        LocalDate novaData = request.dataSaldoInicial() == null ? banco.getDataSaldoInicial() : request.dataSaldoInicial();
        boolean saldoMudou = novoSaldo.compareTo(CalculoFinanceiro.arredondar(banco.getSaldoInicial())) != 0
                || !novaData.equals(banco.getDataSaldoInicial());
        if (saldoMudou) {
            if (movimentacaoRepository.existsByBancoIdAndOrigemNot(id, OrigemMovimentacao.SALDO_INICIAL)) {
                throw new ItemInvalidoException("Não é possível alterar o saldo inicial, pois já existem movimentações neste banco. Utilize um Ajuste.");
            }
            CalculoFinanceiro.validarDataNaoFutura(novaData, LocalDate.now());
            banco.setSaldoInicial(novoSaldo);
            banco.setDataSaldoInicial(novaData);
            regravarSaldoInicial(banco);
        }
        repository.save(banco);
        return obter(id);
    }

    @Transactional
    public BancoResponseDTO ativar(Long id) {
        Banco banco = buscar(id);
        banco.setAtivo(true);
        repository.save(banco);
        return obter(id);
    }

    @Transactional
    public BancoResponseDTO inativar(Long id) {
        Banco banco = buscar(id);
        banco.setAtivo(false);
        repository.save(banco);
        return obter(id);
    }

    @Transactional
    public void excluir(Long id) {
        Banco banco = buscar(id);
        if (movimentacaoRepository.existsByBancoIdAndOrigemNot(id, OrigemMovimentacao.SALDO_INICIAL)) {
            throw new ObjetoEmUsoException("Não é possível excluir o banco \"" + banco.getNome() + "\", pois ele possui movimentações. Inative-o.");
        }
        movimentacaoRepository.findFirstByBancoIdAndOrigem(id, OrigemMovimentacao.SALDO_INICIAL).ifPresent(movimentacaoRepository::delete);
        repository.delete(banco);
    }

    // bloqueia a linha do banco (SELECT ... FOR UPDATE) até o fim da transação
    public Banco buscarComLock(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Banco não encontrado."));
    }

    public Banco buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Banco não encontrado."));
    }

    public Map<Long, BigDecimal> saldos() {
        Map<Long, BigDecimal> saldos = new HashMap<>();
        for (Object[] linha : movimentacaoRepository.saldosPorBanco()) {
            saldos.put((Long) linha[0], (BigDecimal) linha[1]);
        }
        return saldos;
    }

    // cria, atualiza ou remove a movimentação SALDO_INICIAL conforme o novo saldo (só chamada sem outras movimentações)
    private void regravarSaldoInicial(Banco banco) {
        MovimentacaoBanco existente = movimentacaoRepository.findFirstByBancoIdAndOrigem(banco.getId(), OrigemMovimentacao.SALDO_INICIAL).orElse(null);
        MovimentacaoBanco nova = CalculoFinanceiro.movimentoSaldoInicial(banco, usuarioAtual());
        if (existente != null && nova == null) {
            movimentacaoRepository.delete(existente);
        } else if (existente == null && nova != null) {
            movimentacaoRepository.save(nova);
        } else if (existente != null) {
            existente.setTipo(nova.getTipo());
            existente.setValor(nova.getValor());
            existente.setData(nova.getData());
            movimentacaoRepository.save(existente);
        }
    }

    private void preencher(Banco banco, BancoRequestDTO r) {
        String nome = r.nome() == null ? "" : r.nome().trim();
        if (nome.isEmpty()) throw new ItemInvalidoException("O nome é obrigatório.");
        TextoUtils.validarTexto(nome, "O nome", 100);
        TextoUtils.validarTexto(r.codigoBanco(), "O código do banco", 10);
        TextoUtils.validarTexto(r.agencia(), "A agência", 20);
        TextoUtils.validarTexto(r.conta(), "A conta", 30);
        TextoUtils.validarTexto(r.observacao(), "A observação", 500);
        if (r.saldoInicial() != null && r.saldoInicial().stripTrailingZeros().scale() > CalculoFinanceiro.ESCALA) {
            throw new ItemInvalidoException("O saldo inicial deve ter no máximo duas casas decimais.");
        }
        banco.setNome(nome);
        banco.setTipo(r.tipo());
        banco.setCodigoBanco(vazioParaNulo(r.codigoBanco()));
        banco.setAgencia(vazioParaNulo(r.agencia()));
        banco.setConta(vazioParaNulo(r.conta()));
        banco.setObservacao(vazioParaNulo(r.observacao()));
    }

    private Usuario usuarioAtual() {
        return auditor.getCurrentAuditor().orElse(null);
    }

    static String vazioParaNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    static String semAcento(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
