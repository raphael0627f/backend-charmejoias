package com.manager.app.financeiro;

import com.manager.app.acertoconsignacao.AcertoConsignacao;
import com.manager.app.acertoconsignacao.AcertoConsignacaoRepository;
import com.manager.app.cliente.Cliente;
import com.manager.app.financeiro.dto.ContaReceberResumoDTO;
import com.manager.app.financeiro.dto.ExcedenteDTOs;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.specification.SpecificationUtils;
import com.manager.app.usuario.Usuario;
import com.manager.app.usuario.UsuarioResumoMapper;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// excedente do acerto (fase 1 do crédito do cliente): nasce na confirmação do acerto, a partir do que o cliente efetivamente pagou dos títulos do pré-acerto,
// e fica A_DESTINAR. A regra de cálculo está isolada em ExcedenteAcertoCalculo. Destino (crédito/devolução) e desfazer vêm nas fases seguintes.
@Service
@RequiredArgsConstructor
public class ExcedenteAcertoService {

    private final ExcedenteAcertoRepository repository;
    private final ExcedenteAcertoEventoRepository eventoRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final AcertoConsignacaoRepository acertoRepository;
    private final UsuarioAuditorAware auditor;
    private final UsuarioResumoMapper usuarioMapper;

    // ---------- sincronização (chamada dentro da transação de quem alterou o acerto ou os títulos do pré-acerto) ----------

    // recalcula o excedente da consignação: cria (acerto confirmado com título de pré-acerto), ajusta, cancela (zerou) ou reativa. Excedente já destinado não muda.
    @Transactional
    public void sincronizar(Long consignacaoId) {
        Optional<AcertoConsignacao> acertoOpt = acertoRepository.findByConsignacaoId(consignacaoId);
        if (acertoOpt.isEmpty() || !Boolean.TRUE.equals(acertoOpt.get().getConfirmado())) return;
        AcertoConsignacao acerto = acertoOpt.get();
        List<ContaReceber> doPreAcerto = contaReceberRepository.findAtivosDaConsignacao(consignacaoId).stream().filter(t -> t.getPreAcerto() != null).toList();
        Optional<ExcedenteAcerto> existente = repository.findByConsignacaoIdForUpdate(consignacaoId);
        // somente o acerto com título de pré-acerto gera excedente automático
        if (doPreAcerto.isEmpty() && existente.isEmpty()) return;
        BigDecimal recebido = BigDecimal.ZERO;
        for (ContaReceber t : doPreAcerto) recebido = recebido.add(CalculoFinanceiro.arredondar(t.getValorRecebido()));
        BigDecimal calculado = ExcedenteAcertoCalculo.excedente(recebido, acerto.getValorTotalComDesconto());
        Usuario usuario = auditor.getCurrentAuditor().orElse(null);

        if (existente.isEmpty()) {
            if (calculado.signum() <= 0) return;
            ExcedenteAcerto e = new ExcedenteAcerto();
            e.setAcerto(acerto);
            e.setConsignacao(acerto.getConsignacao());
            e.setCliente(acerto.getConsignacao().getCliente());
            e.setValor(calculado);
            repository.save(e);
            registrar(e, "EXCEDENTE_CRIADO", "Excedente de " + com.manager.app.financeiro.MovimentacaoBancoService.moeda(calculado) + " gerado na confirmação do acerto nº "
                    + acerto.getId() + " (recebido dos títulos do pré-acerto: " + com.manager.app.financeiro.MovimentacaoBancoService.moeda(recebido)
                    + "). Situação: a destinar.", usuario);
            return;
        }
        ExcedenteAcerto e = existente.get();
        if (e.getSituacao() == ExcedenteAcerto.Situacao.CREDITO || e.getSituacao() == ExcedenteAcerto.Situacao.DEVOLUCAO) return;
        if (calculado.signum() <= 0) {
            if (e.getSituacao() != ExcedenteAcerto.Situacao.CANCELADO) {
                e.setSituacao(ExcedenteAcerto.Situacao.CANCELADO);
                e.setUpdatedAt(LocalDateTime.now());
                repository.save(e);
                registrar(e, "EXCEDENTE_CANCELADO", "Excedente cancelado: o valor recebido do pré-acerto não supera mais o valor do acerto.", usuario);
            }
            return;
        }
        boolean reativando = e.getSituacao() == ExcedenteAcerto.Situacao.CANCELADO;
        if (!reativando && e.getValor().compareTo(calculado) == 0) return;
        BigDecimal anterior = e.getValor();
        e.setValor(calculado);
        e.setSituacao(ExcedenteAcerto.Situacao.A_DESTINAR);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        registrar(e, reativando ? "EXCEDENTE_REATIVADO" : "EXCEDENTE_AJUSTADO", (reativando ? "Excedente reativado em " : "Excedente ajustado de "
                + com.manager.app.financeiro.MovimentacaoBancoService.moeda(anterior) + " para ")
                + (reativando ? com.manager.app.financeiro.MovimentacaoBancoService.moeda(calculado) : com.manager.app.financeiro.MovimentacaoBancoService.moeda(calculado))
                + " conforme o valor recebido do pré-acerto.", usuario);
    }

