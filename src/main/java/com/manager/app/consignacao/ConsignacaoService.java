package com.manager.app.consignacao;

import com.manager.app.consignacao.dto.ConsignacaoProdutoRequestDTO;
import com.manager.app.consignacao.dto.ConsignacaoRequestDTO;
import com.manager.app.acertojoia.AcertoJoiaConsignacao;
import com.manager.app.cliente.Cliente;
import com.manager.app.endereco.Endereco;
import com.manager.app.joia.Joia;
import com.manager.app.produto.Produto;
import com.manager.app.produto.ProdutoVariacao;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.TipoItem;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import com.manager.app.cliente.ClienteService;
import com.manager.app.joia.JoiaService;
import com.manager.app.produto.ProdutoService;

import java.io.InputStream;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsignacaoService {

    private static final List<String> ORDEM_CATEGORIAS_PADRAO = List.of(
            "COSMÉTICOS", "ACESSÓRIOS", "LINGERIES", "FANTASIAS", "CALCINHAS", "CUECAS"
    );

    private static final List<String> ORDEM_CATEGORIAS_JOIA = List.of(
            "ANÉIS", "BRINCOS", "CORDÕES", "PINGENTES", "PULSEIRAS", "TORNOZELEIRAS", "ACESSÓRIOS"
    );

    private final ConsignacaoRepository repository;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final JoiaService joiaService;
    private final ConsignacaoValidation validation;

    public Page<Consignacao> findAll(String busca, Boolean temCliente, Boolean temAcerto, Boolean confirmada, LocalDate dataInicio, LocalDate dataFim, Pageable pageable){
        Specification<Consignacao> spec = comBusca(busca);

        if (temCliente != null) {
            Specification<Consignacao> temClienteSpec = (root, query, cb) -> temCliente
                    ? cb.isNotNull(root.get("cliente"))
                    : cb.isNull(root.get("cliente"));
            spec = spec == null ? temClienteSpec : spec.and(temClienteSpec);
        }

        if (temAcerto != null) {
            Specification<Consignacao> temAcertoSpec = (root, query, cb) -> {
                Subquery<Long> subquery = query.subquery(Long.class);
                var acertoConsignacaoRoot = subquery.from(AcertoJoiaConsignacao.class);
                subquery.select(acertoConsignacaoRoot.get("id"))
                        .where(cb.equal(acertoConsignacaoRoot.get("consignacao"), root));
                return temAcerto ? cb.exists(subquery) : cb.not(cb.exists(subquery));
            };
            spec = spec == null ? temAcertoSpec : spec.and(temAcertoSpec);
        }

        if (confirmada != null) {
            Specification<Consignacao> confirmadaSpec = (root, query, cb) -> cb.equal(root.get("confirmada"), confirmada);
            spec = spec == null ? confirmadaSpec : spec.and(confirmadaSpec);
        }

        if (dataInicio != null) {
            Specification<Consignacao> dataInicioSpec = (root, query, cb) ->
                    SpecificationUtils.criadoAPartirDe(cb, root.get("createdAt"), dataInicio);
            spec = spec == null ? dataInicioSpec : spec.and(dataInicioSpec);
        }

        if (dataFim != null) {
            Specification<Consignacao> dataFimSpec = (root, query, cb) ->
                    SpecificationUtils.criadoAte(cb, root.get("createdAt"), dataFim);
            spec = spec == null ? dataFimSpec : spec.and(dataFimSpec);
        }

        return repository.findAll(spec, pageable);
    }

    private Specification<Consignacao> comBusca(String busca){
        if (busca == null || busca.isBlank()) return null;
        Long comoNumero = SpecificationUtils.comoLong(busca);
        return (root, query, cb) -> {
            var cliente = root.join("cliente", JoinType.LEFT);
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>(List.of(
                    SpecificationUtils.likeIgnoreCase(cb, root.get("descricao"), busca),
                    SpecificationUtils.likeIgnoreCase(cb, cliente.get("codigo"), busca),
                    SpecificationUtils.likeIgnoreCase(cb, cliente.get("nome"), busca)
            ));
            if (comoNumero != null) {
                predicates.add(cb.equal(root.get("id"), comoNumero));
            }
            return cb.or(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    public Consignacao findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Consignação não encontrada. id: " + id));
    }

    private void preencherItem(ConsignacaoProduto produto, ConsignacaoProdutoRequestDTO produtoRequest, TipoItem tipo){
        validation.validarItem(tipo, produtoRequest.getProdutoId(), produtoRequest.getJoiaId(), produtoRequest.getProdutoVariacaoId());

        if (tipo == TipoItem.JOIA){
            produto.setJoia(joiaService.findByIdAndAtivo(produtoRequest.getJoiaId()));
            produto.setProduto(null);
            produto.setProdutoVariacao(null);
        } else {
            var produtoEncontrado = produtoService.findByIdAndAtivo(produtoRequest.getProdutoId());
            produto.setProduto(produtoEncontrado);
            produto.setJoia(null);
            produto.setProdutoVariacao(resolverProdutoVariacao(produtoRequest.getProdutoVariacaoId(), produtoEncontrado.getId()));
        }
    }

    private ProdutoVariacao resolverProdutoVariacao(Long produtoVariacaoId, UUID produtoId){
        if (produtoVariacaoId == null) return null;
        ProdutoVariacao variacao = produtoService.findVariacaoById(produtoVariacaoId);
        if (!variacao.getProduto().getId().equals(produtoId)) {
            throw new ItemInvalidoException("A combinação informada não pertence ao produto selecionado");
        }
        return variacao;
    }

    @Transactional
    public Consignacao create(ConsignacaoRequestDTO request){
        Consignacao consignacao = new Consignacao();

        if (!validation.clienteNovo(request.getClienteId())){
            consignacao.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));
        } else {
            consignacao.setCliente(null);
        }

        consignacao.setDescricao(request.getDescricao());
        consignacao.setTipo(request.getTipo());
        consignacao.setDataEntrega(request.getDataEntrega());
        consignacao.setDataPreAcertoConsignacao(request.getDataPreAcertoConsignacao());
        consignacao.setDataAcertoConsignacao(request.getDataAcertoConsignacao());

        for (ConsignacaoProdutoRequestDTO produtoRequest : request.getProdutos()){
            ConsignacaoProduto produto = new ConsignacaoProduto();

            preencherItem(produto, produtoRequest, request.getTipo());
            produto.setQuantidade(produtoRequest.getQuantidade());
            produto.setPreco(produtoRequest.getPreco());

            consignacao.adicionarProduto(produto);
        }

        return repository.save(consignacao);
    }

    @Transactional
    public Consignacao update(ConsignacaoRequestDTO request){
        Consignacao consignacaoUpdate = this.findById(request.getId());

        if (Boolean.TRUE.equals(consignacaoUpdate.getConfirmada())) {
            throw new ObjetoEmUsoException("Consignação confirmada não pode ser editada. id: " + consignacaoUpdate.getId());
        }

        if (!validation.clienteNovo(request.getClienteId())){
            consignacaoUpdate.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));
        } else {
            consignacaoUpdate.setCliente(null);
        }

        consignacaoUpdate.setDescricao(request.getDescricao());
        consignacaoUpdate.setTipo(request.getTipo());
        consignacaoUpdate.setDataEntrega(request.getDataEntrega());
        consignacaoUpdate.setDataPreAcertoConsignacao(request.getDataPreAcertoConsignacao());
        consignacaoUpdate.setDataAcertoConsignacao(request.getDataAcertoConsignacao());

        List<ConsignacaoProduto> produtosAtuais = new ArrayList<>(consignacaoUpdate.getProdutos());

        for (ConsignacaoProdutoRequestDTO produtoRequest : request.getProdutos()){
            ConsignacaoProduto produtoExistente = null;

            for (ConsignacaoProduto produto : consignacaoUpdate.getProdutos()){
                Long variacaoAtualId = produto.getProdutoVariacao() != null ? produto.getProdutoVariacao().getId() : null;
                boolean mesmaVariacao = Objects.equals(variacaoAtualId, produtoRequest.getProdutoVariacaoId());
                boolean mesmoProduto = produto.getProduto() != null && produto.getProduto().getId().equals(produtoRequest.getProdutoId()) && mesmaVariacao;
                boolean mesmaJoia = produto.getJoia() != null && produto.getJoia().getId().equals(produtoRequest.getJoiaId());
                if (mesmoProduto || mesmaJoia) {
                    produtoExistente = produto;
                    break;
                }
            }

            if(produtoExistente != null){

                if (!produtoExistente.getQuantidade().equals(produtoRequest.getQuantidade())) {
                    produtoExistente.setQuantidade(produtoRequest.getQuantidade());
                }

                if (produtoExistente.getPreco().compareTo(produtoRequest.getPreco()) != 0) {
                    produtoExistente.setPreco(produtoRequest.getPreco());
                }

                produtosAtuais.remove(produtoExistente);
            } else {
                ConsignacaoProduto novoProduto = new ConsignacaoProduto();

                preencherItem(novoProduto, produtoRequest, request.getTipo());
                novoProduto.setQuantidade(produtoRequest.getQuantidade());
                novoProduto.setPreco(produtoRequest.getPreco());
                consignacaoUpdate.adicionarProduto(novoProduto);
            }
        }

        for (ConsignacaoProduto removerProduto : produtosAtuais) {
            consignacaoUpdate.removerProduto(removerProduto);
        }
        return consignacaoUpdate;
    }

    @Transactional
    public Consignacao confirmar(Long id){
        Consignacao consignacao = this.findById(id);

        if (Boolean.TRUE.equals(consignacao.getConfirmada())) {
            throw new ItemInvalidoException("Consignação já está confirmada. id: " + id);
        }

        if (consignacao.getCliente() == null) {
            throw new ItemInvalidoException("Consignação sem cliente não pode ser confirmada. id: " + id);
        }

        consignacao.setConfirmada(true);
        return consignacao;
    }

    private String categoriaDe(ConsignacaoProduto item){
        if (item.getProduto() != null){
            return item.getProduto().getCategoria() != null ? item.getProduto().getCategoria().getNome() : "";
        }
        return item.getJoia().getCategoria() != null ? item.getJoia().getCategoria().getNome() : "";
    }

    private int indiceCategoria(String categoria, List<String> ordem){
        for (int i = 0; i < ordem.size(); i++){
            if (ordem.get(i).equalsIgnoreCase(categoria.trim())) return i;
        }
        return Integer.MAX_VALUE;
    }

    private long codigoNumerico(String codigo){
        if (codigo == null) return Long.MAX_VALUE;
        String digitos = codigo.replaceAll("\\D", "");
        if (digitos.isBlank()) return Long.MAX_VALUE;
        try {
            return Long.parseLong(digitos);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private List<ConsignacaoProduto> ordenarProdutos(Consignacao consignacao){
        List<ConsignacaoProduto> produtos = new ArrayList<>(consignacao.getProdutos());
        boolean joia = consignacao.getTipo() == TipoItem.JOIA;
        List<String> ordemCategorias = joia ? ORDEM_CATEGORIAS_JOIA : ORDEM_CATEGORIAS_PADRAO;

        Comparator<ConsignacaoProduto> porCategoria = Comparator
                .comparingInt((ConsignacaoProduto item) -> indiceCategoria(categoriaDe(item), ordemCategorias))
                .thenComparing(item -> categoriaDe(item).toLowerCase());

        Comparator<ConsignacaoProduto> porItem = joia
                ? Comparator.comparingLong(item -> codigoNumerico(item.getJoia().getCodigo()))
                : Comparator.comparing(item -> item.getProduto().getNome() == null ? "" : item.getProduto().getNome().toLowerCase());

        produtos.sort(porCategoria.thenComparing(porItem));
        return produtos;
    }

    public List<Map<String, ?>> dataConsignacao(Consignacao consignacao){
        List<Map<String, ?>> linhas = new ArrayList<>();
        Cliente cliente = consignacao.getCliente();
        Endereco endereco = cliente != null ? cliente.getEndereco() : null;

        for (ConsignacaoProduto consignacaoProduto : ordenarProdutos(consignacao)){
            Map<String, Object> linha = new HashMap<>();
            Produto produto = consignacaoProduto.getProduto();
            Joia joia = consignacaoProduto.getJoia();
            ProdutoVariacao variacao = consignacaoProduto.getProdutoVariacao();

            String codigo = variacao != null ? variacao.getSku() : (produto != null ? produto.getSku() : joia.getCodigo());
            String nome = produto != null ? produto.getNome() : joia.getNome();
            String categoria = produto != null ? (produto.getCategoria() != null ? produto.getCategoria().getNome() : null) : (joia.getCategoria() != null ? joia.getCategoria().getNome() : null);

            linha.put("ID", consignacao.getId() != null ? consignacao.getId().intValue() : null);
            linha.put("DataConsignacao", consignacao.getCreatedAt() != null ? Date.valueOf(consignacao.getCreatedAt().toLocalDate()) : null);
            linha.put("ValorTotal", consignacao.getValorTotal() != null ? consignacao.getValorTotal().doubleValue() : null);
            linha.put("QTDEPecas", consignacao.getQuantidadeTotal());
            linha.put("NuConsig", consignacao.getDescricao());

            linha.put("CodProduto", codigo);
            linha.put("NomeProduto", nome);
            linha.put("ValorProdutoU", consignacaoProduto.getPreco() != null ? consignacaoProduto.getPreco().doubleValue() : null);
            linha.put("QtdeProduto", consignacaoProduto.getQuantidade());
            linha.put("TipoProduto", categoria);

            if (cliente != null){
                linha.put("CodCliente", cliente.getCodigo());
                linha.put("NomeCliente", cliente.getNome());
                linha.put("Nome", cliente.getNome());
                linha.put("CPF_CNPJ", cliente.getCpf());
                linha.put("Identidade", cliente.getDocumento());
                linha.put("Celular1", cliente.getCelular());
                linha.put("Celular2", cliente.getTelefone());
            }

            if (endereco != null){
                linha.put("CEP", endereco.getCep());
                linha.put("Endereco", endereco.getLogradouro());
                linha.put("Complemento", endereco.getComplemento());
                linha.put("NuCasa", endereco.getNumero());
                linha.put("Cidade", endereco.getMunicipio());
                linha.put("Bairro", endereco.getBairro());
                linha.put("UF", endereco.getUf());
            }

            linhas.add(linha);
        }

        return linhas;
    }

    public byte[] gerarRelatorio(Long id, TipoRelatorioConsignacao tipo){
        try {
            Consignacao consignacao = this.findById(id);
            JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(dataConsignacao(consignacao));
            boolean semCliente = consignacao.getCliente() == null;

            String relPath;
            if (tipo == TipoRelatorioConsignacao.JOIAS) {
                relPath = semCliente
                        ? "/reports/consignacao/relConsigNovoJoias.jasper"
                        : "/reports/consignacao/relConsigJoias.jasper";
            } else {
                relPath = semCliente
                        ? "/reports/consignacao/relConsigNovo.jasper"
                        : "/reports/consignacao/relConsig.jasper";
            }

            InputStream jasperStream = getClass().getResourceAsStream(relPath);
            Map<String, Object> params = new HashMap<>();
            JasperPrint print = JasperFillManager.fillReport(jasperStream, params, dataSource);
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de consignação", e);
        }
    }

}
