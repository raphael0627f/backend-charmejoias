package com.manager.app.montagem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MontagemItemIncluidoRepository extends JpaRepository<MontagemItemIncluido, Long> {

    List<MontagemItemIncluido> findByMontagemId(Long montagemId);

    // unidades dos pedidos já incluídas nas consignações, por montagem (andamento da listagem)
    @Query("select i.montagemId as montagemId, coalesce(sum(i.quantidade), 0) as total "
            + "from MontagemItemIncluido i where i.montagemId in :ids group by i.montagemId")
    List<MontagemContagemProjecao> somarIncluidos(@Param("ids") List<Long> ids);
}
