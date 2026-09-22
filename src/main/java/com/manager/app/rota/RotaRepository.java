package com.manager.app.rota;

import com.manager.app.usuario.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface RotaRepository extends JpaRepository<Rota, Long>, JpaSpecificationExecutor<Rota> {

    // mesma necessidade de AcertoConsignacaoRepository.tocarAtualizacao: uma edição que só mexe
    // nos itens (RotaCliente, tabela separada) não deixa a Rota "suja" pro Hibernate, e
    // updatedAt/updatedBy nunca mudam nesse caso
    @Modifying
    @Query("UPDATE Rota r SET r.updatedAt = :agora, r.updatedBy = :usuario WHERE r.id = :id")
    void tocarAtualizacao(@Param("id") Long id, @Param("agora") LocalDateTime agora, @Param("usuario") Usuario usuario);
}
