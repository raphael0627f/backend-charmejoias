package com.manager.app.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExcedenteAcertoEventoRepository extends JpaRepository<ExcedenteAcertoEvento, Long> {
    List<ExcedenteAcertoEvento> findByExcedenteIdOrderByIdAsc(Long excedenteId);
}
