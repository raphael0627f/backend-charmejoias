package com.manager.app.consignacao;

import com.manager.app.mostruario.Mostruario;
import com.manager.app.usuario.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ConsignacaoRepository  extends JpaRepository<Consignacao, Long>, JpaSpecificationExecutor<Consignacao> {
    boolean existsByMostruario(Mostruario mostruario);

    // O Hibernate só marca a linha da consignação como "suja" (e dispara
    // @UpdateTimestamp/@LastModifiedBy) quando um campo escalar dela mesma muda de
    // valor - uma edição que só mexe nos produtos (coleção filha, tabela separada)
    // não conta, então updatedAt/updatedBy nunca mudavam nesse caso. Esse UPDATE em
    // lote força a marcação sempre que o service processa uma edição, independente
    // do que mudou.
    @Modifying
    @Query("UPDATE Consignacao c SET c.updatedAt = :agora, c.updatedBy = :usuario WHERE c.id = :id")
    void tocarAtualizacao(@Param("id") Long id, @Param("agora") LocalDateTime agora, @Param("usuario") Usuario usuario);
}
