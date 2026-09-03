package com.manager.app.service;

import com.manager.app.dto.notafiscal.CfopDisponivelDTO;
import com.manager.app.dto.notafiscal.NotaFiscalItemRequestDTO;
import com.manager.app.dto.notafiscal.NotaFiscalPagamentoRequestDTO;
import com.manager.app.dto.notafiscal.NotaFiscalRequestDTO;
import com.manager.app.entity.*;
import com.manager.app.infra.contora.ContoraClient;
import com.manager.app.infra.exception.ItemInvalidoException;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.infra.nfe.CfopReferencia;
import com.manager.app.infra.restEnum.StatusNotaFiscal;
import com.manager.app.infra.validation.NotaFiscalValidation;
import com.manager.app.repository.EmpresaRepository;
import com.manager.app.repository.NotaFiscalRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final NotaFiscalValidation validation;
    private final ContoraClient contoraClient;

    public Page<NotaFiscal> findAll(Pageable pageable){
        return repository.findAll(pageable);
    }

    public NotaFiscal findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Nota fiscal não encontrada. id: " + id));
    }

    public List<CfopDisponivelDTO> cfopsDisponiveis(Long empresaId, UUID clienteId){
        Empresa empresa = empresaService.findById(empresaId);
        Cliente cliente = clienteService.findById(clienteId);

        CfopReferencia.Escopo escopo = CfopReferencia.escopoPara(empresa.getEndereco().getUf(), cliente.getEndereco().getUf());

        return CfopReferencia.disponiveisPara(escopo).stream()
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

        for (NotaFiscalItemRequestDTO itemRequest : request.getItens()){
            validation.validarCfop(itemRequest.getCfop(), ufEmpresa, ufCliente);

            Produto produto = produtoService.findById(itemRequest.getProdutoId());
            BigDecimal desconto = itemRequest.getDesconto() != null ? itemRequest.getDesconto() : BigDecimal.ZERO;
            BigDecimal valorTotal = itemRequest.getValorUnitario().multiply(itemRequest.getQuantidade()).subtract(desconto);

            NotaFiscalItem item = new NotaFiscalItem();
            item.setProduto(produto);
            item.setQuantidade(itemRequest.getQuantidade());
            item.setValorUnitario(itemRequest.getValorUnitario());
            item.setDesconto(desconto);
            item.setValorTotal(valorTotal);
            item.setCfop(itemRequest.getCfop());

            notaFiscal.adicionarItem(item);
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
        return notaFiscal;
    }

    public void corrigir(Long id, String texto){
        NotaFiscal notaFiscal = this.findById(id);
        if (notaFiscal.getStatus() != StatusNotaFiscal.AUTORIZADA) {
            throw new ItemInvalidoException("Só é possível emitir carta de correção para uma nota autorizada. id: " + id + ", status atual: " + notaFiscal.getStatus());
        }
        if (texto == null || texto.trim().length() < 15) {
            throw new ItemInvalidoException("Texto da carta de correção precisa ter ao menos 15 caracteres");
        }

        contoraClient.corrigir(notaFiscal.getEmpresa().getContoraCompanyId(), notaFiscal.getContoraDocumentId(), texto);
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
                notaFiscal.setStatus(StatusNotaFiscal.AUTORIZADA);
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
    }

    // cancelamento/correção vivem em objetos separados na resposta (data.cancellation, data.correction),
    // não em lifecycle.status - a Contora não documenta um enum fechado de cancellation.status,
    // então usamos a presença do protocolo como confirmação de que o SEFAZ processou o cancelamento
    @SuppressWarnings("unchecked")
    private void aplicarStatusCancelamento(NotaFiscal notaFiscal, Object cancellationObj){
        if (!(cancellationObj instanceof Map<?, ?> cancellationMap)) return;
        Map<String, Object> cancellation = (Map<String, Object>) cancellationMap;

        if (cancellation.get("protocol") != null) {
            notaFiscal.setStatus(StatusNotaFiscal.CANCELADA);
        }
    }

    private String stringOu(Object valor){
        return valor != null ? valor.toString() : null;
    }

    private boolean isBlank(String valor){
        return valor == null || valor.isBlank();
    }
}
