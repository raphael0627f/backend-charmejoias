package com.manager.app.notafiscal;

import com.manager.app.notafiscal.dto.CfopDisponivelDTO;
import com.manager.app.notafiscal.dto.NotaFiscalCancelamentoRequestDTO;
import com.manager.app.notafiscal.dto.NotaFiscalCorrecaoRequestDTO;
import com.manager.app.notafiscal.dto.NotaFiscalRequestDTO;
import com.manager.app.notafiscal.dto.NotaFiscalResponseDTO;
import com.manager.app.notafiscal.dto.XmlArquivoResponseDTO;
import com.manager.app.shared.exception.ItemInvalidoException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/notas-fiscais")
public class NotaFiscalController {

    private final NotaFiscalService service;
    private final NotaFiscalMapper mapper;
    private final NotaFiscalXmlStorageService xmlStorageService;
    private final DanfeGeneratorService danfeGeneratorService;

    @GetMapping
    public ResponseEntity<Page<NotaFiscalResponseDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) com.manager.app.shared.ModoBusca modoBusca,
            @RequestParam(required = false) StatusNotaFiscal status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataFim,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, modoBusca, status, dataInicio, dataFim, pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/valor-total")
    public ResponseEntity<java.math.BigDecimal> valorTotal(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) com.manager.app.shared.ModoBusca modoBusca,
            @RequestParam(required = false) StatusNotaFiscal status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataFim){
        return ResponseEntity.ok(service.valorTotalFiltrado(busca, modoBusca, status, dataInicio, dataFim));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<NotaFiscalResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @GetMapping(value = "/cfops-disponiveis")
    public ResponseEntity<List<CfopDisponivelDTO>> cfopsDisponiveis(
            @RequestParam Long empresaId,
            @RequestParam UUID clienteId){
        return ResponseEntity.ok(service.cfopsDisponiveis(empresaId, clienteId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('NOTA_FISCAL_GERENCIAR')")
    public ResponseEntity<NotaFiscalResponseDTO> create(@RequestBody @Valid NotaFiscalRequestDTO notaFiscalRequestDTO){
        NotaFiscal notaFiscal = service.create(notaFiscalRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(notaFiscal.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(notaFiscal));
    }

    @PutMapping(value = "/{id}")
    @PreAuthorize("hasAuthority('NOTA_FISCAL_GERENCIAR')")
    public ResponseEntity<NotaFiscalResponseDTO> update(@PathVariable Long id, @RequestBody @Valid NotaFiscalRequestDTO notaFiscalRequestDTO){
        return ResponseEntity.ok(mapper.toResponseDTO(service.update(id, notaFiscalRequestDTO)));
    }

    @PostMapping(value = "/{id}/emitir")
    @PreAuthorize("hasAuthority('NOTA_FISCAL_EMITIR')")
    public ResponseEntity<NotaFiscalResponseDTO> emitir(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.emitir(id)));
    }

    @PostMapping(value = "/{id}/status")
    public ResponseEntity<NotaFiscalResponseDTO> atualizarStatus(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.atualizarStatus(id)));
    }

    @PostMapping(value = "/{id}/cancelar")
    @PreAuthorize("hasAuthority('NOTA_FISCAL_CANCELAR')")
    public ResponseEntity<NotaFiscalResponseDTO> cancelar(@PathVariable Long id, @RequestBody @Valid NotaFiscalCancelamentoRequestDTO request){
        return ResponseEntity.ok(mapper.toResponseDTO(service.cancelar(id, request.getMotivo())));
    }

    @PostMapping(value = "/{id}/corrigir")
    @PreAuthorize("hasAuthority('NOTA_FISCAL_CORRIGIR')")
    public ResponseEntity<NotaFiscalResponseDTO> corrigir(@PathVariable Long id, @RequestBody @Valid NotaFiscalCorrecaoRequestDTO request){
        return ResponseEntity.ok(mapper.toResponseDTO(service.corrigir(id, request.getTexto())));
    }

    @GetMapping(value = "/{id}/xml")
    public ResponseEntity<byte[]> baixarXml(@PathVariable Long id){
        byte[] xml = service.baixarArtefato(id, "xml_authorized");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml);
    }

    @GetMapping(value = "/{id}/xml-correcao")
    public ResponseEntity<byte[]> baixarXmlCorrecao(@PathVariable Long id){
        byte[] xml = service.baixarArtefato(id, "xml_correction");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml);
    }

    // gera o DANFE a partir do XML já salvo localmente (não bate na Contora) - se o arquivo não
    // existir em disco, propaga o 404 do storage; o frontend usa isso pra oferecer o fluxo de
    // XML avulso em vez de travar numa mensagem genérica de erro
    @GetMapping(value = "/{id}/danfe")
    public ResponseEntity<byte[]> baixarDanfe(@PathVariable Long id){
        byte[] pdf = service.gerarDanfeDoArquivoLocal(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    // gera um DANFE a partir de um XML avulso (não precisa ser de uma nota emitida por este
    // sistema) - operação stateless, sem relação com a tabela de notas fiscais
    @PostMapping(value = "/danfe/gerar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> gerarDanfeDeXml(@RequestParam("xml") MultipartFile xml){
        return gerarPdfDeXml(xml, DanfeGeneratorService.TipoDocumento.NFE, "danfe");
    }

    // PDF do evento de cancelamento a partir do XML do evento (procEventoNFe, tpEvento 110111)
    @PostMapping(value = "/cancelamento/gerar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> gerarPdfCancelamentoDeXml(@RequestParam("xml") MultipartFile xml){
        return gerarPdfDeXml(xml, DanfeGeneratorService.TipoDocumento.CANCELAMENTO, "cancelamento");
    }

    // PDF da carta de correção a partir do XML do evento (procEventoNFe, tpEvento 110110)
    @PostMapping(value = "/correcao/gerar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> gerarPdfCorrecaoDeXml(@RequestParam("xml") MultipartFile xml){
        return gerarPdfDeXml(xml, DanfeGeneratorService.TipoDocumento.CORRECAO, "correcao");
    }

    // mesmo fluxo do DANFE local, usando o XML do cancelamento arquivado (404 se não existir em disco)
    @GetMapping(value = "/{id}/cancelamento/pdf")
    public ResponseEntity<byte[]> baixarPdfCancelamento(@PathVariable Long id){
        byte[] pdf = service.gerarPdfCancelamentoDoArquivoLocal(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    @GetMapping(value = "/{id}/correcao/{sequencia}/pdf")
    public ResponseEntity<byte[]> baixarPdfCorrecao(@PathVariable Long id, @PathVariable Integer sequencia){
        byte[] pdf = service.gerarPdfCorrecaoDoArquivoLocal(id, sequencia);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    private ResponseEntity<byte[]> gerarPdfDeXml(MultipartFile xml, DanfeGeneratorService.TipoDocumento tipo, String prefixoArquivo){
        if (xml == null || xml.isEmpty()) {
            throw new ItemInvalidoException("Arquivo XML é obrigatório");
        }
        String nomeOriginal = xml.getOriginalFilename() != null ? xml.getOriginalFilename().toLowerCase() : "";
        String contentType = xml.getContentType();
        boolean pareceXml = nomeOriginal.endsWith(".xml")
                || MediaType.APPLICATION_XML_VALUE.equals(contentType)
                || MediaType.TEXT_XML_VALUE.equals(contentType)
                || "application/xml".equals(contentType);
        if (!pareceXml) {
            throw new ItemInvalidoException("Arquivo deve ser um XML");
        }

        byte[] xmlBytes;
        try {
            xmlBytes = xml.getBytes();
        } catch (java.io.IOException e) {
            throw new ItemInvalidoException("Não foi possível ler o arquivo enviado");
        }

        byte[] pdf = danfeGeneratorService.gerarPdf(xmlBytes, tipo);
        String chaveAcesso = danfeGeneratorService.extrairChaveAcesso(xmlBytes);
        String nomeArquivo = chaveAcesso != null ? prefixoArquivo + "-" + chaveAcesso + ".pdf" : prefixoArquivo + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=" + nomeArquivo)
                .body(pdf);
    }

    @GetMapping(value = "/xmls")
    public ResponseEntity<Page<XmlArquivoResponseDTO>> listarXmls(
            @PageableDefault(size = 20, sort = "ultimaModificacao") Pageable pageable){
        return ResponseEntity.ok(xmlStorageService.listar(pageable));
    }

    @GetMapping(value = "/xmls/{nomeArquivo}")
    public ResponseEntity<byte[]> baixarXmlPorNome(@PathVariable String nomeArquivo){
        byte[] xml = xmlStorageService.carregar(nomeArquivo);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header("Content-Disposition", "attachment; filename=" + nomeArquivo)
                .body(xml);
    }
}
