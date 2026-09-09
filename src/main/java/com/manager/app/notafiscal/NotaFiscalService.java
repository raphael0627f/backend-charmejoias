package com.manager.app.notafiscal;

import com.manager.app.notafiscal.cfop.CfopRepository;
import com.manager.app.notafiscal.dto.CfopDisponivelDTO;
import com.manager.app.notafiscal.dto.NotaFiscalItemRequestDTO;
import com.manager.app.notafiscal.dto.NotaFiscalPagamentoRequestDTO;
import com.manager.app.notafiscal.dto.NotaFiscalRequestDTO;
import com.manager.app.notafiscal.client.ContoraClient;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.notafiscal.tabela.CfopReferencia;
import com.manager.app.empresa.EmpresaRepository;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.persistence.criteria.JoinType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import com.manager.app.cliente.Cliente;
import com.manager.app.cliente.ClienteService;
import com.manager.app.empresa.Empresa;
import com.manager.app.empresa.EmpresaService;
import com.manager.app.produto.Produto;
import com.manager.app.produto.ProdutoService;
import com.manager.app.transportadora.Transportadora;
import com.manager.app.transportadora.TransportadoraService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotaFiscalService {

    private final NotaFiscalRepository repository;
    private final EmpresaRepository empresaRepository;
    private final EmpresaService empresaService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final TransportadoraService transportadoraService;
    private final NotaFiscalValidation validation;
    private final ContoraClient contoraClient;
    private final CfopRepository cfopRepository;

    public Page<NotaFiscal> findAll(String busca, StatusNotaFiscal status, LocalDate dataInicio, LocalDate dataFim, Pageable pageable){
        Specification<NotaFiscal> spec = comBusca(busca);

        if (status != null) {
            Specification<NotaFiscal> statusSpec = (root, query, cb) -> cb.equal(root.get("status"), status);
            spec = spec == null ? statusSpec : spec.and(statusSpec);
        }

        if (dataInicio != null) {
            Specification<NotaFiscal> dataInicioSpec = (root, query, cb) ->
                    SpecificationUtils.criadoAPartirDe(cb, root.get("createdAt"), dataInicio);
            spec = spec == null ? dataInicioSpec : spec.and(dataInicioSpec);
        }

        if (dataFim != null) {
            Specification<NotaFiscal> dataFimSpec = (root, query, cb) ->
                    SpecificationUtils.criadoAte(cb, root.get("createdAt"), dataFim);
            spec = spec == null ? dataFimSpec : spec.and(dataFimSpec);
        }

        return repository.findAll(spec, pageable);
    }

    private Specification<NotaFiscal> comBusca(String busca){
        if (busca == null || busca.isBlank()) return null;
        Long comoNumero = SpecificationUtils.comoLong(busca);
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(SpecificationUtils.likeIgnoreCase(cb, root.join("cliente", JoinType.LEFT).get("nome"), busca));
            if (comoNumero != null) {
                predicates.add(cb.equal(root.get("id"), comoNumero));
                predicates.add(cb.equal(root.get("numero"), comoNumero));
            }
            return cb.or(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    public NotaFiscal findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Nota fiscal não encontrada. id: " + id));
    }

    public List<CfopDisponivelDTO> cfopsDisponiveis(Long empresaId, UUID clienteId){
        Empresa empresa = empresaService.findById(empresaId);
        Cliente cliente = clienteService.findById(clienteId);

        CfopReferencia.Escopo escopo = CfopReferencia.escopoPara(empresa.getEndereco().getUf(), cliente.getEndereco().getUf());

        return cfopRepository.findByCodigoStartingWithOrderByCodigo(escopo.getPrefixoCfop()).stream()
                .map(cfop -> new CfopDisponivelDTO(cfop.getCodigo(), cfop.getDescricao()))
                .toList();
    }

    @Transactional
    public NotaFiscal create(NotaFiscalRequestDTO request){
        Empresa empresa = empresaRepository.findByIdForUpdate(request.getEmpresaId())
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Empresa não encontrada. id: " + request.getEmpresaId()));
        Cliente cliente = clienteService.findById(request.getClienteId());

        String ufEmpresa = empresa.getEndereco().getUf();
        String ufCliente = cliente.getEndereco().getUf();

        if (isBlank(empresa.getEndereco().getIbge())) {
            throw new ItemInvalidoException("Endereço da empresa está sem o código IBGE do município, obrigatório para a NFe. empresaId: " + empresa.getId());
        }
        if (isBlank(cliente.getEndereco().getIbge())) {
            throw new ItemInvalidoException("Endereço do cliente está sem o código IBGE do município, obrigatório para a NFe. clienteId: " + cliente.getId());
        }

        NotaFiscal notaFiscal = new NotaFiscal();
        notaFiscal.setEmpresa(empresa);
        notaFiscal.setCliente(cliente);
        notaFiscal.setNaturezaOperacao(request.getNaturezaOperacao());
        notaFiscal.setConsumidorFinal(request.getConsumidorFinal());
        notaFiscal.setIndicadorPresenca(request.getIndicadorPresenca());
        notaFiscal.setAmbiente(request.getAmbiente());
        notaFiscal.setStatus(StatusNotaFiscal.RASCUNHO);
        notaFiscal.setDataSaidaEntrada(request.getDataSaidaEntrada());
        notaFiscal.setTransportadora(resolverTransportadora(request.getTransportadoraId()));
        notaFiscal.setModalidadeFrete(request.getModalidadeFrete());
        notaFiscal.setInformacoesComplementares(request.getInformacoesComplementares());
        notaFiscal.setDescontoTotal(request.getDescontoTotal());

        List<BigDecimal> descontosRateados = request.getDescontoTotal() != null
                ? ratearDesconto(request.getItens(), request.getDescontoTotal())
                : null;

        int indiceItem = 0;
        for (NotaFiscalItemRequestDTO itemRequest : request.getItens()){
            validation.validarCfop(itemRequest.getCfop(), ufEmpresa, ufCliente);

            Produto produto = produtoService.findById(itemRequest.getProdutoId());
            BigDecimal desconto = descontosRateados != null
                    ? descontosRateados.get(indiceItem)
                    : (itemRequest.getDesconto() != null ? itemRequest.getDesconto() : BigDecimal.ZERO);
            BigDecimal valorTotal = itemRequest.getValorUnitario().multiply(itemRequest.getQuantidade()).subtract(desconto);

            NotaFiscalItem item = new NotaFiscalItem();
            item.setProduto(produto);
            item.setQuantidade(itemRequest.getQuantidade());
            item.setValorUnitario(itemRequest.getValorUnitario());
            item.setDesconto(desconto);
            item.setValorTotal(valorTotal);
            item.setCfop(itemRequest.getCfop());

            notaFiscal.adicionarItem(item);
            indiceItem++;
        }

        for (NotaFiscalPagamentoRequestDTO pagamentoRequest : request.getPagamentos()){
            NotaFiscalPagamento pagamento = new NotaFiscalPagamento();
            pagamento.setFormaPagamento(pagamentoRequest.getFormaPagamento());
            pagamento.setValor(pagamentoRequest.getValor());
            notaFiscal.adicionarPagamento(pagamento);
        }

        notaFiscal.setSerie(empresa.getSerieNfe());
        notaFiscal.setNumero(empresa.getProximoNumeroNfe());
        empresa.setProximoNumeroNfe(empresa.getProximoNumeroNfe() + 1);

        return repository.save(notaFiscal);
    }

    @Transactional
    public NotaFiscal update(Long id, NotaFiscalRequestDTO request){
        NotaFiscal notaFiscal = this.findById(id);
        if (notaFiscal.getStatus() != StatusNotaFiscal.RASCUNHO) {
            throw new ItemInvalidoException("Só é possível atualizar uma nota fiscal em rascunho. id: " + id + ", status atual: " + notaFiscal.getStatus());
        }

        Empresa empresa = empresaService.findById(request.getEmpresaId());
        Cliente cliente = clienteService.findById(request.getClienteId());

        String ufEmpresa = empresa.getEndereco().getUf();
        String ufCliente = cliente.getEndereco().getUf();

        if (isBlank(empresa.getEndereco().getIbge())) {
            throw new ItemInvalidoException("Endereço da empresa está sem o código IBGE do município, obrigatório para a NFe. empresaId: " + empresa.getId());
        }
        if (isBlank(cliente.getEndereco().getIbge())) {
            throw new ItemInvalidoException("Endereço do cliente está sem o código IBGE do município, obrigatório para a NFe. clienteId: " + cliente.getId());
        }

        notaFiscal.setEmpresa(empresa);
        notaFiscal.setCliente(cliente);
        notaFiscal.setNaturezaOperacao(request.getNaturezaOperacao());
        notaFiscal.setConsumidorFinal(request.getConsumidorFinal());
        notaFiscal.setIndicadorPresenca(request.getIndicadorPresenca());
        notaFiscal.setAmbiente(request.getAmbiente());
        notaFiscal.setDataSaidaEntrada(request.getDataSaidaEntrada());
        notaFiscal.setTransportadora(resolverTransportadora(request.getTransportadoraId()));
        notaFiscal.setModalidadeFrete(request.getModalidadeFrete());
        notaFiscal.setInformacoesComplementares(request.getInformacoesComplementares());
        notaFiscal.setDescontoTotal(request.getDescontoTotal());

        new ArrayList<>(notaFiscal.getItens()).forEach(notaFiscal::removerItem);
        new ArrayList<>(notaFiscal.getPagamentos()).forEach(notaFiscal::removerPagamento);

        List<BigDecimal> descontosRateados = request.getDescontoTotal() != null
                ? ratearDesconto(request.getItens(), request.getDescontoTotal())
                : null;

        int indiceItem = 0;
        for (NotaFiscalItemRequestDTO itemRequest : request.getItens()){
            validation.validarCfop(itemRequest.getCfop(), ufEmpresa, ufCliente);

            Produto produto = produtoService.findById(itemRequest.getProdutoId());
            BigDecimal desconto = descontosRateados != null
                    ? descontosRateados.get(indiceItem)
                    : (itemRequest.getDesconto() != null ? itemRequest.getDesconto() : BigDecimal.ZERO);
            BigDecimal valorTotal = itemRequest.getValorUnitario().multiply(itemRequest.getQuantidade()).subtract(desconto);

            NotaFiscalItem item = new NotaFiscalItem();
            item.setProduto(produto);
            item.setQuantidade(itemRequest.getQuantidade());
            item.setValorUnitario(itemRequest.getValorUnitario());
            item.setDesconto(desconto);
            item.setValorTotal(valorTotal);
            item.setCfop(itemRequest.getCfop());

            notaFiscal.adicionarItem(item);
            indiceItem++;
        }

        for (NotaFiscalPagamentoRequestDTO pagamentoRequest : request.getPagamentos()){
            NotaFiscalPagamento pagamento = new NotaFiscalPagamento();
            pagamento.setFormaPagamento(pagamentoRequest.getFormaPagamento());
            pagamento.setValor(pagamentoRequest.getValor());
            notaFiscal.adicionarPagamento(pagamento);
        }

        return notaFiscal;
    }

    @Transactional
    public NotaFiscal emitir(Long id){
        // lock pessimista: evita que dois cliques/retries simultâneos no mesmo id
        // criem dois rascunhos na Contora pro mesmo número, gerando rejeição de duplicidade na SEFAZ
        NotaFiscal notaFiscal = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Nota fiscal não encontrada. id: " + id));
        if (notaFiscal.getStatus() != StatusNotaFiscal.RASCUNHO) {
            throw new ItemInvalidoException("Nota fiscal já foi enviada. id: " + id + ", status atual: " + notaFiscal.getStatus());
        }
        notaFiscal.setStatus(StatusNotaFiscal.ENVIADA);

        String contoraCompanyId = notaFiscal.getEmpresa().getContoraCompanyId();
        String documentId = contoraClient.criarRascunhoNfe(notaFiscal);
        notaFiscal.setContoraDocumentId(documentId);

        contoraClient.buildRascunho(contoraCompanyId, documentId, notaFiscal.getId());
        contoraClient.autorizarRascunho(contoraCompanyId, documentId, notaFiscal.getId());

        return notaFiscal;
    }

    @Transactional
    public NotaFiscal atualizarStatus(Long id){
        NotaFiscal notaFiscal = this.findById(id);
        if (notaFiscal.getContoraDocumentId() == null) {
            throw new ItemInvalidoException("Nota fiscal ainda não foi enviada à Contora. id: " + id);
        }

        Map<String, Object> dados = contoraClient.consultarStatus(notaFiscal.getEmpresa().getContoraCompanyId(), notaFiscal.getContoraDocumentId());
        aplicarAtualizacaoStatusPolling(notaFiscal, dados);
        return notaFiscal;
    }

    @Transactional
    public NotaFiscal cancelar(Long id, String motivo){
        NotaFiscal notaFiscal = this.findById(id);
        if (notaFiscal.getStatus() != StatusNotaFiscal.AUTORIZADA) {
            throw new ItemInvalidoException("Só é possível cancelar uma nota autorizada. id: " + id + ", status atual: " + notaFiscal.getStatus());
        }
        if (motivo == null || motivo.trim().length() < 15) {
            throw new ItemInvalidoException("Motivo do cancelamento precisa ter ao menos 15 caracteres");
        }

        contoraClient.cancelar(notaFiscal.getEmpresa().getContoraCompanyId(), notaFiscal.getContoraDocumentId(), motivo);
        notaFiscal.setStatus(StatusNotaFiscal.CANCELAMENTO_SOLICITADO);
        notaFiscal.setMotivoRejeicaoCancelamento(null);
        return notaFiscal;
    }

    @Transactional
    public NotaFiscal corrigir(Long id, String texto){
        NotaFiscal notaFiscal = this.findById(id);
        if (notaFiscal.getStatus() != StatusNotaFiscal.AUTORIZADA) {
            throw new ItemInvalidoException("Só é possível emitir carta de correção para uma nota autorizada. id: " + id + ", status atual: " + notaFiscal.getStatus());
        }
        if (texto == null || texto.trim().length() < 15) {
            throw new ItemInvalidoException("Texto da carta de correção precisa ter ao menos 15 caracteres");
        }

        NotaFiscalCorrecao correcao = new NotaFiscalCorrecao();
        correcao.setSequencia(notaFiscal.getCorrecoes().size() + 1);
        correcao.setTexto(texto);
        correcao.setStatus(StatusCorrecao.SOLICITADA);
        notaFiscal.adicionarCorrecao(correcao);

        contoraClient.corrigir(notaFiscal.getEmpresa().getContoraCompanyId(), notaFiscal.getContoraDocumentId(), texto);

        return notaFiscal;
    }

    public byte[] baixarArtefato(Long id, String artefato){
        NotaFiscal notaFiscal = this.findById(id);
        if (notaFiscal.getContoraDocumentId() == null) {
            throw new ItemInvalidoException("Nota fiscal ainda não foi enviada à Contora. id: " + id);
        }
        return contoraClient.baixarArtefato(notaFiscal.getEmpresa().getContoraCompanyId(), notaFiscal.getContoraDocumentId(), artefato);
    }

    // formato do GET .../status: campos aninhados em "lifecycle" e "sefaz"
    @SuppressWarnings("unchecked")
    private void aplicarAtualizacaoStatusPolling(NotaFiscal notaFiscal, Map<String, Object> dados){
        Object lifecycleObj = dados.get("lifecycle");
        if (!(lifecycleObj instanceof Map<?, ?> lifecycle)) return;

        Object status = lifecycle.get("status");
        if (status == null) return;

        Map<String, Object> sefaz = dados.get("sefaz") instanceof Map<?, ?> sefazMap ? (Map<String, Object>) sefazMap : Map.of();

        switch (status.toString()) {
            case "authorized" -> {
                // lifecycle.status continua "authorized" mesmo depois de pedir cancelamento
                // (o cancelamento vive à parte em data.cancellation) - não pode voltar a AUTORIZADA
                // enquanto a confirmação do cancelamento ainda não chegou, senão perde o rastro do pedido
                if (notaFiscal.getStatus() != StatusNotaFiscal.CANCELAMENTO_SOLICITADO) {
                    notaFiscal.setStatus(StatusNotaFiscal.AUTORIZADA);
                }
                notaFiscal.setChaveAcesso(stringOu(sefaz.get("access_key")));
                notaFiscal.setProtocoloAutorizacao(stringOu(sefaz.get("protocol")));
            }
            case "error" -> {
                notaFiscal.setStatus(StatusNotaFiscal.REJEITADA);
                notaFiscal.setMotivoRejeicao(stringOu(sefaz.get("status_message")));
            }
            case "draft", "built_unsigned", "built_signed", "authorize_pending" -> notaFiscal.setStatus(StatusNotaFiscal.ENVIADA);
            default -> { }
        }

        aplicarStatusCancelamento(notaFiscal, dados.get("cancellation"));
        aplicarStatusCorrecao(notaFiscal, dados.get("correction"));
    }

    // cancelamento/correção vivem em objetos separados na resposta (data.cancellation, data.correction),
    // não em lifecycle.status - a nota continua "authorized" mesmo com um cancelamento rejeitado pela SEFAZ
    // (ex: fora do prazo legal de cancelamento), então usamos cancellation.status para diferenciar
    // rejeição (volta a AUTORIZADA, guardando o motivo) de confirmação (protocol presente -> CANCELADA)
    @SuppressWarnings("unchecked")
    private void aplicarStatusCancelamento(NotaFiscal notaFiscal, Object cancellationObj){
        if (!(cancellationObj instanceof Map<?, ?> cancellationMap)) return;
        Map<String, Object> cancellation = (Map<String, Object>) cancellationMap;

        if ("rejected".equals(stringOu(cancellation.get("status")))) {
            notaFiscal.setStatus(StatusNotaFiscal.AUTORIZADA);
            notaFiscal.setMotivoRejeicaoCancelamento(stringOu(cancellation.get("status_message")));
            return;
        }

        if (cancellation.get("protocol") != null) {
            notaFiscal.setStatus(StatusNotaFiscal.CANCELADA);
            notaFiscal.setMotivoRejeicaoCancelamento(null);
        }
    }

    // "correction" na resposta de status reflete só a última carta de correção registrada;
    // casamos pelo número de sequência (que controlamos nós mesmos ao criar o registro local)
    // pra achar qual das nossas correções isso confirma, e só mexemos nela enquanto ainda está SOLICITADA
    @SuppressWarnings("unchecked")
    private void aplicarStatusCorrecao(NotaFiscal notaFiscal, Object correctionObj){
        if (!(correctionObj instanceof Map<?, ?> correctionMap)) return;
        Map<String, Object> correction = (Map<String, Object>) correctionMap;

        Object sequenciaObj = correction.get("sequence");
        if (sequenciaObj == null) return;
        Integer sequencia = Integer.parseInt(sequenciaObj.toString());

        NotaFiscalCorrecao correcao = notaFiscal.getCorrecoes().stream()
                .filter(c -> sequencia.equals(c.getSequencia()) && c.getStatus() == StatusCorrecao.SOLICITADA)
                .findFirst()
                .orElse(null);
        if (correcao == null) return;

        String status = stringOu(correction.get("status"));
        if ("accepted".equals(status)) {
            correcao.setStatus(StatusCorrecao.ACEITA);
            correcao.setProtocolo(stringOu(correction.get("protocol")));
        } else if ("rejected".equals(status)) {
            correcao.setStatus(StatusCorrecao.REJEITADA);
            correcao.setMotivoRejeicao(stringOu(correction.get("status_message")));
        }
    }

    private String stringOu(Object valor){
        return valor != null ? valor.toString() : null;
    }

    private boolean isBlank(String valor){
        return valor == null || valor.isBlank();
    }

    private Transportadora resolverTransportadora(Long transportadoraId){
        return transportadoraId != null ? transportadoraService.findById(transportadoraId) : null;
    }

    // rateia um desconto único da nota entre os itens, proporcionalmente ao valor bruto de cada um
    // (mesmo critério que a Contora usa pra ratear frete/seguro entre items[].freight/insurance) -
    // o resíduo do arredondamento vai pro item de maior valor, pra soma bater exatamente com o informado
    private List<BigDecimal> ratearDesconto(List<NotaFiscalItemRequestDTO> itens, BigDecimal descontoTotal){
        List<BigDecimal> bases = itens.stream()
                .map(i -> i.getValorUnitario().multiply(i.getQuantidade()))
                .toList();
        BigDecimal totalBase = bases.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalBase.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ItemInvalidoException("Não é possível ratear o desconto: valor total dos itens é zero");
        }
        if (descontoTotal.compareTo(BigDecimal.ZERO) < 0 || descontoTotal.compareTo(totalBase) > 0) {
            throw new ItemInvalidoException("Desconto total inválido: deve ser maior ou igual a zero e no máximo igual ao valor total dos itens");
        }

        List<BigDecimal> descontos = new ArrayList<>();
        BigDecimal somaParcial = BigDecimal.ZERO;
        int indiceMaiorBase = 0;
        BigDecimal maiorBase = BigDecimal.ZERO;

        for (int i = 0; i < bases.size(); i++) {
            BigDecimal base = bases.get(i);
            BigDecimal desconto = descontoTotal.multiply(base).divide(totalBase, 2, RoundingMode.HALF_UP);
            descontos.add(desconto);
            somaParcial = somaParcial.add(desconto);
            if (base.compareTo(maiorBase) > 0) {
                maiorBase = base;
                indiceMaiorBase = i;
            }
        }

        BigDecimal residuo = descontoTotal.subtract(somaParcial);
        if (residuo.compareTo(BigDecimal.ZERO) != 0) {
            descontos.set(indiceMaiorBase, descontos.get(indiceMaiorBase).add(residuo));
        }

        return descontos;
    }
}
