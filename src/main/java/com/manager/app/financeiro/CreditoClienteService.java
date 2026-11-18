package com.manager.app.financeiro;

import com.manager.app.cliente.Cliente;
import com.manager.app.cliente.ClienteRepository;
import com.manager.app.financeiro.dto.CreditoClienteDTOs;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.usuario.Usuario;
import com.manager.app.usuario.UsuarioResumoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// crédito do cliente (fase 2): lotes, extrato append-only, destino CRÉDITO do excedente do acerto, desfazer e lançamento manual (ajuste sem banco, com motivo).
// Crédito não tem validade. Ordem de trava: título (fase 3) -> lotes do cliente -> excedente. Aqui só há excedente e lotes, sempre nessa ordem relativa.
@Service
@RequiredArgsConstructor
public class CreditoClienteService {

    private final CreditoClienteLoteRepository loteRepository;
    private final CreditoClienteMovimentoRepository movimentoRepository;
    private final ExcedenteAcertoRepository excedenteRepository;
    private final ExcedenteAcertoEventoRepository excedenteEventoRepository;
    private final ClienteRepository clienteRepository;
    private final ContaPagarService contaPagarService;
    private final ContaPagarRepository contaPagarRepository;
    private final UsuarioAuditorAware auditor;
    private final UsuarioResumoMapper usuarioMapper;

    // ---------- destino do excedente ----------

    @Transactional
    public void destinar(Long excedenteId, String destino, java.time.LocalDate vencimentoDevolucao) {
        String d = destino == null ? "" : destino.trim().toUpperCase();
        if (!d.equals("CREDITO") && !d.equals("DEVOLUCAO")) throw new ItemInvalidoException("Destino inválido. Use CREDITO ou DEVOLUCAO.");
        if (d.equals("DEVOLUCAO")) exigirAutoridade("CONTA_PAGAR_GERENCIAR");
        ExcedenteAcerto e = excedenteRepository.findByIdForUpdate(excedenteId).orElseThrow(() -> new ObjetoNaoEncontradoException("Excedente não encontrado."));
        if (e.getSituacao() != ExcedenteAcerto.Situacao.A_DESTINAR) {
            throw new ItemInvalidoException("Somente excedente a destinar pode receber destino. Situação atual: " + e.getSituacao().name() + ".");
        }
        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        if (d.equals("DEVOLUCAO")) {
            ContaPagar titulo = contaPagarService.criarDevolucao(e, vencimentoDevolucao, usuario);
            e.setSituacao(ExcedenteAcerto.Situacao.DEVOLUCAO);
            e.setDestino(ExcedenteAcerto.Destino.DEVOLUCAO);
            e.setDestinadoEm(LocalDateTime.now());
            e.setDestinadoPor(usuario);
            e.setUpdatedAt(LocalDateTime.now());
            excedenteRepository.save(e);
            evento(e, "EXCEDENTE_DESTINADO_DEVOLUCAO", "Excedente de " + MovimentacaoBancoService.moeda(e.getValor()) + " destinado como devolução ao cliente (título a pagar nº "
                    + titulo.getNumero() + ", vencimento em " + com.manager.app.shared.TextoUtils.data(titulo.getVencimento()) + ").", usuario);
            return;
        }
        CreditoClienteLote lote = new CreditoClienteLote();
        lote.setCliente(e.getCliente());
        lote.setOrigemTipo(CreditoClienteLote.Origem.EXCEDENTE_ACERTO);
        lote.setOrigemId(e.getId());
        lote.setValorOriginal(CalculoFinanceiro.arredondar(e.getValor()));
        lote.setCreatedBy(usuario);
        loteRepository.save(lote);
        movimento(lote, CreditoClienteMovimento.Tipo.ENTRADA, lote.getValorOriginal(), "Excedente do acerto nº " + e.getAcerto().getId() + " destinado como crédito.", usuario);
        e.setSituacao(ExcedenteAcerto.Situacao.CREDITO);
        e.setDestino(ExcedenteAcerto.Destino.CREDITO);
        e.setDestinadoEm(LocalDateTime.now());
        e.setDestinadoPor(usuario);
        e.setUpdatedAt(LocalDateTime.now());
        excedenteRepository.save(e);
        evento(e, "EXCEDENTE_DESTINADO_CREDITO", "Excedente de " + MovimentacaoBancoService.moeda(lote.getValorOriginal()) + " destinado como crédito do cliente (lote nº " + lote.getId() + ").", usuario);
    }

