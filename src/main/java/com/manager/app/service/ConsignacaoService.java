package com.manager.app.service;

import com.manager.app.dto.consignacao.ConsignacaoProdutoRequestDTO;
import com.manager.app.dto.consignacao.ConsignacaoRequestDTO;
import com.manager.app.entity.Cliente;
import com.manager.app.entity.Consignacao;
import com.manager.app.entity.ConsignacaoProduto;
import com.manager.app.entity.Endereco;
import com.manager.app.entity.Produto;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.infra.restEnum.TipoRelatorioConsignacao;
import com.manager.app.infra.validation.ConsignacaoValidation;
import com.manager.app.repository.ConsignacaoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConsignacaoService {

    private final ConsignacaoRepository repository;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final ConsignacaoValidation validation;

    public List<Consignacao> findAll(){
        return repository.findAll();
    }

    public Consignacao findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Consignação não encontrada. id: " + id));
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

        for (ConsignacaoProdutoRequestDTO produtoRequest : request.getProdutos()){
            ConsignacaoProduto produto = new ConsignacaoProduto();

            produto.setProduto(produtoService.findByIdAndAtivo(produtoRequest.getProdutoId()));
            produto.setQuantidade(produtoRequest.getQuantidade());
            produto.setPreco(produtoRequest.getPreco());

            consignacao.adicionarProduto(produto);
        }

        return repository.save(consignacao);
    }

    @Transactional
    public Consignacao update(ConsignacaoRequestDTO request){
        Consignacao consignacaoUpdate = this.findById(request.getId());

        if (!validation.clienteNovo(request.getClienteId())){
            consignacaoUpdate.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));
        } else {
            consignacaoUpdate.setCliente(null);
        }

        consignacaoUpdate.setDescricao(request.getDescricao());

        List<ConsignacaoProduto> produtosAtuais = new ArrayList<>(consignacaoUpdate.getProdutos());

        for (ConsignacaoProdutoRequestDTO produtoRequest : request.getProdutos()){
            ConsignacaoProduto produtoExistente = null;

            for (ConsignacaoProduto produto : consignacaoUpdate.getProdutos()){
                if(produto.getProduto().getId().equals(produtoRequest.getProdutoId())){
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

                novoProduto.setQuantidade(produtoRequest.getQuantidade());
                novoProduto.setPreco(produtoRequest.getPreco());
                novoProduto.setProduto(produtoService.findByIdAndAtivo(produtoRequest.getProdutoId()));
                consignacaoUpdate.adicionarProduto(novoProduto);
            }
        }

        for (ConsignacaoProduto removerProduto : produtosAtuais) {
            consignacaoUpdate.removerProduto(removerProduto);
        }
        return consignacaoUpdate;
    }

    List<Map<String, ?>> dataConsignacao(Consignacao consignacao){
        List<Map<String, ?>> linhas = new ArrayList<>();
        Cliente cliente = consignacao.getCliente();
        Endereco endereco = cliente != null ? cliente.getEndereco() : null;

        for (ConsignacaoProduto consignacaoProduto : consignacao.getProdutos()){
            Map<String, Object> linha = new HashMap<>();
            Produto produto = consignacaoProduto.getProduto();

            linha.put("ID", consignacao.getId() != null ? consignacao.getId().intValue() : null);
            linha.put("DataConsignacao", consignacao.getCreatedAt() != null ? Date.valueOf(consignacao.getCreatedAt().toLocalDate()) : null);
            linha.put("ValorTotal", consignacao.getValorTotal() != null ? consignacao.getValorTotal().doubleValue() : null);
            linha.put("QTDEPecas", consignacao.getQuantidadeTotal());
            linha.put("NuConsig", consignacao.getDescricao());

            linha.put("CodProduto", produto.getCodigo());
            linha.put("NomeProduto", produto.getNome());
            linha.put("ValorProdutoU", consignacaoProduto.getPreco() != null ? consignacaoProduto.getPreco().doubleValue() : null);
            linha.put("QtdeProduto", consignacaoProduto.getQuantidade());
            linha.put("TipoProduto", produto.getCategoria() != null ? produto.getCategoria().getNome() : null);

            if (cliente != null){
                linha.put("CodCliente", cliente.getCodigo());
                linha.put("NomeCliente", cliente.getNome());
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