    // título do pré-acerto cujo excedente já foi destinado (crédito ou devolução) não pode ser cancelado nem ter recebimento estornado
    public void bloquearSeDestinado(ContaReceber titulo, String acao) {
        if (titulo.getPreAcerto() == null || titulo.getConsignacao() == null) return;
        repository.findByConsignacaoIdForUpdate(titulo.getConsignacao().getId()).ifPresent(e -> {
            if (e.getSituacao() == ExcedenteAcerto.Situacao.CREDITO || e.getSituacao() == ExcedenteAcerto.Situacao.DEVOLUCAO) {
                throw new ItemInvalidoException("O título pertence a um pré-acerto cujo excedente já foi destinado (" + (e.getSituacao() == ExcedenteAcerto.Situacao.CREDITO ? "crédito" : "devolução")
                        + "). Desfaça o destino do excedente antes de " + acao + ".");
            }
        });
    }

    public void sincronizarPorTitulo(ContaReceber titulo) {
        if (titulo.getPreAcerto() == null || titulo.getConsignacao() == null) return;
        sincronizar(titulo.getConsignacao().getId());
    }

    // ---------- leitura ----------

    @Transactional(readOnly = true)
    public ExcedenteDTOs.Resumo resumoDoAcerto(Long acertoId) {
        return repository.findByAcertoId(acertoId).filter(e -> e.getSituacao() != ExcedenteAcerto.Situacao.CANCELADO)
                .map(e -> new ExcedenteDTOs.Resumo(e.getId(), CalculoFinanceiro.arredondar(e.getValor()), e.getSituacao().name(),
                        e.getDestino() == null ? null : e.getDestino().name(), e.getCreatedAt())).orElse(null);
    }

    @Transactional(readOnly = true)
    public ExcedenteDTOs.Lista listar(String situacao, String busca, int page, int size) {
        ExcedenteAcerto.Situacao filtro = situacao == null || situacao.isBlank() ? null : parseSituacao(situacao);
        int tamanho = Math.max(1, Math.min(size, 200));
        Specification<ExcedenteAcerto> spec = (root, query, cb) -> cb.conjunction();
        if (filtro != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("situacao"), filtro));
        if (busca != null && !busca.isBlank()) {
            Long numero = SpecificationUtils.comoLong(busca);
            String termo = busca.trim();
            spec = spec.and((root, query, cb) -> {
                var cliente = root.join("cliente", JoinType.LEFT);
                List<jakarta.persistence.criteria.Predicate> ou = new ArrayList<>();
                ou.add(SpecificationUtils.likeIgnoreCase(cb, cliente.get("codigo"), termo, ModoBusca.MEIO));
                ou.add(SpecificationUtils.likeIgnoreCase(cb, cliente.get("nome"), termo, ModoBusca.MEIO));
                if (numero != null) {
                    ou.add(cb.equal(root.get("acerto").get("id"), numero));
                    ou.add(cb.equal(root.get("consignacao").get("id"), numero));
                }
                return cb.or(ou.toArray(new jakarta.persistence.criteria.Predicate[0]));
            });
        }
        Page<ExcedenteAcerto> pagina = repository.findAll(spec, PageRequest.of(Math.max(0, page), tamanho, Sort.by(Sort.Direction.DESC, "id")));
        return new ExcedenteDTOs.Lista(pagina.getContent().stream().map(this::item).toList(), pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ExcedenteDTOs.Detalhe detalhe(Long id) {
        ExcedenteAcerto e = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Excedente não encontrado."));
        List<ExcedenteDTOs.Evento> eventos = eventoRepository.findByExcedenteIdOrderByIdAsc(id).stream()
                .map(ev -> new ExcedenteDTOs.Evento(ev.getId(), ev.getEvento(), ev.getDetalhe(), usuarioMapper.toResumoDTO(ev.getUsuario()), ev.getDataHora())).toList();
        return new ExcedenteDTOs.Detalhe(item(e), eventos);
    }

    private static ExcedenteAcerto.Situacao parseSituacao(String s) {
        try {
            return ExcedenteAcerto.Situacao.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ItemInvalidoException("Situação inválida. Use A_DESTINAR, CREDITO, DEVOLUCAO ou CANCELADO.");
        }
    }

    private ExcedenteDTOs.Item item(ExcedenteAcerto e) {
        Cliente c = e.getCliente();
        return new ExcedenteDTOs.Item(e.getId(), CalculoFinanceiro.arredondar(e.getValor()), e.getSituacao().name(), e.getDestino() == null ? null : e.getDestino().name(),
                e.getAcerto().getId(), e.getConsignacao().getId(), new ContaReceberResumoDTO.ClienteRef(c.getId(), c.getCodigo(), c.getNome(), ContaReceberMapper.documentoDe(c)),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private void registrar(ExcedenteAcerto e, String evento, String detalhe, Usuario usuario) {
        ExcedenteAcertoEvento ev = new ExcedenteAcertoEvento();
        ev.setExcedente(e);
        ev.setEvento(evento);
        ev.setDetalhe(detalhe != null && detalhe.length() > 2000 ? detalhe.substring(0, 2000) : detalhe);
        ev.setUsuario(usuario);
        ev.setDataHora(LocalDateTime.now());
        eventoRepository.save(ev);
    }
}