    @Transactional
    public void desfazerDestino(Long excedenteId, String motivo) {
        // devolução: trava o título a pagar (e dentro dele o excedente); a ordem é sempre título/lote -> excedente
        java.util.Optional<Long> devolucao = contaPagarRepository.findIdAtivoDoExcedente(excedenteId);
        if (devolucao.isPresent()) {
            contaPagarService.cancelarDevolucao(devolucao.get(), motivo.trim());
            ExcedenteAcerto ex = excedenteRepository.findByIdForUpdate(excedenteId).orElseThrow(() -> new ObjetoNaoEncontradoException("Excedente não encontrado."));
            evento(ex, "DESTINO_DESFEITO", "Destino de devolução desfeito (título a pagar nº " + contaPagarRepository.findById(devolucao.get()).map(ContaPagar::getNumero).orElse(null) + " cancelado). Motivo: " + motivo.trim(),
                    auditor.getCurrentAuditor().orElse(null));
            return;
        }
        // ordem de trava lotes -> excedente (a mesma da aplicação do crédito)
        java.util.Optional<CreditoClienteLote> loteOpt = loteRepository.findAtivoDoExcedenteForUpdate(excedenteId);
        ExcedenteAcerto e = excedenteRepository.findByIdForUpdate(excedenteId).orElseThrow(() -> new ObjetoNaoEncontradoException("Excedente não encontrado."));
        if (e.getSituacao() == ExcedenteAcerto.Situacao.DEVOLUCAO) throw new ItemInvalidoException("O excedente foi destinado como devolução ao cliente.");
        if (e.getSituacao() != ExcedenteAcerto.Situacao.CREDITO) throw new ItemInvalidoException("O excedente não possui destino a desfazer.");
        CreditoClienteLote lote = loteOpt.orElseThrow(() -> new ItemInvalidoException("Lote de crédito do excedente não encontrado."));
        exigirNaoUtilizado(lote);
        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        desfazerLote(lote, motivo, usuario);
        e.setSituacao(ExcedenteAcerto.Situacao.A_DESTINAR);
        e.setDestino(null);
        e.setDestinadoEm(null);
        e.setDestinadoPor(null);
        e.setUpdatedAt(LocalDateTime.now());
        excedenteRepository.save(e);
        evento(e, "DESTINO_DESFEITO", "Destino de crédito desfeito (lote nº " + lote.getId() + "). Motivo: " + motivo.trim(), usuario);
    }

    // ---------- lançamento manual (cadastro do cliente) ----------

    @Transactional
    public CreditoClienteDTOs.Lote lancarManual(UUID clienteId, BigDecimal valor, String motivo) {
        Cliente cliente = clienteRepository.findById(clienteId).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado."));
        BigDecimal v = CalculoFinanceiro.arredondar(valor);
        if (v.signum() <= 0) throw new ItemInvalidoException("O valor do crédito deve ser maior que zero.");
        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        CreditoClienteLote lote = new CreditoClienteLote();
        lote.setCliente(cliente);
        lote.setOrigemTipo(CreditoClienteLote.Origem.LANCAMENTO_MANUAL);
        lote.setValorOriginal(v);
        lote.setMotivo(motivo.trim());
        lote.setCreatedBy(usuario);
        loteRepository.save(lote);
        movimento(lote, CreditoClienteMovimento.Tipo.ENTRADA, v, "Lançamento manual: " + motivo.trim(), usuario);
        return lote(lote);
    }

    @Transactional
    public CreditoClienteDTOs.Lote desfazerManual(UUID clienteId, Long loteId, String motivo) {
        CreditoClienteLote lote = loteRepository.findByIdForUpdate(loteId).filter(l -> l.getCliente().getId().equals(clienteId))
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Lote de crédito não encontrado."));
        if (lote.getOrigemTipo() != CreditoClienteLote.Origem.LANCAMENTO_MANUAL) {
            throw new ItemInvalidoException("Este crédito veio de um excedente de acerto. Desfaça o destino pelo excedente.");
        }
        if (lote.getSituacao() == CreditoClienteLote.Situacao.DESFEITO) throw new ItemInvalidoException("O lançamento já foi desfeito.");
        exigirNaoUtilizado(lote);
        desfazerLote(lote, motivo, auditor.getCurrentAuditor().orElse(null));
        return lote(lote);
    }

    private static void exigirNaoUtilizado(CreditoClienteLote lote) {
        if (lote.getValorUtilizado().signum() > 0) {
            throw new ItemInvalidoException("O crédito já foi utilizado (" + MovimentacaoBancoService.moeda(lote.getValorUtilizado())
                    + "). Estorne a aplicação do crédito nos títulos (Contas a Receber) antes de desfazer.");
        }
    }

    private void desfazerLote(CreditoClienteLote lote, String motivo, Usuario usuario) {
        lote.setSituacao(CreditoClienteLote.Situacao.DESFEITO);
        lote.setUpdatedAt(LocalDateTime.now());
        loteRepository.save(lote);
        movimento(lote, CreditoClienteMovimento.Tipo.ESTORNO_ENTRADA, lote.getValorOriginal(), "Crédito desfeito. Motivo: " + motivo.trim(), usuario);
    }

    // ---------- leitura ----------

