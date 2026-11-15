package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.ContaPagarCriacaoResponseDTO;
import com.manager.app.financeiro.dto.ContaPagarDetalheDTO;
import com.manager.app.financeiro.dto.ContaPagarRequestDTO;
import com.manager.app.financeiro.dto.ContaPagarResumoDTO;
import com.manager.app.financeiro.dto.RecorrenciaDTOs;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.TextoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// recorrência: criação da regra e das ocorrências, rotina de completar a janela, reajuste e encerramento.
// Concorrência: a regra é bloqueada (SELECT FOR UPDATE) antes de gerar, reajustar ou encerrar; cada título afetado também é bloqueado
// (regra primeiro e depois os títulos em ordem de id) e reavaliado já com o bloqueio. A unicidade (recorrencia_id, ocorrencia_data) no banco
// é a última garantia contra duplicidade.
@Service
@RequiredArgsConstructor
@Slf4j
public class ContaPagarRecorrenciaService {

    private static final int PROXIMAS = 6;

    private final ContaPagarRecorrenciaRepository repository;
    private final ContaPagarRecorrenciaEventoRepository eventoRegraRepository;
    private final ContaPagarRepository contaPagarRepository;
    private final ContaPagarEventoRepository eventoRepository;
    private final ContaPagarPagamentoRepository pagamentoRepository;
    private final CategoriaDespesaRepository categoriaRepository;
    private final CentroCustoRepository centroCustoRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final UsuarioAuditorAware auditor;
    private final ContaPagarMapper mapper;
    private final FinanceiroMapper financeiroMapper;
    private final PlatformTransactionManager transactionManager;

    // ---------- criação (chamada por ContaPagarService.criar, dentro da transação dele) ----------

    ContaPagarCriacaoResponseDTO criar(ContaPagarService.Base base, ContaPagarRequestDTO r, LocalDate hoje, LocalDate emissao) {
        if (r.parcelamento() != null) throw new ItemInvalidoException("O lançamento recorrente não pode ser combinado com parcelamento.");
        if (r.linhaDigitavel() != null && !r.linhaDigitavel().isBlank()) {
            throw new ItemInvalidoException("A linha digitável não pode ser informada em lançamento recorrente. Informe-a individualmente em cada título.");
        }
        ContaPagarRequestDTO.Recorrencia rec = r.recorrencia();
        List<LocalDate> datas = RecorrenciaCalculo.validarCriacao(rec.periodicidade(), r.vencimento(), rec.dataFim(), hoje);
        Usuario usuario = usuarioAtual();

        ContaPagarRecorrencia regra = new ContaPagarRecorrencia();
        regra.setPeriodicidade(rec.periodicidade());
        regra.setDataInicio(r.vencimento());
        regra.setDataFim(rec.dataFim());
        regra.setDescricao(base.descricao());
        regra.setFavorecido(base.favorecido());
        regra.setCategoria(base.categoria());
        regra.setCentroCusto(base.centroCusto());
        regra.setValor(base.valor());
        regra.setFormaPrevista(base.forma());
        regra.setDocumento(base.documento());
        regra.setObservacao(base.observacao());
        regra.setValorVariavel(RecorrenciaCalculo.valorVariavel(r));
        regra.setAtiva(true);
        regra.setUltimaGeracao(LocalDateTime.now());
        repository.saveAndFlush(regra);
        eventoRegra(regra, "RECORRENCIA_CRIADA", "Recorrência " + rotulo(regra.getPeriodicidade()) + " criada no valor de "
                + MovimentacaoBancoService.moeda(regra.getValor()) + (regra.isValorVariavel() ? " (valor estimado, variável a cada mês)" : "") + ", com primeiro vencimento em " + TextoUtils.data(regra.getDataInicio())
                + (regra.getDataFim() == null ? "" : " e término em " + TextoUtils.data(regra.getDataFim())) + ". Ocorrências geradas: " + datas.size() + ".", usuario);

        List<ContaPagar> titulos = gerar(regra, datas, emissao, usuario);
        List<ContaPagarResumoDTO> resposta = titulos.stream().map(t -> mapper.resumo(t, hoje)).toList();
        return new ContaPagarCriacaoResponseDTO(null, regra.getId(), resposta);
    }

