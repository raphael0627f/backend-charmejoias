package com.manager.app.financeiro;

import com.manager.app.cliente.Cliente;
import com.manager.app.cliente.ClienteRepository;
import com.manager.app.endereco.Endereco;
import com.manager.app.financeiro.dto.ContaReceberAcoesDTO;
import com.manager.app.financeiro.dto.ContaReceberCriacaoResponseDTO;
import com.manager.app.financeiro.dto.ContaReceberDetalheDTO;
import com.manager.app.financeiro.dto.ContaReceberListaResponseDTO;
import com.manager.app.financeiro.dto.ContaReceberRequestDTO;
import com.manager.app.financeiro.dto.ContaReceberResumoDTO;
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
import org.springframework.data.jpa.domain.Specification;
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

// núcleo do Contas a Receber: lançamento (simples/parcelado), lista com filtros e totais, detalhe, edição, vencimento e cancelamento.
// Cálculos puros em ContaReceberCalculo. Não há exclusão física: cancelar registra o motivo e o usuário. Recebimento é da fase 3.
@Service
@RequiredArgsConstructor
public class ContaReceberService {

    static final int TAMANHO_MAXIMO = 200;
    static final int LOTE_MAXIMO = 200;
    static final int LIMITE_CLIENTES = 20;
    private static final Map<String, String> ORDENACOES = Map.of("vencimento", "vencimento", "valor", "valor", "numero", "numero",
            "cliente", "cliente", "situacao", "situacao");

    private final ContaReceberRepository repository;
    private final ContaReceberEventoRepository eventoRepository;
    private final ClienteRepository clienteRepository;
    private final ContaReceberRecebimentoRepository recebimentoRepository;
    private final MovimentacaoBancoRepository movimentacaoRepository;
    private final ContaReceberAnexoRepository anexoRepository;
    private final CategoriaReceitaRepository categoriaRepository;
    private final CentroCustoRepository centroCustoRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final UsuarioAuditorAware auditor;
    private final ContaReceberMapper mapper;
    private final PlatformTransactionManager transactionManager;
    private final com.manager.app.infinitepay.LinkPagamentoCicloVida linkCicloVida;
    private final ExcedenteAcertoService excedenteAcertoService;
    private final DocumentoValoresService documentoValores;

    @PersistenceContext
    private EntityManager em;

    record Base(String descricao, Cliente cliente, ContaReceberCalculo.PagadorAvulso pagador, CategoriaReceita categoria, CentroCusto centroCusto, FormaPagamento forma,
                BigDecimal valor, String documento, String observacao) {
    }

    // ---------- criação ----------

    // origem de consignação do título (nula nos títulos comuns): consignação e pré-acerto OU acerto
    public record Origem(com.manager.app.consignacao.Consignacao consignacao, com.manager.app.preacerto.PreAcertoConsignacao preAcerto,
                         com.manager.app.acertoconsignacao.AcertoConsignacao acerto) {
    }

    public record Criados(UUID grupo, List<ContaReceber> titulos) {
    }

    @Transactional
    public ContaReceberCriacaoResponseDTO criar(ContaReceberRequestDTO r) {
        Criados criados = criarTitulos(r, null);
        LocalDate hoje = LocalDate.now();
        return new ContaReceberCriacaoResponseDTO(criados.grupo(), criados.titulos().stream().map(t -> mapper.resumo(t, hoje)).toList());
    }

