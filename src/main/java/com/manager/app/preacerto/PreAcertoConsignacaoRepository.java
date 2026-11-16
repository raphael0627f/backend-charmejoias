package com.manager.app.preacerto;

import com.manager.app.usuario.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface PreAcertoConsignacaoRepository extends JpaRepository<PreAcertoConsignacao, Long>, JpaSpecificationExecutor<PreAcertoConsignacao> {

    boolean existsByConsignacaoId(Long consignacaoId);

    // id da consignação do pré-acerto sem carregar a entidade (lista vazia = pré-acerto inexistente; elemento nulo = sem consignação)
    @Query("select c.id from PreAcertoConsignacao p left join p.consignacao c where p.id = :id")
    java.util.List<Long> consignacaoIdDe(@Param("id") Long id);

    // bloqueio pessimista da linha (SELECT ... FOR UPDATE): serializa a geração de títulos e a edição do documento
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PreAcertoConsignacao p where p.id = :id")
    java.util.Optional<PreAcertoConsignacao> findByIdForUpdate(@Param("id") Long id);

    boolean existsByConsignacaoIdAndIdNot(Long consignacaoId, Long idAtual);

    // O Hibernate só marca a linha do pré-acerto como "suja" (e dispara
    // @UpdateTimestamp/@LastModifiedBy) quando um campo escalar dele mesmo muda de
    // valor - uma edição que só mexe nos produtos (coleção filha, tabela separada)
    // não conta, então updatedAt/updatedBy nunca mudavam nesse caso. Esse UPDATE em
    // lote força a marcação sempre que o service processa uma edição, independente
    // do que mudou.
    @Modifying
    @Query("UPDATE PreAcertoConsignacao p SET p.updatedAt = :agora, p.updatedBy = :usuario WHERE p.id = :id")
    void tocarAtualizacao(@Param("id") Long id, @Param("agora") LocalDateTime agora, @Param("usuario") Usuario usuario);
}
