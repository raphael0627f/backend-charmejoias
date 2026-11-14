package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.ContaPagarAcoesDTO;
import com.manager.app.financeiro.dto.ContaPagarCriacaoResponseDTO;
import com.manager.app.financeiro.dto.ContaPagarDetalheDTO;
import com.manager.app.financeiro.dto.ContaPagarListaResponseDTO;
import com.manager.app.financeiro.dto.ContaPagarRequestDTO;
import com.manager.app.financeiro.dto.ContaPagarResumoDTO;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.TextoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.usuario.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// núcleo do Contas a Pagar: lançamento (simples/parcelado), lista com filtros e totais, detalhe, edição, vencimento e cancelamento.
// Cálculos puros em ContaPagarCalculo/LinhaDigitavel. Não há exclusão física: cancelar registra o motivo e o usuário.
@Service
@RequiredArgsConstructor
public class ContaPagarService {

    static final int TAMANHO_MAXIMO = 200;
    static final int LOTE_MAXIMO = 200;
    private static final int LIMITE_FAVORECIDOS = 10;
    private static final Map<String, String> ORDENACOES = Map.of("vencimento", "vencimento", "valor", "valor", "numero", "numero",
            "favorecido", "favorecido", "situacao", "situacao");

    private final ContaPagarRepository repository;
    private final ContaPagarEventoRepository eventoRepository;
    private final ContaPagarBaixaRepository baixaRepository;
    private final ContaPagarAnexoRepository anexoRepository;
    private final ContaPagarRecorrenciaService recorrenciaService;
    private final MovimentacaoBancoRepository movimentacaoRepository;
    private final CategoriaDespesaRepository categoriaRepository;
    private final CentroCustoRepository centroCustoRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final UsuarioAuditorAware auditor;
    private final ContaPagarMapper mapper;
    private final PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager em;

    record Base(String descricao, String favorecido, CategoriaDespesa categoria, CentroCusto centroCusto, FormaPagamento forma,
                        BigDecimal valor, String documento, String observacao) {
    }

    // ---------- criação ----------

    @Transactional
    public ContaPagarCriacaoResponseDTO criar(ContaPagarRequestDTO r) {
        LocalDate hoje = LocalDate.now();
        Base base = validarBase(r);
        LocalDate emissao = r.emissao() == null ? hoje : r.emissao();
        String competenciaInformada = r.competencia() == null || r.competencia().isBlank() ? null : ContaPagarCalculo.validarCompetencia(r.competencia());
        Usuario usuario = usuarioAtual();
        List<ContaPagar> titulos = new ArrayList<>();
        UUID grupo = null;

        if (r.recorrencia() != null) {
            return recorrenciaService.criar(base, r, hoje, emissao);
        }
        if (r.parcelamento() == null) {
            if (r.vencimento() == null) throw new ItemInvalidoException("O vencimento é obrigatório.");
            String linha = LinhaDigitavel.normalizar(r.linhaDigitavel());
            titulos.add(novo(base, base.valor(), base.descricao(), emissao, competenciaInformada, r.vencimento(), linha, null, null, null));
        } else {
            ContaPagarRequestDTO.Parcelamento p = r.parcelamento();
            ContaPagarCalculo.validarQuantidade(p.quantidade());
            int quantidade = p.quantidade();
            if (r.linhaDigitavel() != null && !r.linhaDigitavel().isBlank()) {
                throw new ItemInvalidoException("A linha digitável não pode ser informada em título parcelado. Informe-a individualmente em cada título.");
            }
            LocalDate primeiro = p.primeiroVencimento() != null ? p.primeiroVencimento() : r.vencimento();
            List<LocalDate> datas = ContaPagarCalculo.datas(p.intervalo(), primeiro, p.datas(), quantidade);
            List<BigDecimal> valores = p.valores() == null || p.valores().isEmpty()
                    ? ContaPagarCalculo.dividir(base.valor(), quantidade)
                    : ContaPagarCalculo.validarValores(p.valores(), base.valor(), quantidade);
            grupo = UUID.randomUUID();
            for (int i = 0; i < quantidade; i++) {
                String descricao = ContaPagarCalculo.descricaoParcela(base.descricao(), i + 1, quantidade);
                if (descricao.length() > 200) {
                    throw new ItemInvalidoException("A descrição, acrescida do sufixo da parcela, excede 200 caracteres.");
                }
                titulos.add(novo(base, valores.get(i), descricao, emissao, competenciaInformada, datas.get(i), null, grupo, i + 1, quantidade));
            }
        }

        List<ContaPagarResumoDTO> resposta = new ArrayList<>();
        for (ContaPagar titulo : titulos) {
            titulo.setNumero(repository.proximoNumero());
            repository.save(titulo);
            registrar(titulo, "CRIACAO", detalheCriacao(titulo), usuario);
            resposta.add(mapper.resumo(titulo, hoje));
        }
        return new ContaPagarCriacaoResponseDTO(grupo, null, resposta);
    }

