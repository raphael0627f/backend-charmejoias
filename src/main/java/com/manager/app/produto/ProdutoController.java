package com.manager.app.produto;

import com.manager.app.produto.dto.PreviewVariacoesRequestDTO;
import com.manager.app.produto.dto.PreviewVariacoesResponseDTO;
import com.manager.app.produto.dto.ProdutoRequestDTO;
import com.manager.app.produto.dto.ProdutoRelacoesDTO;
import com.manager.app.produto.dto.ProdutoResponseDTO;
import com.manager.app.produto.dto.ProximoSkuProdutoResponseDTO;
import com.manager.app.produto.dto.SkuDisponivelResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value="/produtos")
public class ProdutoController {

    private final ProdutoService service;
    private final ProdutoMapper mapper;

    @GetMapping
    public ResponseEntity<Page<ProdutoResponseDTO>> findAll(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long fornecedorId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable){
        return ResponseEntity.ok(service.findAll(busca, ativo, categoriaId, fornecedorId, pageable).map(mapper::toResponseDTO));
    }

    @GetMapping(value = "/proximo-sku")
    public ResponseEntity<ProximoSkuProdutoResponseDTO> proximoSku(){
        return ResponseEntity.ok(new ProximoSkuProdutoResponseDTO(service.proximoSku()));
    }

    @GetMapping(value = "/sku-disponivel")
    public ResponseEntity<SkuDisponivelResponseDTO> skuDisponivel(
            @RequestParam String sku,
            @RequestParam(required = false) UUID ignorarProdutoId,
            @RequestParam(required = false) Long ignorarProdutoVariacaoId){
        boolean disponivel = service.skuDisponivel(sku, ignorarProdutoId, ignorarProdutoVariacaoId);
        return ResponseEntity.ok(new SkuDisponivelResponseDTO(sku, disponivel));
    }

    @PostMapping(value = "/variacoes/preview")
    public ResponseEntity<PreviewVariacoesResponseDTO> previewVariacoes(@RequestBody @Valid PreviewVariacoesRequestDTO previewRequestDTO){
        return ResponseEntity.ok(service.previewCombinacoes(previewRequestDTO));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<ProdutoResponseDTO> findById(@PathVariable UUID id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> create(@RequestBody @Valid ProdutoRequestDTO produtoRequestDTO){
        ProdutoRelacoesDTO relacoes = new ProdutoRelacoesDTO(produtoRequestDTO.getCategoriaId(), produtoRequestDTO.getFornecedorId(), produtoRequestDTO.getGruposIds(), produtoRequestDTO.getNcmCodigo());
        Produto produto = service.create(mapper.toEntity(produtoRequestDTO), relacoes, produtoRequestDTO.getVariacoes());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(produto.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(produto));
    }

    @PutMapping
    public ResponseEntity<ProdutoResponseDTO> update(@RequestBody @Valid ProdutoRequestDTO produtoRequestDTO){
        ProdutoRelacoesDTO relacoes = new ProdutoRelacoesDTO(produtoRequestDTO.getCategoriaId(), produtoRequestDTO.getFornecedorId(), produtoRequestDTO.getGruposIds(), produtoRequestDTO.getNcmCodigo());
        Produto produto = service.update(mapper.toEntity(produtoRequestDTO), relacoes, produtoRequestDTO.getVariacoes());
        return ResponseEntity.ok(mapper.toResponseDTO(produto));
    }

    @PutMapping(value = "activate/{id}")
    public ResponseEntity<ProdutoResponseDTO> activate(@PathVariable UUID id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.activate(id)));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProdutoResponseDTO> uploadImagem(@PathVariable UUID id, @RequestParam("imagem") MultipartFile imagem){
        return ResponseEntity.ok(mapper.toResponseDTO(service.atualizarImagem(id, imagem)));
    }

    @GetMapping(value = "/{id}/imagem/{arquivo}")
    public ResponseEntity<byte[]> imagem(@PathVariable UUID id, @PathVariable String arquivo){
        byte[] bytes = service.carregarImagem(id);
        MediaType mediaType = switch (arquivo.substring(arquivo.lastIndexOf('.') + 1).toLowerCase()) {
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };
        return ResponseEntity.ok().contentType(mediaType).body(bytes);
    }

    @DeleteMapping(value = "/{id}/imagem")
    public ResponseEntity<Void> deleteImagem(@PathVariable UUID id){
        service.removerImagem(id);
        return ResponseEntity.noContent().build();
    }
}
