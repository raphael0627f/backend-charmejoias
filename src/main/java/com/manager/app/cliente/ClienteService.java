package com.manager.app.cliente;

import com.manager.app.endereco.Endereco;
import com.manager.app.grupo.Grupo;
import com.manager.app.grupo.TipoGrupo;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.grupo.GrupoValidation;
import com.manager.app.shared.specification.SpecificationUtils;
import com.manager.app.grupo.GrupoRepository;
import com.manager.app.shared.ModoBusca;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import com.manager.app.endereco.EnderecoService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository repository;
    private final GrupoRepository grupoRepository;
    private final EnderecoService enderecoService;
    private final ClienteValidation validation;
    private final GrupoValidation grupoValidation;

    public Page<Cliente> findAll(String busca, ModoBusca modoBusca, Boolean ativo, List<Long> gruposIds, Pageable pageable){
        Specification<Cliente> spec = Specification.where(comBusca(busca, modoBusca));

        if (ativo != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ativo"), ativo));
        }

        if (gruposIds != null && !gruposIds.isEmpty()) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return root.join("grupos").get("id").in(gruposIds);
            });
        }

        return repository.findAll(spec, pageable);
    }

    private Specification<Cliente> comBusca(String busca, ModoBusca modoBusca){
        if (busca == null || busca.isBlank()) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> cb.or(
                SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca, modoBusca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("codigo"), busca, modoBusca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("cpf"), busca, modoBusca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("documento"), busca, modoBusca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("telefone"), busca, modoBusca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("celular"), busca, modoBusca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("email"), busca, modoBusca)
        );
    }

    public Cliente findById(UUID id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + id));
    }

    public Cliente findByIdAndAtivo(UUID id){
        return repository.findByIdAndAtivoTrue(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado ou inativo. id: " + id));
    }

    public Cliente findByCodigo(String codigo){
        return repository.findByCodigo(codigo).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. codigo: " + codigo));
    }

    @Transactional
    public Cliente create(Cliente cliente, List<Long> gruposIds){
        validation.codigoExistenteCreate(cliente.getCodigo());
        validarEnderecos(cliente.getEnderecos());
        cliente.getEnderecos().forEach(enderecoService::enriquecerIbge);

        cliente.setGrupos(resolverGrupos(gruposIds, TipoGrupo.CLIENTE));
        return repository.save(cliente);
    }

    @Transactional
    public Cliente update(Cliente cliente, List<Long> gruposIds){
        validation.codigoExistenteUpdate(cliente.getCodigo(), cliente.getId());
        validarEnderecos(cliente.getEnderecos());
        Cliente clienteUpdate = repository.findById(cliente.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + cliente.getId()));
        sincronizarEnderecos(clienteUpdate, cliente.getEnderecos());

        clienteUpdate.setCodigo(cliente.getCodigo());
        clienteUpdate.setReferencia(cliente.getReferencia());
        clienteUpdate.setNome(cliente.getNome());
        clienteUpdate.setFiliacao(cliente.getFiliacao());
        clienteUpdate.setCpf(cliente.getCpf());
        clienteUpdate.setDocumento(cliente.getDocumento());
        clienteUpdate.setGenero(cliente.getGenero());
        clienteUpdate.setTelefone(cliente.getTelefone());
        clienteUpdate.setCelular(cliente.getCelular());
        clienteUpdate.setEmail(cliente.getEmail());
        clienteUpdate.setObservacao(cliente.getObservacao());
        clienteUpdate.setDataNascimento(cliente.getDataNascimento());
        clienteUpdate.setAtivo(cliente.isAtivo());

        clienteUpdate.setGrupos(resolverGrupos(gruposIds, TipoGrupo.CLIENTE));

        return clienteUpdate;
    }

    // Sempre resolve a lista de grupos (mesmo vazia/nula -> lista vazia), pra
    // remover o último grupo de um cliente de fato limpar a relação - antes,
    // gruposIds vazio pulava a atribuição inteira e o grupo antigo nunca saía.
    private List<Grupo> resolverGrupos(List<Long> gruposIds, TipoGrupo tipoEsperado){
        if (grupoValidation.gruposEmpty(gruposIds)) {
            return new ArrayList<>();
        }
        List<Grupo> grupos = grupoRepository.findAllById(gruposIds);
        if (grupos.size() != gruposIds.size()) {
            throw new ObjetoNaoEncontradoException("Um ou mais grupos não encontrados");
        }
        grupoValidation.validarTipo(grupos, tipoEsperado);
        return grupos;
    }

    // cliente precisa ter ao menos um endereço, e exatamente um marcado como principal - é esse
    // principal que Cliente.getEnderecoPrincipal() devolve pros relatórios/NFe/SPC usarem
    private void validarEnderecos(List<Endereco> enderecos){
        if (enderecos == null || enderecos.isEmpty()) {
            throw new ItemInvalidoException("Cliente precisa ter ao menos um endereço");
        }
        long principais = enderecos.stream().filter(Endereco::isPrincipal).count();
        if (principais != 1) {
            throw new ItemInvalidoException("Cliente precisa ter exatamente um endereço marcado como principal");
        }
    }

    // mesmo padrão de sincronização usado em ProdutoService/ConsignacaoService pras suas listas:
    // casa por id (atualiza em vigor), o que não bate em nenhum existente é novo, e o que sobra dos
    // existentes é removido (orphanRemoval cuida do DELETE)
    private void sincronizarEnderecos(Cliente clienteUpdate, List<Endereco> enderecosRequest){
        Map<Long, Endereco> existentesPorId = new HashMap<>();
        for (Endereco existente : clienteUpdate.getEnderecos()) {
            existentesPorId.put(existente.getId(), existente);
        }

        for (Endereco enderecoRequest : enderecosRequest) {
            if (enderecoRequest.getId() != null && existentesPorId.containsKey(enderecoRequest.getId())) {
                Endereco existente = existentesPorId.remove(enderecoRequest.getId());
                existente.setLogradouro(enderecoRequest.getLogradouro());
                existente.setNumero(enderecoRequest.getNumero());
                existente.setComplemento(enderecoRequest.getComplemento());
                existente.setBairro(enderecoRequest.getBairro());
                existente.setMunicipio(enderecoRequest.getMunicipio());
                existente.setUf(enderecoRequest.getUf());
                existente.setCep(enderecoRequest.getCep());
                existente.setIbge(enderecoRequest.getIbge());
                existente.setPrincipal(enderecoRequest.isPrincipal());
                existente.setPontoReferencia(enderecoRequest.getPontoReferencia());
                existente.setDescricao(enderecoRequest.getDescricao());
                existente.setLatitude(enderecoRequest.getLatitude());
                existente.setLongitude(enderecoRequest.getLongitude());
                enderecoService.enriquecerIbge(existente);
            } else {
                enderecoService.enriquecerIbge(enderecoRequest);
                clienteUpdate.adicionarEndereco(enderecoRequest);
            }
        }

        for (Endereco removerEndereco : existentesPorId.values()) {
            clienteUpdate.removerEndereco(removerEndereco);
        }
    }

    @Transactional
    public Cliente activate(UUID id){
        Cliente cliente = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + id));
        cliente.setAtivo(true);
        return cliente;
    }

    @Transactional
    public void deactivate(UUID id){
        Cliente cliente = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + id));
        cliente.setAtivo(false);
    }
}