    private ContaPagar novo(Base b, BigDecimal valor, String descricao, LocalDate emissao, String competenciaInformada, LocalDate vencimento,
                            String linha, UUID grupo, Integer parcela, Integer total) {
        ContaPagar c = new ContaPagar();
        c.setDescricao(descricao);
        c.setFavorecido(b.favorecido());
        c.setCategoria(b.categoria());
        c.setCentroCusto(b.centroCusto());
        c.setFormaPrevista(b.forma());
        c.setValor(valor);
        c.setEmissao(emissao);
        c.setCompetencia(competenciaInformada != null ? competenciaInformada : ContaPagarCalculo.competenciaPadrao(vencimento));
        c.setVencimento(vencimento);
        c.setDocumento(b.documento());
        c.setLinhaDigitavel(linha);
        c.setObservacao(b.observacao());
        c.setSituacao(SituacaoContaPagar.ABERTO);
        c.setValorPago(BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA));
        c.setGrupoParcelamentoId(grupo);
        c.setParcelaNumero(parcela);
        c.setParcelaTotal(total);
        return c;
    }

    private String detalheCriacao(ContaPagar c) {
        String parcela = c.getParcelaNumero() == null ? "" : " (parcela " + c.getParcelaNumero() + "/" + c.getParcelaTotal() + ")";
        return "Título criado no valor de " + MovimentacaoBancoService.moeda(c.getValor()) + ", com vencimento em "
                + TextoUtils.data(c.getVencimento()) + parcela + ".";
    }

    // ---------- consulta ----------

    @Transactional(readOnly = true)
    public ContaPagarListaResponseDTO listar(ContaPagarFiltro filtro, String ordenar, String direcao, int pagina, int tamanho) {
        if (pagina < 0) throw new ItemInvalidoException("A página deve ser maior ou igual a zero.");
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new ItemInvalidoException("O tamanho da página deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        }
        String campo = ORDENACOES.get(ordenar == null || ordenar.isBlank() ? "vencimento" : ordenar.trim().toLowerCase());
        if (campo == null) throw new ItemInvalidoException("Ordenação inválida. Use: vencimento, valor, numero, favorecido ou situacao.");
        boolean desc = direcao != null && direcao.trim().equalsIgnoreCase("desc");
        if (direcao != null && !direcao.isBlank() && !desc && !direcao.trim().equalsIgnoreCase("asc")) {
            throw new ItemInvalidoException("Direção inválida. Use asc ou desc.");
        }
        validarPeriodo("vencimento", filtro.vencimentoDe(), filtro.vencimentoAte());
        validarPeriodo("emissão", filtro.emissaoDe(), filtro.emissaoAte());
        validarPeriodo("pagamento", filtro.pagamentoDe(), filtro.pagamentoAte());
        if (filtro.valorMin() != null && filtro.valorMax() != null && filtro.valorMin().compareTo(filtro.valorMax()) > 0) {
            throw new ItemInvalidoException("O valor mínimo não pode ser maior que o valor máximo.");
        }

        LocalDate hoje = LocalDate.now();
        LocalDate de = filtro.vencimentoDe();
        LocalDate ate = filtro.vencimentoAte();
        if (!filtro.temFiltroDeData() && !filtro.temBusca() && !filtro.temSituacao()) {
            YearMonth mes = YearMonth.from(hoje);
            de = mes.atDay(1);
            ate = mes.atEndOfMonth();
        }
        var spec = ContaPagarEspecificacao.de(filtro, de, ate, hoje);
        Sort sort = Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, campo);
        if (!campo.equals("numero")) sort = sort.and(Sort.by(Sort.Direction.ASC, "numero"));
        Page<ContaPagar> resultado = repository.findAll(spec, PageRequest.of(pagina, tamanho, sort));
        Map<Long, Integer> anexos = contagemAnexos(resultado.getContent().stream().map(ContaPagar::getId).toList());
        List<ContaPagarResumoDTO> itens = resultado.getContent().stream().map(c -> mapper.resumo(c, hoje, anexos.getOrDefault(c.getId(), 0))).toList();
        return new ContaPagarListaResponseDTO(itens, pagina, tamanho, resultado.getTotalElements(), resultado.getTotalPages(),
                new ContaPagarListaResponseDTO.Periodo(de, ate), totais(spec, hoje));
    }

    private ContaPagarListaResponseDTO.Totais totais(org.springframework.data.jpa.domain.Specification<ContaPagar> spec, LocalDate hoje) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<ContaPagar> root = q.from(ContaPagar.class);
        Predicate where = spec.toPredicate(root, q, cb);
        Expression<BigDecimal> zero = cb.literal(BigDecimal.ZERO);
        Expression<BigDecimal> valor = root.get("valor");
        Expression<BigDecimal> pago = root.get("valorPago");
        Predicate naoCancelado = cb.notEqual(root.get("situacao"), SituacaoContaPagar.CANCELADO);
        Predicate emAberto = root.get("situacao").in(SituacaoContaPagar.ABERTO, SituacaoContaPagar.PAGO_PARCIAL);
        Predicate atrasado = cb.and(emAberto, cb.lessThan(root.get("vencimento"), hoje));
        Expression<BigDecimal> total = cb.<BigDecimal>selectCase().when(naoCancelado, valor).otherwise(zero);
        Expression<BigDecimal> aberto = cb.<BigDecimal>selectCase().when(emAberto, cb.diff(valor, pago)).otherwise(zero);
        Expression<BigDecimal> vencido = cb.<BigDecimal>selectCase().when(atrasado, cb.diff(valor, pago)).otherwise(zero);
        q.multiselect(cb.count(root), cb.sum(total), cb.sum(pago), cb.sum(aberto), cb.sum(vencido)).where(where);
        Object[] linha = em.createQuery(q).getSingleResult();
        return new ListaTotais(linha).paraDto();
    }

    private record ListaTotais(Object[] linha) {
        ContaPagarListaResponseDTO.Totais paraDto() {
            return new ContaPagarListaResponseDTO.Totais(((Number) linha[0]).longValue(), valor(linha[1]), valor(linha[2]), valor(linha[3]), valor(linha[4]));
        }

        private static BigDecimal valor(Object o) {
            return o == null ? BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA) : CalculoFinanceiro.arredondar((BigDecimal) o);
        }
    }

    @Transactional(readOnly = true)
    public ContaPagarDetalheDTO obter(Long id) {
        return detalhe(buscar(id));
    }

    public ContaPagarDetalheDTO detalhe(ContaPagar c) {
        List<ContaPagar> parcelas = c.getGrupoParcelamentoId() == null ? List.of()
                : repository.findByGrupoParcelamentoIdOrderByParcelaNumeroAscIdAsc(c.getGrupoParcelamentoId());
        List<ContaPagarBaixa> baixas = baixaRepository.findByContaPagarIdOrderByIdAsc(c.getId());
        Map<Long, Long> movimentacoes = new java.util.HashMap<>();
        if (!baixas.isEmpty()) {
            movimentacaoRepository.findByBaixaIdIn(baixas.stream().map(ContaPagarBaixa::getId).toList()).stream()
                    .filter(m -> m.getOrigem() == OrigemMovimentacao.BAIXA_CONTA_PAGAR).forEach(m -> movimentacoes.put(m.getBaixaId(), m.getId()));
        }
        List<Long> ids = new java.util.ArrayList<>(parcelas.stream().map(ContaPagar::getId).toList());
        ids.add(c.getId());
        return mapper.detalhe(c, LocalDate.now(), baixas, parcelas, eventoRepository.findByContaPagarIdOrderByIdAsc(c.getId()), movimentacoes,
                contagemAnexos(ids), anexoRepository.findByContaPagarIdOrderByIdAsc(c.getId()), recorrenciaService.resumoDoTitulo(c));
    }

    private Map<Long, Integer> contagemAnexos(List<Long> ids) {
        Map<Long, Integer> mapa = new java.util.HashMap<>();
        if (!ids.isEmpty()) anexoRepository.contarPorTitulos(ids).forEach(l -> mapa.put((Long) l[0], ((Number) l[1]).intValue()));
        return mapa;
    }

    @Transactional(readOnly = true)
    public List<String> favorecidos(String busca) {
        String padrao = "%";
        if (busca != null && !busca.isBlank()) {
            String termo = busca.trim().toLowerCase().replace("\u0000", "").replace("!", "!!").replace("%", "!%").replace("_", "!_");
            padrao = "%" + termo + "%";
        }
        return repository.favorecidos(padrao, PageRequest.of(0, LIMITE_FAVORECIDOS)).stream().map(l -> (String) l[0]).toList();
    }

    // ---------- edição ----------

    @Transactional
    public ContaPagarDetalheDTO atualizar(Long id, ContaPagarRequestDTO r) {
        ContaPagar c = buscarComLock(id);
        if (c.getSituacao() == SituacaoContaPagar.CANCELADO) {
            throw new ItemInvalidoException("O título cancelado não pode ser editado.");
        }
        Base base = validarBase(r);
        LocalDate vencimento = r.vencimento() == null ? c.getVencimento() : r.vencimento();
        LocalDate emissao = r.emissao() == null ? c.getEmissao() : r.emissao();
        String competencia = r.competencia() == null || r.competencia().isBlank() ? c.getCompetencia() : ContaPagarCalculo.validarCompetencia(r.competencia());
        String linha = LinhaDigitavel.normalizar(r.linhaDigitavel());

        List<String> alteracoes = new ArrayList<>();
        if (c.getSituacao() == SituacaoContaPagar.PAGO) {
            boolean outrosCampos = !Objects.equals(c.getDescricao(), base.descricao()) || !Objects.equals(c.getFavorecido(), base.favorecido())
                    || !c.getCategoria().getId().equals(base.categoria().getId())
                    || !Objects.equals(idDe(c.getCentroCusto()), idDe(base.centroCusto()))
                    || !Objects.equals(idDe(c.getFormaPrevista()), idDe(base.forma()))
                    || c.getValor().compareTo(base.valor()) != 0 || !c.getVencimento().equals(vencimento) || !c.getEmissao().equals(emissao)
                    || !Objects.equals(c.getCompetencia(), competencia) || !Objects.equals(c.getDocumento(), base.documento())
                    || !Objects.equals(c.getLinhaDigitavel(), linha);
            if (outrosCampos) {
                throw new ItemInvalidoException("O título pago permite alterar apenas a observação. Para alterar os demais dados, estorne a baixa antes.");
            }
            diff(alteracoes, "observação", c.getObservacao(), base.observacao());
            c.setObservacao(base.observacao());
        } else {
            if (base.valor().compareTo(CalculoFinanceiro.arredondar(c.getValorPago())) < 0) {
                throw new ItemInvalidoException("O valor do título não pode ser inferior ao valor já pago ("
                        + MovimentacaoBancoService.moeda(c.getValorPago()) + ").");
            }
            diff(alteracoes, "descrição", c.getDescricao(), base.descricao());
            diff(alteracoes, "favorecido", c.getFavorecido(), base.favorecido());
            diff(alteracoes, "categoria", c.getCategoria().getNome(), base.categoria().getNome());
            diff(alteracoes, "centro de custo", nomeDe(c.getCentroCusto()), nomeDe(base.centroCusto()));
            diff(alteracoes, "forma prevista", nomeDe(c.getFormaPrevista()), nomeDe(base.forma()));
            if (c.getValor().compareTo(base.valor()) != 0) {
                alteracoes.add("valor: " + MovimentacaoBancoService.moeda(c.getValor()) + " → " + MovimentacaoBancoService.moeda(base.valor()));
            }
            if (!c.getEmissao().equals(emissao)) alteracoes.add("emissão: " + TextoUtils.data(c.getEmissao()) + " → " + TextoUtils.data(emissao));
            diff(alteracoes, "competência", c.getCompetencia(), competencia);
            if (!c.getVencimento().equals(vencimento)) {
                alteracoes.add("vencimento: " + TextoUtils.data(c.getVencimento()) + " → " + TextoUtils.data(vencimento));
            }
            diff(alteracoes, "documento", c.getDocumento(), base.documento());
            diff(alteracoes, "linha digitável", c.getLinhaDigitavel(), linha);
            diff(alteracoes, "observação", c.getObservacao(), base.observacao());

            c.setDescricao(base.descricao());
            c.setFavorecido(base.favorecido());
            c.setCategoria(base.categoria());
            c.setCentroCusto(base.centroCusto());
            c.setFormaPrevista(base.forma());
            c.setValor(base.valor());
            c.setEmissao(emissao);
            c.setCompetencia(competencia);
            c.setVencimento(vencimento);
            c.setDocumento(base.documento());
            c.setLinhaDigitavel(linha);
            c.setObservacao(base.observacao());
            c.setSituacao(ContaPagarCalculo.situacaoPara(c.getValor(), c.getValorPago()));
        }
        repository.save(c);
        if (!alteracoes.isEmpty()) registrar(c, "EDICAO", String.join("; ", alteracoes) + ".", usuarioAtual());
        return detalhe(c);
    }

    @Transactional
    public ContaPagarDetalheDTO alterarVencimento(Long id, ContaPagarAcoesDTO.Vencimento r) {
        ContaPagar c = buscarComLock(id);
        if (c.getSituacao() == SituacaoContaPagar.PAGO || c.getSituacao() == SituacaoContaPagar.CANCELADO) {
            throw new ItemInvalidoException("Não é possível alterar o vencimento de um título pago ou cancelado.");
        }
        String motivo = CalculoFinanceiro.validarMotivo(r.motivo());
        if (r.novoVencimento().equals(c.getVencimento())) {
            throw new ItemInvalidoException("O novo vencimento deve ser diferente do vencimento atual.");
        }
        LocalDate antigo = c.getVencimento();
        c.setVencimento(r.novoVencimento());
        repository.save(c);
        registrar(c, "VENCIMENTO_ALTERADO", "Vencimento alterado de " + TextoUtils.data(antigo) + " para " + TextoUtils.data(r.novoVencimento())
                + ". Motivo: " + motivo, usuarioAtual());
        return detalhe(c);
    }

    // ---------- cancelamento ----------

    @Transactional
    public ContaPagarDetalheDTO cancelar(Long id, ContaPagarAcoesDTO.Cancelar r) {
        ContaPagar c = cancelarTitulo(id, CalculoFinanceiro.validarMotivo(r == null ? null : r.motivo()));
        return detalhe(c);
    }

    // o lote nunca falha inteiro: cada título é cancelado em sua própria transação e a resposta traz o resultado por id
    public ContaPagarAcoesDTO.ResultadoLote cancelarLote(ContaPagarAcoesDTO.CancelarLote r) {
        if (r == null || r.ids() == null || r.ids().isEmpty()) throw new ItemInvalidoException("Selecione ao menos um título.");
        if (r.ids().stream().anyMatch(Objects::isNull)) throw new ItemInvalidoException("A lista de títulos contém um identificador inválido.");
        if (r.ids().size() > LOTE_MAXIMO) throw new ItemInvalidoException("O cancelamento em lote aceita no máximo " + LOTE_MAXIMO + " títulos.");
        String motivo = CalculoFinanceiro.validarMotivo(r.motivo());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<ContaPagarAcoesDTO.Item> itens = new ArrayList<>();
        int cancelados = 0;
        for (Long id : new LinkedHashSet<>(r.ids())) {
            try {
                Long numero = tx.execute(status -> cancelarTitulo(id, motivo).getNumero());
                itens.add(new ContaPagarAcoesDTO.Item(id, numero, true, null));
                cancelados++;
            } catch (ItemInvalidoException | ObjetoNaoEncontradoException e) {
                itens.add(new ContaPagarAcoesDTO.Item(id, numeroOuNulo(id), false, e.getMessage()));
            }
        }
        return new ContaPagarAcoesDTO.ResultadoLote(cancelados, itens.size() - cancelados, itens);
    }

    private Long numeroOuNulo(Long id) {
        return repository.findById(id).map(ContaPagar::getNumero).orElse(null);
    }

    private ContaPagar cancelarTitulo(Long id, String motivo) {
        ContaPagar c = buscarComLock(id);
        if (c.getSituacao() == SituacaoContaPagar.CANCELADO) throw new ItemInvalidoException("O título já está cancelado.");
        if (baixaRepository.existsByContaPagarIdAndEstornadaFalse(id)) {
            throw new ItemInvalidoException("O título possui baixas ativas. Estorne as baixas antes de cancelar.");
        }
        Usuario usuario = usuarioAtual();
        c.setSituacao(SituacaoContaPagar.CANCELADO);
        c.setCancelamentoMotivo(motivo);
        c.setCancelamentoEm(LocalDateTime.now());
        c.setCanceladoPor(usuario);
        repository.save(c);
        registrar(c, "CANCELAMENTO", "Título cancelado. Motivo: " + motivo, usuario);
        return c;
    }

    // ---------- apoio ----------

    public ContaPagar buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Título não encontrado."));
    }

    // bloqueia a linha do título (SELECT ... FOR UPDATE) até o fim da transação: usado por quem altera o título ou o seu valor pago
    public ContaPagar buscarComLock(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Título não encontrado."));
    }

    Base validarBase(ContaPagarRequestDTO r) {
        String descricao = r.descricao() == null ? "" : r.descricao().trim();
        if (descricao.isEmpty()) throw new ItemInvalidoException("A descrição é obrigatória.");
        TextoUtils.validarTexto(descricao, "A descrição", 200);
        TextoUtils.validarTexto(r.favorecido(), "O favorecido", 150);
        TextoUtils.validarTexto(r.documento(), "O documento", 60);
        TextoUtils.validarTexto(r.observacao(), "A observação", 1000);
        BigDecimal valor = CalculoFinanceiro.validarValorPositivo(r.valor());
        CategoriaDespesa categoria = categoriaRepository.findById(r.categoriaId())
                .orElseThrow(() -> new ItemInvalidoException("A categoria informada não foi encontrada."));
        if (!categoria.isAtivo()) throw new ItemInvalidoException("A categoria \"" + categoria.getNome() + "\" está inativa.");
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
        return new Base(descricao, BancoService.vazioParaNulo(r.favorecido()), categoria, centro, forma, valor,
                BancoService.vazioParaNulo(r.documento()), BancoService.vazioParaNulo(r.observacao()));
    }

    private void registrar(ContaPagar c, String evento, String detalhe, Usuario usuario) {
        ContaPagarEvento e = new ContaPagarEvento();
        e.setContaPagar(c);
        e.setEvento(evento);
        e.setDetalhe(detalhe != null && detalhe.length() > 2000 ? detalhe.substring(0, 2000) : detalhe);
        e.setUsuario(usuario);
        e.setDataHora(LocalDateTime.now());
        eventoRepository.save(e);
    }

    private static void validarPeriodo(String nome, LocalDate de, LocalDate ate) {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new ItemInvalidoException("A data inicial de " + nome + " não pode ser posterior à data final.");
        }
    }

    private static void diff(List<String> alteracoes, String campo, String antes, String depois) {
        if (!Objects.equals(antes, depois)) alteracoes.add(campo + ": " + texto(antes) + " → " + texto(depois));
    }

    private static String texto(String valor) {
        if (valor == null || valor.isEmpty()) return "(vazio)";
        return "\"" + (valor.length() > 80 ? valor.substring(0, 80) + "..." : valor) + "\"";
    }

    private static Long idDe(CentroCusto c) {
        return c == null ? null : c.getId();
    }

    private static Long idDe(FormaPagamento f) {
        return f == null ? null : f.getId();
    }

    private static String nomeDe(CentroCusto c) {
        return c == null ? null : c.getNome();
    }

    private static String nomeDe(FormaPagamento f) {
        return f == null ? null : f.getNome();
    }

    private Usuario usuarioAtual() {
        return auditor.getCurrentAuditor().orElse(null);
    }
}