    @Transactional(readOnly = true)
    public CreditoClienteDTOs.Credito credito(UUID clienteId) {
        exigirCliente(clienteId);
        List<CreditoClienteDTOs.Lote> lotes = loteRepository.findByClienteIdOrderByIdDesc(clienteId).stream().map(this::lote).toList();
        return new CreditoClienteDTOs.Credito(CalculoFinanceiro.arredondar(loteRepository.saldo(clienteId)), lotes);
    }

    // extrato do mais recente ao mais antigo, com o saldo do cliente após cada movimento
    @Transactional(readOnly = true)
    public CreditoClienteDTOs.Extrato extrato(UUID clienteId, LocalDate de, LocalDate ate, int page, int size) {
        exigirCliente(clienteId);
        if (de != null && ate != null && ate.isBefore(de)) throw new ItemInvalidoException("A data inicial do período não pode ser posterior à data final.");
        BigDecimal saldo = BigDecimal.ZERO;
        List<CreditoClienteDTOs.Movimento> todos = new ArrayList<>();
        for (CreditoClienteMovimento m : movimentoRepository.findByClienteIdOrderByIdAsc(clienteId)) {
            saldo = m.aumentaSaldo() ? saldo.add(m.getValor()) : saldo.subtract(m.getValor());
            LocalDate dia = m.getDataHora().toLocalDate();
            if ((de != null && dia.isBefore(de)) || (ate != null && dia.isAfter(ate))) continue;
            todos.add(new CreditoClienteDTOs.Movimento(m.getId(), m.getTipo().name(), CalculoFinanceiro.arredondar(m.getValor()), m.getLote().getId(), m.getContaReceberId(),
                    m.getRecebimentoId(), m.getMotivo(), usuarioMapper.toResumoDTO(m.getUsuario()), m.getDataHora(), CalculoFinanceiro.arredondar(saldo)));
        }
        Collections.reverse(todos);
        int tamanho = Math.max(1, Math.min(size, 200));
        int pagina = Math.max(0, page);
        int inicio = Math.min(todos.size(), pagina * tamanho);
        List<CreditoClienteDTOs.Movimento> fatia = todos.subList(inicio, Math.min(todos.size(), inicio + tamanho));
        return new CreditoClienteDTOs.Extrato(CalculoFinanceiro.arredondar(saldo), fatia, pagina, tamanho, todos.size(), (int) Math.ceil(todos.size() / (double) tamanho));
    }

    private static void exigirAutoridade(String permissao) {
        var autenticacao = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean ok = autenticacao != null && autenticacao.getAuthorities().stream().anyMatch(a -> permissao.equals(a.getAuthority()));
        if (!ok) throw new org.springframework.security.access.AccessDeniedException("Acesso negado.");
    }

    private void exigirCliente(UUID clienteId) {
        if (!clienteRepository.existsById(clienteId)) throw new ObjetoNaoEncontradoException("Cliente não encontrado.");
    }

    private CreditoClienteDTOs.Lote lote(CreditoClienteLote l) {
        return new CreditoClienteDTOs.Lote(l.getId(), l.getOrigemTipo().name(), l.getOrigemId(), CalculoFinanceiro.arredondar(l.getValorOriginal()),
                CalculoFinanceiro.arredondar(l.getValorUtilizado()), CalculoFinanceiro.arredondar(l.getDisponivel()), l.getSituacao().name(), l.getMotivo(), l.getCreatedAt(),
                usuarioMapper.toResumoDTO(l.getCreatedBy()));
    }

    private void movimento(CreditoClienteLote lote, CreditoClienteMovimento.Tipo tipo, BigDecimal valor, String motivo, Usuario usuario) {
        movimento(lote, tipo, valor, motivo, usuario, null, null);
    }

    void movimento(CreditoClienteLote lote, CreditoClienteMovimento.Tipo tipo, BigDecimal valor, String motivo, Usuario usuario, Long contaReceberId, Long recebimentoId) {
        CreditoClienteMovimento m = new CreditoClienteMovimento();
        m.setCliente(lote.getCliente());
        m.setLote(lote);
        m.setTipo(tipo);
        m.setValor(valor);
        m.setContaReceberId(contaReceberId);
        m.setRecebimentoId(recebimentoId);
        m.setMotivo(motivo != null && motivo.length() > 500 ? motivo.substring(0, 500) : motivo);
        m.setUsuario(usuario);
        m.setDataHora(LocalDateTime.now());
        movimentoRepository.save(m);
    }

    private void evento(ExcedenteAcerto e, String evento, String detalhe, Usuario usuario) {
        ExcedenteAcertoEvento ev = new ExcedenteAcertoEvento();
        ev.setExcedente(e);
        ev.setEvento(evento);
        ev.setDetalhe(detalhe != null && detalhe.length() > 2000 ? detalhe.substring(0, 2000) : detalhe);
        ev.setUsuario(usuario);
        ev.setDataHora(LocalDateTime.now());
        excedenteEventoRepository.save(ev);
    }
}
