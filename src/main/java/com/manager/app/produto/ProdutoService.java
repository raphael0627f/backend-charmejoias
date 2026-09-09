package com.manager.app.produto;

import com.manager.app.produto.dto.PreviewVariacoesRequestDTO;
import com.manager.app.produto.dto.PreviewVariacoesResponseDTO;
import com.manager.app.produto.dto.ProdutoRelacoesDTO;
import com.manager.app.produto.dto.ProdutoVariacaoPreviewDTO;
import com.manager.app.produto.dto.ProdutoVariacaoRequestDTO;
import com.manager.app.produto.dto.VariacaoSelecionadaDTO;
import com.manager.app.grupo.Grupo;
import com.manager.app.shared.NanoIdUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.grupo.GrupoValidation;
import com.manager.app.shared.specification.SpecificationUtils;
import com.manager.app.grupo.GrupoRepository;
import com.manager.app.variacao.ItemVariacao;
import com.manager.app.variacao.ItemVariacaoRepository;
import com.manager.app.variacao.dto.ItemVariacaoDTO;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.manager.app.categoria.CategoriaService;
import com.manager.app.fornecedor.FornecedorService;
import com.manager.app.ncm.NcmService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository repository;
    private final ProdutoVariacaoRepository produtoVariacaoRepository;
    private final ItemVariacaoRepository itemVariacaoRepository;
    private final GrupoRepository grupoRepository;
    private final FornecedorService fornecedorService;
    private final CategoriaService categoriaService;
    private final NcmService ncmService;
    private final ProdutoValidation validation;
    private final GrupoValidation grupoValidation;
    private final ProdutoImagemStorageService imagemStorageService;

    private static final Pattern CODIGO_NUMERICO = Pattern.compile("^\\d{4}$");
    private static final int TAMANHO_CODIGO_BARRAS = 10;

    public Page<Produto> findAll(String busca, Boolean ativo, Long categoriaId, Long fornecedorId, Pageable pageable){
        Specification<Produto> spec = comBusca(busca);

        if (ativo != null) {
            Specification<Produto> ativoSpec = (root, query, cb) -> cb.equal(root.get("ativo"), ativo);
            spec = spec == null ? ativoSpec : spec.and(ativoSpec);
        }

        if (categoriaId != null) {
            Specification<Produto> categoriaSpec = (root, query, cb) -> cb.equal(root.get("categoria").get("id"), categoriaId);
            spec = spec == null ? categoriaSpec : spec.and(categoriaSpec);
        }

        if (fornecedorId != null) {
            Specification<Produto> fornecedorSpec = (root, query, cb) -> cb.equal(root.get("fornecedor").get("id"), fornecedorId);
            spec = spec == null ? fornecedorSpec : spec.and(fornecedorSpec);
        }

        return repository.findAll(spec, pageable);
    }

    private Specification<Produto> comBusca(String busca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> cb.or(
                SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("sku"), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("referencia"), busca)
        );
    }

    public Produto findById(UUID id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Produto não encontrado. id: " + id));
    }

    public Produto findByIdAndAtivo(UUID id){
        return repository.findByIdAndAtivoTrue(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Produto não encontrado ou inativo. id: " + id));
    }

    public Produto findBySku(String sku){
        return repository.findBySkuAndAtivoTrue(sku).orElseThrow(() -> new ObjetoNaoEncontradoException("Produto não encontrado. sku: " + sku));
    }

    public ProdutoVariacao findVariacaoById(Long id){
        return produtoVariacaoRepository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Variação de produto não encontrada. id: " + id));
    }

    public String proximoSku(){
        Set<Integer> usados = new HashSet<>();
        for (String sku : repository.findAllSkus()) {
            if (sku == null || sku.length() < 4) continue;
            String prefixo = sku.substring(0, 4);
            if (CODIGO_NUMERICO.matcher(prefixo).matches()) {
                usados.add(Integer.parseInt(prefixo));
            }
        }

        int proximo = 1;
        while (usados.contains(proximo)) {
            proximo++;
        }
        return String.format("%04d", proximo);
    }

    public PreviewVariacoesResponseDTO previewCombinacoes(PreviewVariacoesRequestDTO dto){
        Set<Long> variacaoIdsVistos = new HashSet<>();
        List<List<ItemVariacao>> grupos = new ArrayList<>();

        for (VariacaoSelecionadaDTO selecao : dto.getVariacoesSelecionadas()) {
            if (!variacaoIdsVistos.add(selecao.getVariacaoId())) {
                throw new ItemInvalidoException("Variação selecionada mais de uma vez: " + selecao.getVariacaoId());
            }

            Map<Long, ItemVariacao> itensPorId = itemVariacaoRepository.findAllById(selecao.getItensVariacaoIds()).stream()
                    .collect(Collectors.toMap(ItemVariacao::getId, i -> i));

            List<ItemVariacao> itensOrdenados = new ArrayList<>();
            for (Long itemId : selecao.getItensVariacaoIds()) {
                ItemVariacao item = itensPorId.get(itemId);
                if (item == null) {
                    throw new ObjetoNaoEncontradoException("Item de variação não encontrado. id: " + itemId);
                }
                if (!item.getVariacao().getId().equals(selecao.getVariacaoId())) {
                    throw new ItemInvalidoException("Item " + itemId + " não pertence à variação " + selecao.getVariacaoId());
                }
                itensOrdenados.add(item);
            }
            grupos.add(itensOrdenados);
        }

        List<List<ItemVariacao>> combinacoes = cartesianProduct(grupos);

        List<ProdutoVariacaoPreviewDTO> resultado = combinacoes.stream()
                .map(combo -> new ProdutoVariacaoPreviewDTO(montarSku(dto.getSkuPai(), combo), toItemVariacaoDTOs(combo)))
                .toList();

        return new PreviewVariacoesResponseDTO(resultado);
    }

    private List<List<ItemVariacao>> cartesianProduct(List<List<ItemVariacao>> grupos){
        List<List<ItemVariacao>> resultado = new ArrayList<>();
        resultado.add(new ArrayList<>());
        for (List<ItemVariacao> grupo : grupos) {
            List<List<ItemVariacao>> novo = new ArrayList<>();
            for (List<ItemVariacao> combinacaoParcial : resultado) {
                for (ItemVariacao item : grupo) {
                    List<ItemVariacao> combinacao = new ArrayList<>(combinacaoParcial);
                    combinacao.add(item);
                    novo.add(combinacao);
                }
            }
            resultado = novo;
        }
        return resultado;
    }

    private String montarSku(String skuPai, List<ItemVariacao> itens){
        return skuPai + "-" + itens.stream().map(ItemVariacao::getValor).collect(Collectors.joining(""));
    }

    private List<ItemVariacaoDTO> toItemVariacaoDTOs(List<ItemVariacao> itens){
        return itens.stream().map(item -> {
            ItemVariacaoDTO itemDTO = new ItemVariacaoDTO();
            itemDTO.setId(item.getId());
            itemDTO.setNome(item.getNome());
            itemDTO.setValor(item.getValor());
            itemDTO.setAtivo(item.isAtivo());
            return itemDTO;
        }).toList();
    }

    private List<ItemVariacao> resolverItensOrdenados(List<Long> itensVariacaoIds){
        Map<Long, ItemVariacao> itensPorId = itemVariacaoRepository.findAllById(itensVariacaoIds).stream()
                .collect(Collectors.toMap(ItemVariacao::getId, i -> i));

        List<ItemVariacao> itensOrdenados = new ArrayList<>();
        Set<Long> variacoesUsadas = new HashSet<>();
        for (Long itemId : itensVariacaoIds) {
            ItemVariacao item = itensPorId.get(itemId);
            if (item == null) {
                throw new ObjetoNaoEncontradoException("Item de variação não encontrado. id: " + itemId);
            }
            if (!variacoesUsadas.add(item.getVariacao().getId())) {
                throw new ItemInvalidoException("Mais de um item da mesma variação na combinação: " + item.getVariacao().getNome());
            }
            itensOrdenados.add(item);
        }
        return itensOrdenados;
    }

    private void validarSkuVariacaoUnico(String sku, Long idAtual){
        boolean existeEmProduto = repository.existsBySku(sku);
        boolean existeEmVariacao = idAtual == null
                ? produtoVariacaoRepository.existsBySku(sku)
                : produtoVariacaoRepository.existsBySkuAndIdNot(sku, idAtual);
        if (existeEmProduto || existeEmVariacao) {
            throw new ObjetoExistenteException("SKU já existente: " + sku);
        }
    }

    public boolean skuDisponivel(String sku, UUID ignorarProdutoId, Long ignorarProdutoVariacaoId){
        boolean existeEmProduto = ignorarProdutoId != null
                ? repository.existsBySkuAndIdNot(sku, ignorarProdutoId)
                : repository.existsBySku(sku);
        boolean existeEmVariacao = ignorarProdutoVariacaoId != null
                ? produtoVariacaoRepository.existsBySkuAndIdNot(sku, ignorarProdutoVariacaoId)
                : produtoVariacaoRepository.existsBySku(sku);
        return !existeEmProduto && !existeEmVariacao;
    }

    private String gerarCodigoBarrasUnico(){
        String codigo;
        do {
            codigo = NanoIdUtils.generate(TAMANHO_CODIGO_BARRAS);
        } while (produtoVariacaoRepository.existsByCodigoBarras(codigo));
        return codigo;
    }

    private void validarCombinacoesDuplicadas(List<ProdutoVariacaoRequestDTO> variacoesRequest){
        Set<Set<Long>> combinacoesVistas = new HashSet<>();
        for (ProdutoVariacaoRequestDTO reqVar : variacoesRequest) {
            Set<Long> chave = new LinkedHashSet<>(reqVar.getItensVariacaoIds());
            if (!combinacoesVistas.add(chave)) {
                throw new ItemInvalidoException("Combinação de variação duplicada no request");
            }
        }
    }

    private ProdutoVariacao montarNovaVariacao(ProdutoVariacaoRequestDTO reqVar){
        List<ItemVariacao> itensOrdenados = resolverItensOrdenados(reqVar.getItensVariacaoIds());
        validarSkuVariacaoUnico(reqVar.getSku(), null);

        ProdutoVariacao pv = new ProdutoVariacao();
        pv.setSku(reqVar.getSku());
        pv.setPrecoCusto(reqVar.getPrecoCusto());
        pv.setPrecoVenda(reqVar.getPrecoVenda());
        pv.setQuantidadeEstoque(reqVar.getQuantidadeEstoque());
        pv.setAtivo(reqVar.isAtivo());
        pv.setCodigoBarras(gerarCodigoBarrasUnico());
        itensOrdenados.forEach(item -> {
            ProdutoVariacaoItem pvi = new ProdutoVariacaoItem();
            pvi.setItemVariacao(item);
            pv.adicionarItem(pvi);
        });
        return pv;
    }

    @Transactional
    public Produto create(Produto produto, ProdutoRelacoesDTO relacoes, List<ProdutoVariacaoRequestDTO> variacoesRequest){
        validation.skuExistenteCreate(produto.getSku());

        produto.setCategoria(categoriaService.findById(relacoes.getCategoriaId()));
        produto.setFornecedor(relacoes.getFornecedorId() != null
                ? fornecedorService.findById(relacoes.getFornecedorId())
                : null);
        produto.setNcm(relacoes.getNcmCodigo() != null
                ? ncmService.findById(relacoes.getNcmCodigo())
                : null);

        if(!grupoValidation.gruposEmpty(relacoes.getGruposIds())){
            List<Grupo> grupos = grupoRepository.findAllById(relacoes.getGruposIds());
            if (grupos.size() != relacoes.getGruposIds().size()) {
                throw new ObjetoNaoEncontradoException("Um ou mais grupos não encontrados");
            }
            produto.setGrupos(grupos);
        }

        if (produto.getQuantidadeEstoque() == null) {
            produto.setQuantidadeEstoque(0);
        }

        if (produto.getTipo() == TipoProduto.SIMPLES) {
            if (variacoesRequest != null && !variacoesRequest.isEmpty()) {
                throw new ItemInvalidoException("Produto simples não pode ter variações");
            }
            if (produto.getPrecoVenda() == null) {
                throw new ItemInvalidoException("Preço de venda é obrigatório para produto simples");
            }
        } else {
            if (variacoesRequest == null || variacoesRequest.isEmpty()) {
                throw new ItemInvalidoException("Produto variável precisa de ao menos uma combinação de variação");
            }
            validarCombinacoesDuplicadas(variacoesRequest);
            for (ProdutoVariacaoRequestDTO reqVar : variacoesRequest) {
                produto.adicionarVariacao(montarNovaVariacao(reqVar));
            }
        }

        return repository.save(produto);
    }

    @Transactional
    public Produto update(Produto produto, ProdutoRelacoesDTO relacoes, List<ProdutoVariacaoRequestDTO> variacoesRequest){
        validation.skuExistenteUpdate(produto.getSku(), produto.getId());
        Produto produtoUpdate = repository.findById(produto.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Produto não encontrado. id: " + produto.getId()));

        produtoUpdate.setSku(produto.getSku());
        produtoUpdate.setReferencia(produto.getReferencia());
        produtoUpdate.setNome(produto.getNome());
        produtoUpdate.setTipo(produto.getTipo());
        produtoUpdate.setPrecoCusto(produto.getPrecoCusto());
        produtoUpdate.setPrecoVenda(produto.getPrecoVenda());
        produtoUpdate.setQuantidadeEstoque(produto.getQuantidadeEstoque() != null ? produto.getQuantidadeEstoque() : 0);
        produtoUpdate.setAtivo(produto.isAtivo());
        produtoUpdate.setObservacoes(produto.getObservacoes());
        produtoUpdate.setOrigemProduto(produto.getOrigemProduto());
        produtoUpdate.setUnidadeComercial(produto.getUnidadeComercial());
        produtoUpdate.setCodigoSituacaoTributariaIcms(produto.getCodigoSituacaoTributariaIcms());
        produtoUpdate.setCodigoSituacaoTributariaPis(produto.getCodigoSituacaoTributariaPis());
        produtoUpdate.setCodigoSituacaoTributariaCofins(produto.getCodigoSituacaoTributariaCofins());
        produtoUpdate.setCategoria(categoriaService.findById(relacoes.getCategoriaId()));
        produtoUpdate.setFornecedor(relacoes.getFornecedorId() != null
                ? fornecedorService.findById(relacoes.getFornecedorId())
                : null);
        produtoUpdate.setNcm(relacoes.getNcmCodigo() != null
                ? ncmService.findById(relacoes.getNcmCodigo())
                : null);

        if(!grupoValidation.gruposEmpty(relacoes.getGruposIds())){
            List<Grupo> grupos = grupoRepository.findAllById(relacoes.getGruposIds());
            if (grupos.size() != relacoes.getGruposIds().size()) {
                throw new ObjetoNaoEncontradoException("Um ou mais grupos não encontrados");
            }
            produtoUpdate.setGrupos(grupos);
        }

        if (produtoUpdate.getTipo() == TipoProduto.SIMPLES) {
            if (variacoesRequest != null && !variacoesRequest.isEmpty()) {
                throw new ItemInvalidoException("Produto simples não pode ter variações");
            }
            if (produtoUpdate.getPrecoVenda() == null) {
                throw new ItemInvalidoException("Preço de venda é obrigatório para produto simples");
            }
            List<ProdutoVariacao> antigas = List.copyOf(produtoUpdate.getVariacoes());
            antigas.forEach(produtoUpdate::removerVariacao);
        } else {
            if (variacoesRequest == null || variacoesRequest.isEmpty()) {
                throw new ItemInvalidoException("Produto variável precisa de ao menos uma combinação de variação");
            }
            validarCombinacoesDuplicadas(variacoesRequest);

            Map<Long, ProdutoVariacao> existentesPorId = new HashMap<>();
            for (ProdutoVariacao existente : produtoUpdate.getVariacoes()) {
                existentesPorId.put(existente.getId(), existente);
            }

            for (ProdutoVariacaoRequestDTO reqVar : variacoesRequest) {
                List<ItemVariacao> itensOrdenados = resolverItensOrdenados(reqVar.getItensVariacaoIds());

                if (reqVar.getId() != null && existentesPorId.containsKey(reqVar.getId())) {
                    ProdutoVariacao existente = existentesPorId.remove(reqVar.getId());
                    if (!existente.getSku().equals(reqVar.getSku())) {
                        validarSkuVariacaoUnico(reqVar.getSku(), existente.getId());
                    }
                    existente.setSku(reqVar.getSku());
                    existente.setPrecoCusto(reqVar.getPrecoCusto());
                    existente.setPrecoVenda(reqVar.getPrecoVenda());
                    existente.setQuantidadeEstoque(reqVar.getQuantidadeEstoque());
                    existente.setAtivo(reqVar.isAtivo());

                    Set<Long> itemIdsDesejados = itensOrdenados.stream().map(ItemVariacao::getId).collect(Collectors.toSet());
                    List<ProdutoVariacaoItem> paraRemover = existente.getItens().stream()
                            .filter(pvi -> !itemIdsDesejados.contains(pvi.getItemVariacao().getId()))
                            .toList();
                    paraRemover.forEach(existente.getItens()::remove);

                    Set<Long> itemIdsRestantes = existente.getItens().stream()
                            .map(pvi -> pvi.getItemVariacao().getId())
                            .collect(Collectors.toSet());
                    for (ItemVariacao item : itensOrdenados) {
                        if (!itemIdsRestantes.contains(item.getId())) {
                            ProdutoVariacaoItem pvi = new ProdutoVariacaoItem();
                            pvi.setItemVariacao(item);
                            existente.adicionarItem(pvi);
                        }
                    }
                } else {
                    produtoUpdate.adicionarVariacao(montarNovaVariacao(reqVar));
                }
            }

            List<ProdutoVariacao> removidas = List.copyOf(existentesPorId.values());
            removidas.forEach(produtoUpdate::removerVariacao);
        }

        return produtoUpdate;
    }

    @Transactional
    public Produto activate(UUID id){
        Produto produto = this.findById(id);
        produto.setAtivo(true);
        return produto;
    }

    @Transactional
    public void deactivate(UUID id){
        Produto produto = this.findById(id);
        produto.setAtivo(false);
        produto.getVariacoes().forEach(v -> v.setAtivo(false));
    }

    @Transactional
    public Produto atualizarImagem(UUID id, MultipartFile imagem){
        Produto produto = this.findById(id);
        if (produto.getImagemUrl() != null) {
            imagemStorageService.excluir(produto.getImagemUrl());
        }
        produto.setImagemUrl(imagemStorageService.salvar(id, imagem));
        return produto;
    }

    public byte[] carregarImagem(UUID id){
        Produto produto = this.findById(id);
        if (produto.getImagemUrl() == null) {
            throw new ObjetoNaoEncontradoException("Produto não possui imagem cadastrada. id: " + id);
        }
        return imagemStorageService.carregar(produto.getImagemUrl());
    }

    @Transactional
    public void removerImagem(UUID id){
        Produto produto = this.findById(id);
        if (produto.getImagemUrl() != null) {
            imagemStorageService.excluir(produto.getImagemUrl());
            produto.setImagemUrl(null);
        }
    }
}
