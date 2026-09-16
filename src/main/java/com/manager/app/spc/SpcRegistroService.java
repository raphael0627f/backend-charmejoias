package com.manager.app.spc;

import com.manager.app.cliente.Cliente;
import com.manager.app.cliente.ClienteService;
import com.manager.app.endereco.Endereco;
import com.manager.app.spc.client.SpcClient;
import com.manager.app.spc.client.SpcXmlBuilder;
import com.manager.app.spc.client.SpcXmlStorageService;
import com.manager.app.spc.dto.SpcExclusaoRequestDTO;
import com.manager.app.spc.dto.SpcInclusaoRequestDTO;
import com.manager.app.spc.dto.SpcRegistroResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SpcRegistroService {

    private final ClienteService clienteService;
    private final SpcCatalogoService catalogoService;
    private final SpcRegistroRepository repository;
    private final SpcXmlBuilder xmlBuilder;
    private final SpcClient client;
    private final SpcXmlStorageService xmlStorageService;
    private final com.manager.app.usuario.UsuarioResumoMapper usuarioResumoMapper;

    private static final Pattern SUCESSO = Pattern.compile("<sucesso>(.*?)</sucesso>", Pattern.DOTALL);

    private String extrairSucesso(String xmlResposta){
        Matcher matcher = SUCESSO.matcher(xmlResposta);
        return matcher.find() ? matcher.group(1).trim() : xmlResposta;
    }

    private SpcXmlBuilder.DadosPessoaFisica dadosPessoaFisica(Cliente cliente, TipoPessoaSpc tipoPessoa){
        return tipoPessoa == TipoPessoaSpc.F ? new SpcXmlBuilder.DadosPessoaFisica(cliente.getCpf(), cliente.getNome()) : null;
    }

    private SpcXmlBuilder.DadosPessoaJuridica dadosPessoaJuridica(Cliente cliente, TipoPessoaSpc tipoPessoa){
        return tipoPessoa == TipoPessoaSpc.J ? new SpcXmlBuilder.DadosPessoaJuridica(cliente.getDocumento(), cliente.getNome(), null) : null;
    }

    private SpcXmlBuilder.DadosEndereco dadosEndereco(Cliente cliente){
        Endereco endereco = cliente.getEnderecoPrincipal();
        if (endereco == null) {
            return null;
        }
        return new SpcXmlBuilder.DadosEndereco(endereco.getCep(), endereco.getLogradouro(), endereco.getBairro(),
                endereco.getNumero(), endereco.getComplemento());
    }

    public SpcRegistroResponseDTO incluir(SpcInclusaoRequestDTO request){
        Cliente cliente = clienteService.findByIdAndAtivo(request.getClienteId());
        SpcNaturezaInclusao natureza = catalogoService.buscarNaturezaInclusao(request.getNaturezaInclusaoId());

        String xmlRequest = xmlBuilder.montarInclusao(
                dadosPessoaFisica(cliente, request.getTipoPessoa()),
                dadosPessoaJuridica(cliente, request.getTipoPessoa()),
                request.getDataCompra(), request.getDataVencimento(), request.getCodigoTipoDevedor().name(),
                request.getNumeroContrato(), request.getValorDebito(), natureza.getId().longValue(),
                dadosEndereco(cliente));

        String xmlResponse = client.incluir(xmlRequest);

        SpcRegistro registro = new SpcRegistro();
        registro.setCliente(cliente);
        registro.setTipoOperacao(TipoOperacaoSpc.INCLUSAO);
        registro.setTipoPessoa(request.getTipoPessoa());
        registro.setNumeroContrato(request.getNumeroContrato());
        registro.setDataCompra(request.getDataCompra());
        registro.setDataVencimento(request.getDataVencimento());
        registro.setCodigoTipoDevedor(request.getCodigoTipoDevedor());
        registro.setValorDebito(request.getValorDebito());
        registro.setNaturezaInclusao(natureza);
        registro.setMensagemRetorno(extrairSucesso(xmlResponse));
        registro = repository.save(registro);

        xmlStorageService.salvarInclusao(registro.getId(), xmlRequest, xmlResponse);

        return toResponseDTO(registro);
    }

    public SpcRegistroResponseDTO excluir(SpcExclusaoRequestDTO request){
        Cliente cliente = clienteService.findByIdAndAtivo(request.getClienteId());
        SpcMotivoExclusao motivo = catalogoService.buscarMotivoExclusao(request.getMotivoExclusaoId());

        String xmlRequest = xmlBuilder.montarExclusao(
                dadosPessoaFisica(cliente, request.getTipoPessoa()),
                dadosPessoaJuridica(cliente, request.getTipoPessoa()),
                request.getDataVencimento(), request.getCodigoTipoDevedor().name(), request.getNumeroContrato(),
                motivo.getId().longValue());

        String xmlResponse = client.excluir(xmlRequest);

        SpcRegistro registro = new SpcRegistro();
        registro.setCliente(cliente);
        registro.setTipoOperacao(TipoOperacaoSpc.EXCLUSAO);
        registro.setTipoPessoa(request.getTipoPessoa());
        registro.setNumeroContrato(request.getNumeroContrato());
        registro.setDataVencimento(request.getDataVencimento());
        registro.setCodigoTipoDevedor(request.getCodigoTipoDevedor());
        registro.setMotivoExclusao(motivo);
        registro.setMensagemRetorno(extrairSucesso(xmlResponse));
        registro = repository.save(registro);

        xmlStorageService.salvarExclusao(registro.getId(), xmlRequest, xmlResponse);

        return toResponseDTO(registro);
    }

    public Page<SpcRegistroResponseDTO> findAll(UUID clienteId, TipoOperacaoSpc tipoOperacao, Pageable pageable){
        Specification<SpcRegistro> spec = null;

        if (clienteId != null) {
            Specification<SpcRegistro> clienteSpec = (root, query, cb) -> cb.equal(root.get("cliente").get("id"), clienteId);
            spec = clienteSpec;
        }

        if (tipoOperacao != null) {
            Specification<SpcRegistro> tipoSpec = (root, query, cb) -> cb.equal(root.get("tipoOperacao"), tipoOperacao);
            spec = spec == null ? tipoSpec : spec.and(tipoSpec);
        }

        return repository.findAll(spec, pageable).map(this::toResponseDTO);
    }

    private SpcRegistroResponseDTO toResponseDTO(SpcRegistro registro){
        SpcRegistroResponseDTO dto = new SpcRegistroResponseDTO();
        dto.setId(registro.getId());
        dto.setClienteId(registro.getCliente().getId());
        dto.setClienteNome(registro.getCliente().getNome());
        dto.setTipoOperacao(registro.getTipoOperacao());
        dto.setTipoPessoa(registro.getTipoPessoa());
        dto.setNumeroContrato(registro.getNumeroContrato());
        dto.setDataCompra(registro.getDataCompra());
        dto.setDataVencimento(registro.getDataVencimento());
        dto.setCodigoTipoDevedor(registro.getCodigoTipoDevedor());
        dto.setValorDebito(registro.getValorDebito());
        if (registro.getNaturezaInclusao() != null) {
            dto.setNaturezaInclusaoId(registro.getNaturezaInclusao().getId());
            dto.setNaturezaInclusaoNome(registro.getNaturezaInclusao().getNome());
        }
        if (registro.getMotivoExclusao() != null) {
            dto.setMotivoExclusaoId(registro.getMotivoExclusao().getId());
            dto.setMotivoExclusaoDescricao(registro.getMotivoExclusao().getDescricao());
        }
        dto.setMensagemRetorno(registro.getMensagemRetorno());
        dto.setCreatedAt(registro.getCreatedAt());
        dto.setCreatedBy(usuarioResumoMapper.toResumoDTO(registro.getCreatedBy()));
        return dto;
    }
}
