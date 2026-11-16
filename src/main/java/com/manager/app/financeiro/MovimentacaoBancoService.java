package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.AjusteRequestDTO;
import com.manager.app.financeiro.dto.EstornoRequestDTO;
import com.manager.app.financeiro.dto.MovimentacaoResponseDTO;
import com.manager.app.financeiro.dto.MovimentacaoResultadoDTO;
import com.manager.app.financeiro.dto.SaldosBancosDTO;
import com.manager.app.financeiro.dto.TransferenciaRequestDTO;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// extrato, saldos, transferência, ajuste e estorno; regras e cálculos em CalculoFinanceiro
@Service
@RequiredArgsConstructor
public class MovimentacaoBancoService {

    static final int TAMANHO_MAXIMO = 200;

    private final MovimentacaoBancoRepository repository;
    private final TransferenciaBancoRepository transferenciaRepository;
    private final ExtratoBancoRepository extratoRepository;
    private final BancoService bancoService;
    private final BancoRepository bancoRepository;
    private final UsuarioAuditorAware auditor;
    private final FinanceiroMapper mapper;
    private final SaldoVisibilidade saldoVisibilidade;

    @Transactional(readOnly = true)
    public Page<MovimentacaoResponseDTO> extrato(Long bancoId, LocalDate inicio, LocalDate fim, OrigemMovimentacao origem,
                                                 String busca, int pagina, int tamanho) {
        if (pagina < 0) throw new ItemInvalidoException("A página deve ser maior ou igual a zero.");
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new ItemInvalidoException("O tamanho da página deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        }
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new ItemInvalidoException("A data inicial não pode ser posterior à data final.");
        }
        if (bancoId != null) bancoService.buscar(bancoId);
        return extratoRepository.buscar(bancoId, inicio, fim, origem, busca, pagina, tamanho);
    }

    @Transactional(readOnly = true)
    public SaldosBancosDTO saldos() {
        Map<Long, BigDecimal> saldos = bancoService.saldos();
        List<SaldosBancosDTO.SaldoBancoDTO> bancos = bancoRepository.findAll().stream()
                .sorted(Comparator.comparing(b -> BancoService.semAcento(b.getNome())))
                .map(b -> new SaldosBancosDTO.SaldoBancoDTO(b.getId(), b.getNome(), b.getTipo(), b.isAtivo(),
                        CalculoFinanceiro.arredondar(saldos.getOrDefault(b.getId(), BigDecimal.ZERO))))
                .toList();
        BigDecimal total = bancos.stream().map(SaldosBancosDTO.SaldoBancoDTO::saldoAtual).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SaldosBancosDTO(bancos, CalculoFinanceiro.arredondar(total));
    }

    @Transactional
    public MovimentacaoResultadoDTO transferir(TransferenciaRequestDTO request) {
        // bloqueio dos dois bancos em ordem crescente de id (evita impasse com outras operações)
        Long idOrigem = request.bancoOrigemId();
        Long idDestino = request.bancoDestinoId();
        Banco origem;
        Banco destino;
        if (idOrigem != null && idDestino != null && idOrigem > idDestino) {
            destino = bancoService.buscarComLock(idDestino);
            origem = bancoService.buscarComLock(idOrigem);
        } else {
            origem = bancoService.buscarComLock(idOrigem);
            destino = bancoService.buscarComLock(idDestino);
        }
        CalculoFinanceiro.ResultadoTransferencia r = CalculoFinanceiro.criarTransferencia(origem, destino, request.valor(), request.data(),
                request.descricao(), usuarioAtual(), LocalDate.now());
        transferenciaRepository.save(r.transferencia());
        repository.save(r.saida());
        repository.save(r.entrada());
        return resultado(List.of(r.saida(), r.entrada()), r.transferencia().getId(), List.of(origem, destino));
    }