    // cria os títulos das datas informadas (todas ainda não geradas)
    private List<ContaPagar> gerar(ContaPagarRecorrencia regra, List<LocalDate> datas, LocalDate emissao, Usuario usuario) {
        List<ContaPagar> criados = new ArrayList<>();
        for (LocalDate data : datas) {
            ContaPagar c = RecorrenciaCalculo.montarOcorrencia(regra, data, emissao);
            c.setNumero(contaPagarRepository.proximoNumero());
            contaPagarRepository.save(c);
            eventoTitulo(c, "RECORRENCIA_CRIADA", "Título gerado pela recorrência " + rotulo(regra.getPeriodicidade()) + " nº " + regra.getId()
                    + ", no valor de " + MovimentacaoBancoService.moeda(c.getValor()) + ", com vencimento em " + TextoUtils.data(data) + ".", usuario);
            criados.add(c);
        }
        return criados;
    }

    // ---------- rotina: completa a janela de 12 meses de todas as regras ativas (idempotente) ----------

    // cada regra em transação própria: a falha de uma não impede as demais. Devolve o total de títulos gerados.
    public int completarJanela(LocalDate hoje) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int total = 0;
        for (Long id : repository.idsAtivas()) {
            try {
                Integer gerados = tx.execute(status -> completarRegra(id, hoje));
                total += gerados == null ? 0 : gerados;
            } catch (RuntimeException e) {
                log.error("Falha ao completar a janela da recorrência {}", id, e);
            }
        }
        return total;
    }

    private int completarRegra(Long id, LocalDate hoje) {
        ContaPagarRecorrencia regra = repository.findByIdForUpdate(id).orElse(null);
        if (regra == null || !regra.isAtiva()) return 0;
        LocalDate limite = RecorrenciaCalculo.limiteJanela(hoje, regra.getDataFim());
        List<LocalDate> faltantes = RecorrenciaCalculo.faltantes(regra.getPeriodicidade(), regra.getDataInicio(), limite, contaPagarRepository.ocorrencias(id));
        regra.setUltimaGeracao(LocalDateTime.now());
        repository.save(regra);
        if (faltantes.isEmpty()) return 0;
        List<ContaPagar> criados = gerar(regra, faltantes, hoje, null);
        eventoRegra(regra, "RECORRENCIA_CRIADA", "Geração automática: " + criados.size() + " ocorrência(s) criada(s) até " + TextoUtils.data(limite) + ".", null);
        return criados.size();
    }

    // ---------- consulta ----------

    @Transactional(readOnly = true)
    public RecorrenciaDTOs.Regra obter(Long id) {
        return montar(buscar(id), LocalDate.now());
    }

    // resumo da regra e posição na série, para o detalhe do título (nulo quando o título não nasceu de uma regra)
    @Transactional(readOnly = true)
    public ContaPagarDetalheDTO.RecorrenciaResumo resumoDoTitulo(ContaPagar c) {
        if (c.getRecorrenciaId() == null) return null;
        ContaPagarRecorrencia regra = repository.findById(c.getRecorrenciaId()).orElse(null);
        if (regra == null) return null;
        long posicao = c.getOcorrenciaData() == null ? 0
                : contaPagarRepository.countByRecorrenciaIdAndOcorrenciaDataLessThanEqual(regra.getId(), c.getOcorrenciaData());
        return new ContaPagarDetalheDTO.RecorrenciaResumo(regra.getId(), regra.getPeriodicidade(), regra.isAtiva(), regra.getDataFim(), posicao,
                contaPagarRepository.countByRecorrenciaId(regra.getId()), regra.isValorVariavel());
    }

    // ---------- reajuste ----------

    @Transactional
    public RecorrenciaDTOs.ResultadoReajuste reajustar(Long id, RecorrenciaDTOs.Reajuste r) {
        if (r == null) throw new ItemInvalidoException("Os dados do reajuste são obrigatórios.");
        BigDecimal valor = CalculoFinanceiro.validarValorPositivo(r.valor());
        if (r.aPartirDe() == null) throw new ItemInvalidoException("A data a partir da qual o reajuste vale é obrigatória.");
        TextoUtils.validarTexto(r.descricao(), "A descrição", 200);
        TextoUtils.validarTexto(r.favorecido(), "O favorecido", 150);
        TextoUtils.validarTexto(r.documento(), "O documento", 60);
        TextoUtils.validarTexto(r.observacao(), "A observação", 1000);
        if (r.descricao() != null && r.descricao().isBlank()) throw new ItemInvalidoException("A descrição não pode ser vazia.");
        CategoriaDespesa categoria = null;
        if (r.categoriaId() != null) {
            categoria = categoriaRepository.findById(r.categoriaId()).orElseThrow(() -> new ItemInvalidoException("A categoria informada não foi encontrada."));
            if (!categoria.isAtivo()) throw new ItemInvalidoException("A categoria \"" + categoria.getNome() + "\" está inativa.");
        }
        CentroCusto centro = null;
        if (r.centroCustoId() != null) {
            centro = centroCustoRepository.findById(r.centroCustoId()).orElseThrow(() -> new ItemInvalidoException("O centro de custo informado não foi encontrado."));
            if (!centro.isAtivo()) throw new ItemInvalidoException("O centro de custo \"" + centro.getNome() + "\" está inativo.");
        }
        FormaPagamento forma = null;
        if (r.formaPrevistaId() != null) {
            forma = formaPagamentoRepository.findById(r.formaPrevistaId()).orElseThrow(() -> new ItemInvalidoException("A forma de pagamento informada não foi encontrada."));
            if (!forma.isAtivo()) throw new ItemInvalidoException("A forma de pagamento \"" + forma.getNome() + "\" está inativa.");
        }

        ContaPagarRecorrencia regra = bloquear(id);
        if (!regra.isAtiva()) throw new ItemInvalidoException("A recorrência está encerrada e não pode ser reajustada.");
        Usuario usuario = usuarioAtual();

        List<String> alteracoes = new ArrayList<>();
        if (regra.getValor().compareTo(valor) != 0) {
            alteracoes.add("valor: " + MovimentacaoBancoService.moeda(regra.getValor()) + " → " + MovimentacaoBancoService.moeda(valor));
        }
        regra.setValor(valor);
        if (r.descricao() != null) {
            String nova = r.descricao().trim();
            if (!nova.equals(regra.getDescricao())) alteracoes.add("descrição: \"" + regra.getDescricao() + "\" → \"" + nova + "\"");
            regra.setDescricao(nova);
        }
        if (r.favorecido() != null) {
            String novo = BancoService.vazioParaNulo(r.favorecido());
            if (!java.util.Objects.equals(novo, regra.getFavorecido())) alteracoes.add("favorecido: " + nv(regra.getFavorecido()) + " → " + nv(novo));
            regra.setFavorecido(novo);
        }
        if (categoria != null) {
            if (!categoria.getId().equals(regra.getCategoria().getId())) alteracoes.add("categoria: " + regra.getCategoria().getNome() + " → " + categoria.getNome());
            regra.setCategoria(categoria);
        }
        if (centro != null) {
            if (regra.getCentroCusto() == null || !centro.getId().equals(regra.getCentroCusto().getId())) {
                alteracoes.add("centro de custo: " + (regra.getCentroCusto() == null ? "(nenhum)" : regra.getCentroCusto().getNome()) + " → " + centro.getNome());
            }
            regra.setCentroCusto(centro);
        }
        if (forma != null) {
            if (regra.getFormaPrevista() == null || !forma.getId().equals(regra.getFormaPrevista().getId())) {
                alteracoes.add("forma prevista: " + (regra.getFormaPrevista() == null ? "(nenhuma)" : regra.getFormaPrevista().getNome()) + " → " + forma.getNome());
            }
            regra.setFormaPrevista(forma);
        }
        if (r.documento() != null) {
            String novo = BancoService.vazioParaNulo(r.documento());
            if (!java.util.Objects.equals(novo, regra.getDocumento())) alteracoes.add("documento: " + nv(regra.getDocumento()) + " → " + nv(novo));
            regra.setDocumento(novo);
        }
        if (r.observacao() != null) {
            String novo = BancoService.vazioParaNulo(r.observacao());
            if (!java.util.Objects.equals(novo, regra.getObservacao())) alteracoes.add("observação alterada");
            regra.setObservacao(novo);
        }
        repository.save(regra);

        // bloqueia as ocorrências (ordem de id) e só então reavalia o estado de cada uma
        int atualizadas = 0;
        for (Long tituloId : contaPagarRepository.idsDaRecorrencia(id)) {
            ContaPagar t = contaPagarRepository.findByIdForUpdate(tituloId).orElse(null);
            if (t == null || !RecorrenciaCalculo.reajustavel(t, r.aPartirDe(), pagamentoRepository.existsByContaPagarIdAndEstornadaFalse(tituloId), regra.isValorVariavel())) continue;
            BigDecimal valorAntes = t.getValor();
            t.setValor(regra.getValor());
            t.setDescricao(regra.getDescricao());
            t.setFavorecido(regra.getFavorecido());
            t.setCategoria(regra.getCategoria());
            t.setCentroCusto(regra.getCentroCusto());
            t.setFormaPrevista(regra.getFormaPrevista());
            t.setDocumento(regra.getDocumento());
            t.setObservacao(regra.getObservacao());
            contaPagarRepository.save(t);
            eventoTitulo(t, "RECORRENCIA_REAJUSTADA", "Título reajustado pela recorrência nº " + id + ": valor " + MovimentacaoBancoService.moeda(valorAntes)
                    + " → " + MovimentacaoBancoService.moeda(t.getValor()) + ".", usuario);
            atualizadas++;
        }
        eventoRegra(regra, "RECORRENCIA_REAJUSTADA", "Reajuste a partir de " + TextoUtils.data(r.aPartirDe()) + ": "
                + (alteracoes.isEmpty() ? "nenhum dado da regra foi alterado" : String.join("; ", alteracoes)) + ". Ocorrências atualizadas: " + atualizadas + ".", usuario);
        return new RecorrenciaDTOs.ResultadoReajuste(atualizadas, montar(regra, LocalDate.now()));
    }

    // ---------- encerramento ----------

    @Transactional
    public RecorrenciaDTOs.ResultadoEncerramento encerrar(Long id, RecorrenciaDTOs.Encerramento r) {
        boolean cancelar = r != null && r.cancelarFuturas();
        String motivo = r == null ? null : BancoService.vazioParaNulo(r.motivo());
        if (cancelar) motivo = CalculoFinanceiro.validarMotivo(motivo);
        else TextoUtils.validarTexto(motivo, "O motivo", 500);

        ContaPagarRecorrencia regra = bloquear(id);
        if (!regra.isAtiva()) throw new ItemInvalidoException("A recorrência já está encerrada.");
        Usuario usuario = usuarioAtual();
        LocalDate hoje = LocalDate.now();
        regra.setAtiva(false);
        regra.setEncerradaEm(LocalDateTime.now());
        repository.save(regra);

        int canceladas = 0;
        if (cancelar) {
            for (Long tituloId : contaPagarRepository.idsDaRecorrencia(id)) {
                ContaPagar t = contaPagarRepository.findByIdForUpdate(tituloId).orElse(null);
                if (t == null || !RecorrenciaCalculo.encerravel(t, hoje, pagamentoRepository.existsByContaPagarIdAndEstornadaFalse(tituloId))) continue;
                t.setSituacao(SituacaoContaPagar.CANCELADO);
                t.setCancelamentoMotivo(motivo);
                t.setCancelamentoEm(LocalDateTime.now());
                t.setCanceladoPor(usuario);
                contaPagarRepository.save(t);
                eventoTitulo(t, "CANCELAMENTO", "Título cancelado. Motivo: " + motivo, usuario);
                eventoTitulo(t, "RECORRENCIA_ENCERRADA", "Título cancelado pelo encerramento da recorrência nº " + id + ".", usuario);
                canceladas++;
            }
        }
        eventoRegra(regra, "RECORRENCIA_ENCERRADA", "Recorrência encerrada" + (motivo == null ? "" : ". Motivo: " + motivo) + ". "
                + (cancelar ? "Ocorrências futuras canceladas: " + canceladas + "." : "As ocorrências já geradas foram mantidas."), usuario);
        return new RecorrenciaDTOs.ResultadoEncerramento(canceladas, montar(regra, hoje));
    }

    // ---------- apoio ----------

    private ContaPagarRecorrencia buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Recorrência não encontrada."));
    }

    private ContaPagarRecorrencia bloquear(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Recorrência não encontrada."));
    }

    private RecorrenciaDTOs.Regra montar(ContaPagarRecorrencia g, LocalDate hoje) {
        CategoriaDespesa pai = g.getCategoria().getPai();
        ContaPagarResumoDTO.CategoriaRef categoria = new ContaPagarResumoDTO.CategoriaRef(g.getCategoria().getId(), g.getCategoria().getNome(),
                pai == null ? null : pai.getId(), pai == null ? null : pai.getNome());
        List<ContaPagarResumoDTO> proximas = contaPagarRepository
                .findTop6ByRecorrenciaIdAndSituacaoNotAndVencimentoGreaterThanEqualOrderByVencimentoAscIdAsc(g.getId(), SituacaoContaPagar.CANCELADO, hoje).stream()
                .limit(PROXIMAS).map(t -> mapper.resumo(t, hoje)).toList();
        List<ContaPagarDetalheDTO.Evento> eventos = eventoRegraRepository.findByRecorrenciaIdOrderByIdAsc(g.getId()).stream()
                .map(e -> new ContaPagarDetalheDTO.Evento(e.getId(), e.getEvento(), e.getDetalhe(), financeiroMapper.usuario(e.getUsuario()), e.getDataHora())).toList();
        return new RecorrenciaDTOs.Regra(g.getId(), g.getPeriodicidade(), g.getDataInicio(), g.getDataFim(), g.isAtiva(), g.isValorVariavel(), g.getDescricao(), g.getFavorecido(),
                categoria, g.getCentroCusto() == null ? null : new ContaPagarResumoDTO.Ref(g.getCentroCusto().getId(), g.getCentroCusto().getNome()),
                g.getFormaPrevista() == null ? null : new ContaPagarResumoDTO.Ref(g.getFormaPrevista().getId(), g.getFormaPrevista().getNome()),
                CalculoFinanceiro.arredondar(g.getValor()), g.getDocumento(), g.getObservacao(), g.getUltimaGeracao(), g.getEncerradaEm(),
                contaPagarRepository.countByRecorrenciaId(g.getId()), proximas, eventos, g.getCreatedAt(), financeiroMapper.usuario(g.getCreatedBy()),
                g.getUpdatedAt(), financeiroMapper.usuario(g.getUpdatedBy()));
    }

    private void eventoTitulo(ContaPagar c, String evento, String detalhe, Usuario usuario) {
        ContaPagarEvento e = new ContaPagarEvento();
        e.setContaPagar(c);
        e.setEvento(evento);
        e.setDetalhe(limitar(detalhe));
        e.setUsuario(usuario);
        e.setDataHora(LocalDateTime.now());
        eventoRepository.save(e);
    }

    private void eventoRegra(ContaPagarRecorrencia g, String evento, String detalhe, Usuario usuario) {
        ContaPagarRecorrenciaEvento e = new ContaPagarRecorrenciaEvento();
        e.setRecorrencia(g);
        e.setEvento(evento);
        e.setDetalhe(limitar(detalhe));
        e.setUsuario(usuario);
        e.setDataHora(LocalDateTime.now());
        eventoRegraRepository.save(e);
    }

    private static String limitar(String detalhe) {
        return detalhe != null && detalhe.length() > 2000 ? detalhe.substring(0, 2000) : detalhe;
    }

    private static String nv(String valor) {
        return valor == null ? "(vazio)" : "\"" + valor + "\"";
    }

    private static String rotulo(PeriodicidadeRecorrencia p) {
        return p.name().toLowerCase(java.util.Locale.ROOT);
    }

    private Usuario usuarioAtual() {
        return auditor.getCurrentAuditor().orElse(null);
    }
}
