package com.manager.app.infinitepay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface InfinitePayEventoRepository extends JpaRepository<InfinitePayEvento, Long>, JpaSpecificationExecutor<InfinitePayEvento> {

    Optional<InfinitePayEvento> findByTransactionNsuAndEvento(String transactionNsu, String evento);

    java.util.List<InfinitePayEvento> findByStatusInOrderByIdAsc(java.util.Collection<InfinitePayEvento.Status> status);

    java.util.List<InfinitePayEvento> findByLinkIdInOrderByIdDesc(java.util.Collection<Long> linkIds);

    long countByStatus(InfinitePayEvento.Status status);

    long countByStatusAndTransactionNsuNotLike(InfinitePayEvento.Status status, String padrao);
}
