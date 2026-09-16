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
