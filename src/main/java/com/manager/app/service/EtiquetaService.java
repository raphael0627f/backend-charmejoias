package com.manager.app.service;

import com.manager.app.dto.etiqueta.EtiquetaDTO;
import com.manager.app.dto.etiqueta.EtiquetaProdutoDTO;
import com.manager.app.dto.etiqueta.EtiquetaRequestDTO;
import com.manager.app.entity.Produto;
import com.manager.app.infra.restEnum.Impressora;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EtiquetaService {

    private final ProdutoService produtoService;


    private List<EtiquetaDTO> dataEtiqueta(EtiquetaRequestDTO request){
        List<EtiquetaDTO> etiquetas = new ArrayList<>();
        EtiquetaDTO etiquetaVazia = new EtiquetaDTO(null, null);

        if(request.getInicio() > 1){
            for (int i = 1; i < request.getInicio(); i++) {
                etiquetas.add(etiquetaVazia);
            }
        }

        for(EtiquetaProdutoDTO produtoEtiqueta : request.getProdutos()){
            EtiquetaDTO etiqueta = new EtiquetaDTO();
            Produto produto = produtoService.findById(produtoEtiqueta.getProdutoId());
            etiqueta.setDescricao(produto.getNome());
            etiqueta.setCodigoBarras(produto.getCodigo());

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

            if(request.getImpressora() == Impressora.TINTA) {
                relPath = "/reports/etiqueta/relEtiquetaProdutoTinta.jasper";
            } else {
                relPath = "/reports/etiqueta/relEtiquetaProdutoTermica.jasper";
            }

            InputStream jasperStream = getClass().getResourceAsStream(relPath);
            Map<String, Object> params = new HashMap<>();
            JasperPrint print = JasperFillManager.fillReport(jasperStream, params, dataSource);
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de etiquetas", e);
        }
    }
}
