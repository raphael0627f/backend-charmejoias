package com.manager.app.spc;

import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.spc.dto.SpcInsumoOpcionalDTO;
import com.manager.app.spc.dto.SpcProdutoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpcCatalogoService {

    private final SpcNaturezaInclusaoRepository naturezaInclusaoRepository;
    private final SpcMotivoExclusaoRepository motivoExclusaoRepository;
    private final SpcProdutoRepository produtoRepository;
    private final SpcProdutoInsumoOpcionalRepository produtoInsumoOpcionalRepository;

    public List<SpcNaturezaInclusao> naturezasInclusao(){
        return naturezaInclusaoRepository.findAll(Sort.by("nome"));
    }

    public List<SpcMotivoExclusao> motivosExclusao(){
        return motivoExclusaoRepository.findAll(Sort.by("descricao"));
    }

    public List<SpcProdutoDTO> produtos(TipoPessoaSpc tipoPessoa){
        List<TipoPessoaProdutoSpc> tipos = List.of(TipoPessoaProdutoSpc.valueOf(tipoPessoa.name()), TipoPessoaProdutoSpc.AMBOS);
        return produtoRepository.findByTipoPessoaInOrderByNome(tipos).stream()
                .map(produto -> new SpcProdutoDTO(produto.getCodigo(), produto.getNome()))
                .toList();
    }

    public List<SpcInsumoOpcionalDTO> insumosOpcionais(Integer codigoProduto){
        if (!produtoRepository.existsById(codigoProduto)) {
            throw new ObjetoNaoEncontradoException("Produto SPC não encontrado no catálogo. código: " + codigoProduto);
        }
        return produtoInsumoOpcionalRepository.findByProdutoCodigoOrderByInsumoNome(codigoProduto).stream()
                .map(insumo -> new SpcInsumoOpcionalDTO(insumo.getInsumoCodigo(), insumo.getInsumoNome()))
                .toList();
    }

    public SpcProduto buscarProduto(Integer codigo){
        return produtoRepository.findById(codigo)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Produto SPC não encontrado no catálogo. código: " + codigo));
    }

    public boolean insumoOpcionalValido(Integer codigoProduto, Integer codigoInsumo){
        return produtoInsumoOpcionalRepository.existsByProdutoCodigoAndInsumoCodigo(codigoProduto, codigoInsumo);
    }

    public SpcNaturezaInclusao buscarNaturezaInclusao(Integer id){
        return naturezaInclusaoRepository.findById(id)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Natureza de inclusão SPC não encontrada. id: " + id));
    }

    public SpcMotivoExclusao buscarMotivoExclusao(Integer id){
        return motivoExclusaoRepository.findById(id)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Motivo de exclusão SPC não encontrado. id: " + id));
    }
}
