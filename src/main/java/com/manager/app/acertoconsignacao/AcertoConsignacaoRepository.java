package com.manager.app.acertoconsignacao;

import com.manager.app.usuario.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AcertoConsignacaoRepository extends JpaRepository<AcertoConsignacao, Long>, JpaSpecificationExecutor<AcertoConsignacao> {

    // listagem: traz a consignação e o cliente na mesma consulta
    @Override
    @EntityGraph(attributePaths = {"consignacao", "consignacao.cliente"})
    Page<AcertoConsignacao> findAll(Specification<AcertoConsignacao> spec, Pageable pageable);

    boolean existsByConsignacaoId(Long consignacaoId);

    // usado na listagem de consignações pra marcar "Finalizado" (tem acerto
    // confirmado) vs "Em aberto" — em lote pelos ids da página, não um por um
    @Query("SELECT DISTINCT a.consignacao.id FROM AcertoConsignacao a WHERE a.consignacao.id IN :consignacaoIds AND a.confirmado = true")
    List<Long> findConsignacaoIdsComAcertoConfirmado(@Param("consignacaoIds") List<Long> consignacaoIds);

    // O Hibernate só marca a linha do acerto como "suja" (e dispara @UpdateTimestamp/
    // @LastModifiedBy) quando um campo escalar dele mesmo muda de valor - uma edição que
    // só mexe nos produtos (coleção filha, tabela separada) não conta, então updatedAt/
    // updatedBy nunca mudavam num "devolver produto e salvar". Esse UPDATE em lote força
    // a marcação sempre que o service processa uma edição, independente do que mudou.
    @Modifying
    @Query("UPDATE AcertoConsignacao a SET a.updatedAt = :agora, a.updatedBy = :usuario WHERE a.id = :id")
    void tocarAtualizacao(@Param("id") Long id, @Param("agora") LocalDateTime agora, @Param("usuario") Usuario usuario);
}
