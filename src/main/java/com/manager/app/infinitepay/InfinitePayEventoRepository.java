package com.manager.app.infinitepay;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InfinitePayEventoRepository extends JpaRepository<InfinitePayEvento, Long> {

    Optional<InfinitePayEvento> findByTransactionNsuAndEvento(String transactionNsu, String evento);

    java.util.List<InfinitePayEvento> findByStatusInOrderByIdAsc(java.util.Collection<InfinitePayEvento.Status> status);

    long countByStatus(InfinitePayEvento.Status status);
}
