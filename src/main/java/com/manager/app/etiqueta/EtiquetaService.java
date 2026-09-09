package com.manager.app.etiqueta;

import com.manager.app.etiqueta.dto.EtiquetaDTO;
import com.manager.app.etiqueta.dto.EtiquetaProdutoDTO;
import com.manager.app.etiqueta.dto.EtiquetaRequestDTO;
import com.manager.app.joia.Joia;
import com.manager.app.produto.Produto;
import com.manager.app.produto.ProdutoVariacao;
import com.manager.app.shared.TipoItem;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;
import com.manager.app.joia.JoiaService;
import com.manager.app.produto.ProdutoService;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EtiquetaService {

    private final ProdutoService produtoService;
    private final JoiaService joiaService;

    private List<EtiquetaDTO> dataEtiqueta(EtiquetaRequestDTO request){
        List<EtiquetaDTO> etiquetas = new ArrayList<>();
        EtiquetaDTO etiquetaVazia = new EtiquetaDTO(null, null);

        if(request.getInicio() > 1){
            for (int i = 1; i < request.getInicio(); i++) {
                etiquetas.add(etiquetaVazia);
            }
        }

        boolean isJoia = request.getTipo() == TipoItem.JOIA;

        for(EtiquetaProdutoDTO produtoEtiqueta : request.getProdutos()){
            EtiquetaDTO etiqueta = new EtiquetaDTO();

            if (isJoia) {
                Joia joia = joiaService.findById(produtoEtiqueta.getProdutoId());
                etiqueta.setDescricao(joia.getNome());
                etiqueta.setCodigoBarras(joia.getCodigo());
            } else if (produtoEtiqueta.getProdutoVariacaoId() != null) {
                ProdutoVariacao variacao = produtoService.findVariacaoById(produtoEtiqueta.getProdutoVariacaoId());
                etiqueta.setDescricao(variacao.getProduto().getNome());
                etiqueta.setCodigoBarras(variacao.getCodigoBarras());
            } else {
                Produto produto = produtoService.findById(produtoEtiqueta.getProdutoId());
                etiqueta.setDescricao(produto.getNome());
                etiqueta.setCodigoBarras(produto.getSku());
            }

            for (int i = 0; i < produtoEtiqueta.getQuantidade(); i++) {
                etiquetas.add(etiqueta);
            }
        }

        return etiquetas;
    }

    public byte[] gerarRelatorio(EtiquetaRequestDTO request){
        try {
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(dataEtiqueta(request));
            String relPath;
            Map<String, Object> params = new HashMap<>();

            if(request.getImpressora() == Impressora.TINTA) {
                relPath = "/reports/etiqueta/relEtiquetaProdutoBrincoCharmeJoias.jasper";
            } else {
                relPath = "/reports/etiqueta/relEtiquetaProdutoAnelCharmeJoias.jasper";
                //params.put("LOGO", getClass().getResourceAsStream("/images/logo_etiqueta.png"));
            }

            InputStream jasperStream = getClass().getResourceAsStream(relPath);
            JasperPrint print = JasperFillManager.fillReport(jasperStream, params, dataSource);
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de etiquetas", e);
        }
    }
}