    @Transactional
    public MovimentacaoResultadoDTO ajustar(AjusteRequestDTO request) {
        Banco banco = bancoService.buscarComLock(request.bancoId());
        MovimentacaoBanco ajuste = CalculoFinanceiro.criarAjuste(banco, request.tipo(), request.valor(), request.data(),
                request.motivo(), usuarioAtual(), LocalDate.now());
        repository.save(ajuste);
        return resultado(List.of(ajuste), null, List.of(banco));
    }

    @Transactional
    public MovimentacaoResultadoDTO estornar(Long id, EstornoRequestDTO request) {
        // bloqueia os bancos envolvidos (ordem crescente de id) ANTES de reler a movimentação, para enxergar o estado já confirmado
        repository.bancosEnvolvidos(id).stream().sorted().forEach(bancoService::buscarComLock);
        MovimentacaoBanco original = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Movimentação não encontrada."));
        String motivo = CalculoFinanceiro.validarMotivo(request == null ? null : request.motivo());
        CalculoFinanceiro.validarEstornavel(original);

        // transferência: as duas pontas são estornadas juntas
        List<MovimentacaoBanco> alvos = new ArrayList<>();
        TransferenciaBanco transferencia = original.getTransferencia();
        if (transferencia != null) {
            repository.findByTransferenciaIdOrderByIdAsc(transferencia.getId()).stream()
                    .filter(m -> m.getOrigem() == OrigemMovimentacao.TRANSFERENCIA)
                    .forEach(alvos::add);
        } else {
            alvos.add(original);
        }
        // valida todas as pontas antes de alterar qualquer uma
        alvos.forEach(m -> {
            CalculoFinanceiro.validarEstornavel(m);
            CalculoFinanceiro.validarBancoAtivo(m.getBanco());
        });

        Usuario usuario = usuarioAtual();
        LocalDate hoje = LocalDate.now();
        LocalDateTime agora = LocalDateTime.now();
        List<MovimentacaoBanco> geradas = new ArrayList<>();
        List<Banco> afetados = new ArrayList<>();
        for (MovimentacaoBanco m : alvos) {
            geradas.add(repository.save(CalculoFinanceiro.estornar(m, motivo, usuario, hoje, agora)));
            afetados.add(m.getBanco());
        }
        if (transferencia != null) {
            transferencia.setEstornada(true);
            transferenciaRepository.save(transferencia);
        }
        return resultado(geradas, transferencia == null ? null : transferencia.getId(), afetados);
    }

    private MovimentacaoResultadoDTO resultado(List<MovimentacaoBanco> movimentacoes, Long transferenciaId, List<Banco> afetados) {
        repository.flush();
        List<MovimentacaoResponseDTO> dtos = movimentacoes.stream().map(m -> mapper.movimentacao(m, null)).toList();
        return new MovimentacaoResultadoDTO(dtos, transferenciaId, avisoSaldoNegativo(afetados));
    }

    // saldo negativo é permitido: apenas informa
    String avisoSaldoNegativo(List<Banco> bancos) {
        boolean verSaldo = saldoVisibilidade.podeVerSaldo();
        Map<Long, String> avisos = new LinkedHashMap<>();
        for (Banco banco : bancos) {
            if (avisos.containsKey(banco.getId())) continue;
            BigDecimal saldo = repository.saldoDoBanco(banco.getId());
            if (saldo.signum() < 0) {
                avisos.put(banco.getId(), "o saldo do banco \"" + banco.getNome() + "\" ficou negativo" + (verSaldo ? " (" + moeda(saldo) + ")" : ""));
            }
        }
        if (avisos.isEmpty()) return null;
        return "Atenção: após esta operação, " + String.join("; ", avisos.values()) + ".";
    }

    static String moeda(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        return "R$ " + formato.format(CalculoFinanceiro.arredondar(valor));
    }

    private Usuario usuarioAtual() {
        return auditor.getCurrentAuditor().orElse(null);
    }
}