    // criação simples ou parcelada, usada também pela geração a partir de pré-acerto/acerto (mesmas validações e mesma transação do chamador)
    @Transactional
    public Criados criarTitulos(ContaReceberRequestDTO r, Origem origem) {
        LocalDate hoje = LocalDate.now();
        Base base = validarBase(r);
        LocalDate emissao = r.emissao() == null ? hoje : r.emissao();
        String competenciaInformada = r.competencia() == null || r.competencia().isBlank() ? null : ContaReceberCalculo.validarCompetencia(r.competencia());
        Usuario usuario = usuarioAtual();
        List<ContaReceber> titulos = new ArrayList<>();
        UUID grupo = null;

        if (r.parcelamento() == null) {
            ContaReceberCalculo.validarVencimento(emissao, r.vencimento());
            titulos.add(novo(base, base.valor(), base.descricao(), emissao, competenciaInformada, r.vencimento(), null, null, null));
        } else {
            ContaReceberRequestDTO.Parcelamento p = r.parcelamento();
            ContaReceberCalculo.validarQuantidade(p.quantidade());
            int quantidade = p.quantidade();
            LocalDate primeiro = p.primeiroVencimento() != null ? p.primeiroVencimento() : r.vencimento();
            List<LocalDate> datas = ContaReceberCalculo.datas(p.intervalo(), primeiro, p.datas(), quantidade);
            List<BigDecimal> valores = p.valores() == null || p.valores().isEmpty()
                    ? ContaReceberCalculo.dividir(base.valor(), quantidade)
                    : ContaReceberCalculo.validarValores(p.valores(), base.valor(), quantidade);
            grupo = UUID.randomUUID();
            for (int i = 0; i < quantidade; i++) {
                ContaReceberCalculo.validarVencimento(emissao, datas.get(i));
                String descricao = ContaReceberCalculo.descricaoParcela(base.descricao(), i + 1, quantidade);
                if (descricao.length() > 200) {
                    throw new ItemInvalidoException("A descrição, acrescida do sufixo da parcela, excede 200 caracteres.");
                }
                titulos.add(novo(base, valores.get(i), descricao, emissao, competenciaInformada, datas.get(i), grupo, i + 1, quantidade));
            }
        }

        for (ContaReceber titulo : titulos) {
            if (origem != null) {
                titulo.setConsignacao(origem.consignacao());
                titulo.setPreAcerto(origem.preAcerto());
                titulo.setAcerto(origem.acerto());
            }
            titulo.setNumero(repository.proximoNumero());
            repository.save(titulo);
            registrar(titulo, "TITULO_CRIADO", detalheCriacao(titulo), usuario);
        }
        return new Criados(grupo, titulos);
    }

