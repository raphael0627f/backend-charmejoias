package com.manager.app.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AgendaMarcadorRepository extends JpaRepository<AgendaMarcador, Long> {

    boolean existsByData(LocalDate data);

    boolean existsByDataAndIdNot(LocalDate data, Long id);

    @Query("""
            select m from AgendaMarcador m
            where (m.data between :inicio and :fim)
               or (m.dataPreAcerto between :inicio and :fim)
               or (m.dataAcerto between :inicio and :fim)
            order by m.data asc
            """)
    List<AgendaMarcador> findByPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
