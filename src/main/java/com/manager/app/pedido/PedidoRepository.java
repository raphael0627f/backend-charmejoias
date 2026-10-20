package com.manager.app.pedido;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> findByIdForUpdate(Long id);

    // pedidos do cliente para uma data de entrega, exceto os de um status (usado ao vincular automaticamente à montagem)
    List<Pedido> findByCliente_IdAndDataEntregaAndStatusNot(UUID clienteId, LocalDate dataEntrega, StatusPedido status);
}