    // título de R$ 0,00 já quitado (acerto sem valor a pagar): sem banco, forma, movimentação nem recebimento financeiro; mesmas regras de numeração e histórico
    @Transactional
    public ContaReceber criarTituloZeradoDoAcerto(Cliente cliente, com.manager.app.consignacao.Consignacao consignacao,
                                                  com.manager.app.acertoconsignacao.AcertoConsignacao acerto, CategoriaReceita categoria, String descricao, LocalDate data) {
        Usuario usuario = usuarioAtual();
        ContaReceber c = new ContaReceber();
        c.setCliente(cliente);
        c.setConsignacao(consignacao);
        c.setAcerto(acerto);
        c.setDescricao(descricao);
        c.setCategoria(categoria);
        c.setValor(BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA));
        c.setValorRecebido(BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA));
        c.setEmissao(data);
        c.setVencimento(data);
        c.setCompetencia(ContaReceberCalculo.competenciaPadrao(data));
        c.setObservacao(DocumentoFinanceiroCalculo.OBSERVACAO_TITULO_ZERADO);
        c.setSituacao(SituacaoContaReceber.RECEBIDO);
        c.setNumero(repository.proximoNumero());
        repository.save(c);
        registrar(c, "TITULO_CRIADO", detalheCriacao(c), usuario);
        registrar(c, "RECEBIMENTO_AUTOMATICO", "Título sem valor a pagar quitado automaticamente em " + TextoUtils.data(data)
                + ". Não houve recebimento financeiro nem movimentação bancária.", usuario);
        return c;
    }

    // ajuste do título do pré-acerto na confirmação do acerto (único caminho que altera o valor de título de consignação): reduz o valor ou, sem recebimento
    // e totalmente reduzido, cancela a parcela. Registra evento próprio; links ativos deixam de valer.
    @Transactional
    public boolean ajustarTituloDoPreAcerto(ContaReceber c, BigDecimal novoValor, String contexto) {
        Usuario usuario = usuarioAtual();
        if (novoValor.signum() == 0) {
            c.setSituacao(SituacaoContaReceber.CANCELADO);
            c.setCancelamentoMotivo("Cancelado automaticamente na confirmação do acerto sem valor a pagar.");
            c.setCancelamentoEm(LocalDateTime.now());
            c.setCanceladoPor(usuario);
            repository.save(c);
            registrar(c, "TITULO_CANCELADO", "Cancelado automaticamente na confirmação do acerto sem valor a pagar (nada foi pago). " + contexto, usuario);
            return linkCicloVida.aoCancelar(c);
        }
        BigDecimal anterior = c.getValor();
        c.setValor(novoValor);
        c.setSituacao(ContaReceberCalculo.situacaoPara(novoValor, c.getValorRecebido()));
        repository.save(c);
        registrar(c, "VALOR_AJUSTADO_ACERTO", "Valor do título ajustado de " + MovimentacaoBancoService.moeda(anterior) + " para " + MovimentacaoBancoService.moeda(novoValor)
                + " na confirmação do acerto. " + contexto, usuario);
        if (linkCicloVida.aoAjustarValor(c)) registrar(c, "LINK_PAGAMENTO_NAO_UTILIZAR", "Links de pagamento ativos marcados como não utilizar: o valor do título foi ajustado.", usuario);
        linkCicloVida.aoQuitar(c);
        return false;
    }

    private static void exigirNaoZerado(ContaReceber c, String acao) {
        if (c.getAcerto() != null && DocumentoFinanceiroCalculo.tituloZerado(c.getValor())) {
            throw new ItemInvalidoException("O título de acerto sem valor a pagar é um registro automático e não pode ser " + acao + ".");
        }
    }

    private ContaReceber novo(Base b, BigDecimal valor, String descricao, LocalDate emissao, String competenciaInformada, LocalDate vencimento,
                              UUID grupo, Integer parcela, Integer total) {
        ContaReceber c = new ContaReceber();
        aplicarPagador(c, b);
        c.setDescricao(descricao);
        c.setCategoria(b.categoria());
        c.setCentroCusto(b.centroCusto());
        c.setFormaPrevista(b.forma());
        c.setValor(valor);
        c.setEmissao(emissao);
        c.setCompetencia(competenciaInformada != null ? competenciaInformada : ContaReceberCalculo.competenciaPadrao(vencimento));
        c.setVencimento(vencimento);
        c.setDocumento(b.documento());
        c.setObservacao(b.observacao());
        c.setSituacao(SituacaoContaReceber.ABERTO);
        c.setValorRecebido(BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA));
        c.setGrupoParcelamentoId(grupo);
        c.setParcelaNumero(parcela);
        c.setParcelaTotal(total);
        return c;
    }

    private String detalheCriacao(ContaReceber c) {
        String parcela = c.getParcelaNumero() == null ? "" : " (parcela " + c.getParcelaNumero() + "/" + c.getParcelaTotal() + ")";
        String paraQuem = c.getCliente() != null ? "o cliente " + c.getCliente().getNome() : "o pagador avulso " + c.getPagadorNome();
        return "Título criado para " + paraQuem + " no valor de " + MovimentacaoBancoService.moeda(c.getValor())
                + ", com vencimento em " + TextoUtils.data(c.getVencimento()) + parcela + "." + descricaoOrigem(c);
    }

    private static String descricaoOrigem(ContaReceber c) {
        if (c.getPreAcerto() == null && c.getAcerto() == null) return "";
        String documento = c.getPreAcerto() != null ? "pré-acerto nº " + c.getPreAcerto().getId() : "acerto nº " + c.getAcerto().getId();
        return " Origem: " + documento + (c.getConsignacao() == null ? "" : " da consignação nº " + c.getConsignacao().getId()) + ".";
    }

    // grava o cliente ou o pagador avulso (nunca os dois)
    private static void aplicarPagador(ContaReceber c, Base b) {
        c.setCliente(b.cliente());
        c.setPagadorNome(b.pagador() == null ? null : b.pagador().nome());
        c.setPagadorDocumento(b.pagador() == null ? null : b.pagador().documento());
    }

    private static String exibicao(Base b) {
        return ContaReceberCalculo.pagadorExibicao(b.cliente() == null ? null : b.cliente().getNome(), b.pagador() == null ? null : b.pagador().nome());
    }

    private static UUID idDe(Cliente c) {
        return c == null ? null : c.getId();
    }

    // ---------- consulta ----------

    @Transactional(readOnly = true)
    public ContaReceberListaResponseDTO listar(ContaReceberFiltro filtro, String ordenar, String direcao, int pagina, int tamanho) {
        if (pagina < 0) throw new ItemInvalidoException("A página deve ser maior ou igual a zero.");
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new ItemInvalidoException("O tamanho da página deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        }
        String campo = ORDENACOES.get(ordenar == null || ordenar.isBlank() ? "vencimento" : ordenar.trim().toLowerCase());
        if (campo == null) throw new ItemInvalidoException("Ordenação inválida. Use: vencimento, valor, numero, cliente ou situacao.");
        boolean desc = direcao != null && direcao.trim().equalsIgnoreCase("desc");
        if (direcao != null && !direcao.isBlank() && !desc && !direcao.trim().equalsIgnoreCase("asc")) {
            throw new ItemInvalidoException("Direção inválida. Use asc ou desc.");
        }
        if (filtro.origem() != null && !filtro.origem().isBlank()
                && !filtro.origem().trim().equalsIgnoreCase("PRE_ACERTO") && !filtro.origem().trim().equalsIgnoreCase("ACERTO")) {
            throw new ItemInvalidoException("Origem inválida. Use PRE_ACERTO ou ACERTO.");
        }
        if (filtro.tipoPagador() != null && !filtro.tipoPagador().isBlank()
                && !filtro.tipoPagador().trim().equalsIgnoreCase("CLIENTE") && !filtro.tipoPagador().trim().equalsIgnoreCase("AVULSO")) {
            throw new ItemInvalidoException("Tipo de pagador inválido. Use CLIENTE ou AVULSO.");
        }
        validarPeriodo("vencimento", filtro.vencimentoDe(), filtro.vencimentoAte());
        validarPeriodo("emissão", filtro.emissaoDe(), filtro.emissaoAte());
        validarPeriodo("recebimento", filtro.recebimentoDe(), filtro.recebimentoAte());
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
        var spec = ContaReceberEspecificacao.de(filtro, de, ate, hoje);
        Page<ContaReceber> resultado;
        if (campo.equals("cliente")) {
            // nome de exibição (cliente ou pagador avulso); a ordenação vai na própria consulta, com junção externa
            resultado = repository.findAll(ContaReceberEspecificacao.ordenadaPorPagador(spec, desc), PageRequest.of(pagina, tamanho));
        } else {
            Sort sort = Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, campo);
            if (!campo.equals("numero")) sort = sort.and(Sort.by(Sort.Direction.ASC, "numero"));
            resultado = repository.findAll(spec, PageRequest.of(pagina, tamanho, sort));
        }
        Map<Long, Integer> anexos = contagemAnexos(resultado.getContent().stream().map(ContaReceber::getId).toList());
        Map<Long, TotaisRecebimento> pagos = totaisPagos(resultado.getContent().stream().map(ContaReceber::getId).toList());
        List<ContaReceberResumoDTO> itens = resultado.getContent().stream()
                .map(c -> mapper.resumo(c, hoje, anexos.getOrDefault(c.getId(), 0), TotaisRecebimento.de(pagos, c.getId()))).toList();
        return new ContaReceberListaResponseDTO(itens, pagina, tamanho, resultado.getTotalElements(), resultado.getTotalPages(),
                new ContaReceberListaResponseDTO.Periodo(de, ate), totais(spec, hoje));
    }

    private ContaReceberListaResponseDTO.Totais totais(Specification<ContaReceber> spec, LocalDate hoje) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<ContaReceber> root = q.from(ContaReceber.class);
        Predicate where = spec.toPredicate(root, q, cb);
        Expression<BigDecimal> zero = cb.literal(BigDecimal.ZERO);
        Expression<BigDecimal> valor = root.get("valor");
        Expression<BigDecimal> recebido = root.get("valorRecebido");
        Predicate naoCancelado = cb.notEqual(root.get("situacao"), SituacaoContaReceber.CANCELADO);
        Predicate emAberto = root.get("situacao").in(SituacaoContaReceber.ABERTO, SituacaoContaReceber.RECEBIDO_PARCIAL);
        Predicate atrasado = cb.and(emAberto, cb.lessThan(root.get("vencimento"), hoje));
        Expression<BigDecimal> total = cb.<BigDecimal>selectCase().when(naoCancelado, valor).otherwise(zero);
        Expression<BigDecimal> aberto = cb.<BigDecimal>selectCase().when(emAberto, cb.diff(valor, recebido)).otherwise(zero);
        Expression<BigDecimal> vencido = cb.<BigDecimal>selectCase().when(atrasado, cb.diff(valor, recebido)).otherwise(zero);
        q.multiselect(cb.count(root), cb.sum(total), cb.sum(recebido), cb.sum(aberto), cb.sum(vencido)).where(where);
        Object[] l = em.createQuery(q).getSingleResult();
        BigDecimal[] pago = totalPagoDoFiltro(spec);
        return new ContaReceberListaResponseDTO.Totais(((Number) l[0]).longValue(), soma(l[1]), soma(l[2]), soma(l[3]), soma(l[4]), pago[0], pago[1]);
    }

    // "valor realmente pago" dos títulos do filtro: recebimentos não estornados (principal + juros + multa - desconto), com a parte paga por crédito do cliente
    private BigDecimal[] totalPagoDoFiltro(Specification<ContaReceber> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<ContaReceber> titulo = q.from(ContaReceber.class);
        Root<ContaReceberRecebimento> r = q.from(ContaReceberRecebimento.class);
        Expression<BigDecimal> zero = cb.literal(BigDecimal.ZERO);
        Expression<BigDecimal> credito = cb.<BigDecimal>selectCase().when(cb.equal(r.get("origem"), ContaReceberRecebimento.Origem.CREDITO_CLIENTE), r.<BigDecimal>get("valorTotal"))
                .otherwise(zero);
        q.multiselect(cb.sum(r.<BigDecimal>get("valorTotal")), cb.sum(credito))
                .where(cb.and(spec.toPredicate(titulo, q, cb), cb.equal(r.get("contaReceber"), titulo), cb.isFalse(r.get("estornada"))));
        Object[] l = em.createQuery(q).getSingleResult();
        return new BigDecimal[]{soma(l[0]), soma(l[1])};
    }

    private Map<Long, TotaisRecebimento> totaisPagos(List<Long> ids) {
        Map<Long, TotaisRecebimento> mapa = new java.util.HashMap<>();
        if (!ids.isEmpty()) recebimentoRepository.totaisPorTitulo(ids).forEach(t -> mapa.put(t.tituloId(), t));
        return mapa;
    }

    private static BigDecimal soma(Object o) {
        return o == null ? BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA) : CalculoFinanceiro.arredondar((BigDecimal) o);
    }

    @Transactional(readOnly = true)
    public ContaReceberDetalheDTO obter(Long id) {
        return detalhe(buscar(id));
    }

    public ContaReceberDetalheDTO detalhe(ContaReceber c) {
        List<ContaReceber> parcelas = c.getGrupoParcelamentoId() == null ? List.of()
                : repository.findByGrupoParcelamentoIdOrderByParcelaNumeroAscIdAsc(c.getGrupoParcelamentoId());
        List<Long> ids = new ArrayList<>(parcelas.stream().map(ContaReceber::getId).toList());
        ids.add(c.getId());
        List<ContaReceberRecebimento> recebimentos = recebimentoRepository.findByContaReceberIdOrderByIdAsc(c.getId());
        Map<Long, Long> movimentacoes = new java.util.HashMap<>();
        if (!recebimentos.isEmpty()) {
            movimentacaoRepository.findByRecebimentoIdIn(recebimentos.stream().map(ContaReceberRecebimento::getId).toList()).stream()
                    .filter(m -> m.getOrigem() == OrigemMovimentacao.RECEBIMENTO_CONTA_RECEBER).forEach(m -> movimentacoes.put(m.getRecebimentoId(), m.getId()));
        }
        return mapper.detalhe(c, LocalDate.now(), parcelas, eventoRepository.findByContaReceberIdOrderByIdAsc(c.getId()), recebimentos, movimentacoes,
                contagemAnexos(ids), anexoRepository.findByContaReceberIdOrderByIdAsc(c.getId()), totaisPagos(ids), documentoValores.origemDoTitulo(c));
    }

    private Map<Long, Integer> contagemAnexos(List<Long> ids) {
        Map<Long, Integer> mapa = new java.util.HashMap<>();
        if (!ids.isEmpty()) anexoRepository.contarPorTitulos(ids).forEach(l -> mapa.put((Long) l[0], ((Number) l[1]).intValue()));
        return mapa;
    }

    // seletor de clientes: ativos, por código, nome ou CPF/documento (contém, sem diferenciar caixa), até 20
    @Transactional(readOnly = true)
    public List<ContaReceberAcoesDTO.Cliente> buscarClientes(String busca) {
        StringBuilder jpql = new StringBuilder("select c from Cliente c where c.ativo = true");
        String padrao = null;
        String padraoDigitos = BuscaDocumento.padrao(busca);
        if (busca != null && !busca.isBlank()) {
            String termo = busca.trim().toLowerCase().replace("\u0000", "").replace("!", "!!").replace("%", "!%").replace("_", "!_");
            padrao = "%" + termo + "%";
            jpql.append(" and (lower(c.codigo) like :p escape '!' or lower(c.nome) like :p escape '!' or lower(c.cpf) like :p escape '!' or lower(c.documento) like :p escape '!'");
            if (padraoDigitos != null) jpql.append(" or c.cpf like :d or c.documento like :d");
            jpql.append(")");
        }
        jpql.append(" order by c.nome asc");
        var consulta = em.createQuery(jpql.toString(), Cliente.class).setMaxResults(LIMITE_CLIENTES);
        if (padrao != null) consulta.setParameter("p", padrao);
        if (padraoDigitos != null) consulta.setParameter("d", padraoDigitos);
        return consulta.getResultList().stream().map(c -> new ContaReceberAcoesDTO.Cliente(c.getId(), c.getCodigo(), c.getNome(),
                ContaReceberMapper.documentoDe(c), cidade(c))).toList();
    }

    private static String cidade(Cliente c) {
        Endereco e = c.getEnderecoPrincipal();
        if (e == null && !c.getEnderecos().isEmpty()) e = c.getEnderecos().get(0);
        return e == null ? null : e.getMunicipio();
    }

    // ---------- edição ----------

    @Transactional
    public ContaReceberDetalheDTO atualizar(Long id, ContaReceberRequestDTO r) {
        ContaReceber c = buscarComLock(id);
        if (c.getSituacao() == SituacaoContaReceber.CANCELADO) {
            throw new ItemInvalidoException("O título cancelado não pode ser editado.");
        }
        exigirNaoZerado(c, "editado");
        Base base = validarBase(r);
        LocalDate vencimento = r.vencimento() == null ? c.getVencimento() : r.vencimento();
        LocalDate emissao = r.emissao() == null ? c.getEmissao() : r.emissao();
        String competencia = r.competencia() == null || r.competencia().isBlank() ? c.getCompetencia() : ContaReceberCalculo.validarCompetencia(r.competencia());
        boolean trocouCliente = !Objects.equals(idDe(c.getCliente()), idDe(base.cliente()))
                || !Objects.equals(c.getPagadorNome(), base.pagador() == null ? null : base.pagador().nome())
                || !Objects.equals(c.getPagadorDocumento(), base.pagador() == null ? null : base.pagador().documento());

        List<String> alteracoes = new ArrayList<>();
        if (c.getSituacao() == SituacaoContaReceber.RECEBIDO) {
            boolean outrosCampos = !Objects.equals(c.getDescricao(), base.descricao()) || trocouCliente
                    || !c.getCategoria().getId().equals(base.categoria().getId())
                    || !Objects.equals(idDe(c.getCentroCusto()), idDe(base.centroCusto()))
                    || !Objects.equals(idDe(c.getFormaPrevista()), idDe(base.forma()))
                    || c.getValor().compareTo(base.valor()) != 0 || !c.getVencimento().equals(vencimento) || !c.getEmissao().equals(emissao)
                    || !Objects.equals(c.getCompetencia(), competencia) || !Objects.equals(c.getDocumento(), base.documento());
            if (outrosCampos) {
                throw new ItemInvalidoException("O título recebido permite alterar apenas a observação. Para alterar os demais dados, estorne o recebimento antes.");
            }
            diff(alteracoes, "observação", c.getObservacao(), base.observacao());
            c.setObservacao(base.observacao());
        } else {
            ContaReceberCalculo.validarVencimento(emissao, vencimento);
            ContaReceberCalculo.validarValorNaoAbaixoDoRecebido(base.valor(), c.getValorRecebido());
            ContaReceberCalculo.validarTrocaDeCliente(trocouCliente, c.getValorRecebido());
            ContaReceberCalculo.validarEdicaoDeTituloDeConsignacao(c.getPreAcerto() != null || c.getAcerto() != null,
                    c.getValor().compareTo(base.valor()) != 0 || trocouCliente);
            diff(alteracoes, "descrição", c.getDescricao(), base.descricao());
            diff(alteracoes, "pagador", ContaReceberMapper.pagadorExibicao(c), exibicao(base));
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
            diff(alteracoes, "observação", c.getObservacao(), base.observacao());

            c.setDescricao(base.descricao());
            aplicarPagador(c, base);
            c.setCategoria(base.categoria());
            c.setCentroCusto(base.centroCusto());
            c.setFormaPrevista(base.forma());
            c.setValor(base.valor());
            c.setEmissao(emissao);
            c.setCompetencia(competencia);
            c.setVencimento(vencimento);
            c.setDocumento(base.documento());
            c.setObservacao(base.observacao());
            c.setSituacao(ContaReceberCalculo.situacaoPara(c.getValor(), c.getValorRecebido()));
        }
        repository.save(c);
        if (!alteracoes.isEmpty()) registrar(c, "TITULO_EDITADO", String.join("; ", alteracoes) + ".", usuarioAtual());
        return detalhe(c);
    }

    @Transactional
    public ContaReceberDetalheDTO alterarVencimento(Long id, ContaReceberAcoesDTO.Vencimento r) {
        ContaReceber c = buscarComLock(id);
        ContaReceberCalculo.validarVencimentoAlteravel(c.getSituacao());
        String motivo = CalculoFinanceiro.validarMotivo(r.motivo());
        if (r.dataVencimento().equals(c.getVencimento())) {
            throw new ItemInvalidoException("O novo vencimento deve ser diferente do vencimento atual.");
        }
        ContaReceberCalculo.validarVencimento(c.getEmissao(), r.dataVencimento());
        LocalDate antigo = c.getVencimento();
        c.setVencimento(r.dataVencimento());
        repository.save(c);
        registrar(c, "VENCIMENTO_ALTERADO", "Vencimento alterado de " + TextoUtils.data(antigo) + " para " + TextoUtils.data(r.dataVencimento())
                + ". Motivo: " + motivo, usuarioAtual());
        return detalhe(c);
    }

    // ---------- cancelamento ----------

    @Transactional
    public ContaReceberDetalheDTO cancelar(Long id, ContaReceberAcoesDTO.Cancelar r) {
        Cancelado cancelado = cancelarTitulo(id, CalculoFinanceiro.validarMotivo(r == null ? null : r.motivo()));
        ContaReceberDetalheDTO detalhe = detalhe(cancelado.titulo());
        return cancelado.comLinkAtivo() ? detalhe.comAviso(com.manager.app.infinitepay.LinkPagamentoCicloVida.AVISO_CANCELAMENTO) : detalhe;
    }

    private record Cancelado(ContaReceber titulo, boolean comLinkAtivo) {
    }

    // o lote nunca falha inteiro: cada título é cancelado em sua própria transação e a resposta traz o resultado por id
    public ContaReceberAcoesDTO.ResultadoLote cancelarLote(ContaReceberAcoesDTO.CancelarLote r) {
        if (r == null || r.ids() == null || r.ids().isEmpty()) throw new ItemInvalidoException("Selecione ao menos um título.");
        if (r.ids().stream().anyMatch(Objects::isNull)) throw new ItemInvalidoException("A lista de títulos contém um identificador inválido.");
        if (r.ids().size() > LOTE_MAXIMO) throw new ItemInvalidoException("O cancelamento em lote aceita no máximo " + LOTE_MAXIMO + " títulos.");
        String motivo = CalculoFinanceiro.validarMotivo(r.motivo());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<ContaReceberAcoesDTO.Item> itens = new ArrayList<>();
        int cancelados = 0;
        int comLink = 0;
        for (Long id : new LinkedHashSet<>(r.ids())) {
            try {
                Cancelado cancelado = tx.execute(status -> cancelarTitulo(id, motivo));
                itens.add(new ContaReceberAcoesDTO.Item(id, cancelado.titulo().getNumero(), true, null));
                cancelados++;
                if (cancelado.comLinkAtivo()) comLink++;
            } catch (ItemInvalidoException | ObjetoNaoEncontradoException e) {
                itens.add(new ContaReceberAcoesDTO.Item(id, numeroOuNulo(id), false, e.getMessage()));
            }
        }
        return new ContaReceberAcoesDTO.ResultadoLote(cancelados, itens.size() - cancelados, itens, com.manager.app.infinitepay.LinkPagamentoCicloVida.avisoDoLote(comLink));
    }

    private Long numeroOuNulo(Long id) {
        return repository.findById(id).map(ContaReceber::getNumero).orElse(null);
    }

    private Cancelado cancelarTitulo(Long id, String motivo) {
        ContaReceber c = buscarComLock(id);
        ContaReceberCalculo.validarCancelavel(c.getSituacao(), c.getValorRecebido());
        exigirNaoZerado(c, "cancelado");
        excedenteAcertoService.bloquearSeDestinado(c, "cancelar o título");
        Usuario usuario = usuarioAtual();
        c.setSituacao(SituacaoContaReceber.CANCELADO);
        c.setCancelamentoMotivo(motivo);
        c.setCancelamentoEm(LocalDateTime.now());
        c.setCanceladoPor(usuario);
        repository.save(c);
        registrar(c, "TITULO_CANCELADO", "Título cancelado. Motivo: " + motivo, usuario);
        excedenteAcertoService.sincronizarPorTitulo(c);
        // links de pagamento ativos deste título passam a TITULO_CANCELADO (a InfinitePay não permite cancelá-los lá)
        boolean comLink = linkCicloVida.aoCancelar(c);
        return new Cancelado(c, comLink);
    }

    // ---------- apoio ----------

    public ContaReceber buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Título não encontrado."));
    }

    // bloqueia a linha do título (SELECT ... FOR UPDATE) até o fim da transação
    public ContaReceber buscarComLock(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Título não encontrado."));
    }

    Base validarBase(ContaReceberRequestDTO r) {
        String descricao = r.descricao() == null ? "" : r.descricao().trim();
        if (descricao.isEmpty()) throw new ItemInvalidoException("A descrição é obrigatória.");
        TextoUtils.validarTexto(descricao, "A descrição", 200);
        TextoUtils.validarTexto(r.documento(), "O documento", 60);
        TextoUtils.validarTexto(r.observacao(), "A observação", 1000);
        BigDecimal valor = CalculoFinanceiro.validarValorPositivo(r.valor());
        ContaReceberCalculo.PagadorAvulso pagador = ContaReceberCalculo.validarPagador(r.clienteId() != null, r.pagadorNome(), r.pagadorDocumento());
        Cliente cliente = null;
        if (r.clienteId() != null) {
            cliente = clienteRepository.findById(r.clienteId())
                    .orElseThrow(() -> new ItemInvalidoException("O cliente informado não foi encontrado."));
            if (!cliente.isAtivo()) throw new ItemInvalidoException("O cliente \"" + cliente.getNome() + "\" está desativado.");
        }
        CategoriaReceita categoria = categoriaRepository.findById(r.categoriaId())
                .orElseThrow(() -> new ItemInvalidoException("A categoria informada não foi encontrada."));
        if (!categoria.isAtivo()) throw new ItemInvalidoException("A categoria \"" + categoria.getNome() + "\" está desativada.");
        CentroCusto centro = null;
        if (r.centroCustoId() != null) {
            centro = centroCustoRepository.findById(r.centroCustoId()).orElseThrow(() -> new ItemInvalidoException("O centro de custo informado não foi encontrado."));
            if (!centro.isAtivo()) throw new ItemInvalidoException("O centro de custo \"" + centro.getNome() + "\" está desativado.");
        }
        FormaPagamento forma = null;
        if (r.formaPrevistaId() != null) {
            forma = formaPagamentoRepository.findById(r.formaPrevistaId()).orElseThrow(() -> new ItemInvalidoException("A forma de pagamento informada não foi encontrada."));
            if (!forma.isAtivo()) throw new ItemInvalidoException("A forma de pagamento \"" + forma.getNome() + "\" está desativada.");
        }
        return new Base(descricao, cliente, pagador, categoria, centro, forma, valor, BancoService.vazioParaNulo(r.documento()),
                BancoService.vazioParaNulo(r.observacao()));
    }

    private void registrar(ContaReceber c, String evento, String detalhe, Usuario usuario) {
        ContaReceberEvento e = new ContaReceberEvento();
        e.setContaReceber(c);
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
