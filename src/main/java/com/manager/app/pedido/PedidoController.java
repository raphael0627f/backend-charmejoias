package com.manager.app.pedido;

import com.manager.app.consignacao.dto.ConsignacaoRequestDTO;
import com.manager.app.consignacao.dto.ConsignacaoResponseDTO;
import com.manager.app.pedido.dto.PedidoRequestDTO;
import com.manager.app.pedido.dto.PedidoResponseDTO;
import com.manager.app.pedido.dto.PedidoStatusRequestDTO;
import com.manager.app.consignacao.Consignacao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping(value="/pedidos")
public class PedidoController {

    private final PedidoService service;
    private final PedidoMapper mapper;

    @GetMapping
    public ResponseEntity<List<PedidoResponseDTO>> findAll(){
        return ResponseEntity.ok(service.findAll().stream().map(mapper::toResponseDTO).collect(Collectors.toList()));
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<PedidoResponseDTO> findById(@PathVariable Long id){
        return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<PedidoResponseDTO> create(@RequestBody @Valid PedidoRequestDTO request){
        Pedido pedido = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(pedido.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponseDTO(pedido));
    }

    @PutMapping
    public ResponseEntity<PedidoResponseDTO> update(@RequestBody @Valid PedidoRequestDTO request){
        Pedido pedido = service.update(request);
        return ResponseEntity.ok(mapper.toResponseDTO(pedido));
    }

    @PutMapping(value = "status")
    public ResponseEntity<PedidoResponseDTO> updateStatus(@RequestBody @Valid PedidoStatusRequestDTO request){
        Pedido pedido = service.updateStatus(request);
        return ResponseEntity.ok(mapper.toResponseDTO(pedido));
    }
}
