package com.manager.app.montagem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MontagemItemIncluidoRepository extends JpaRepository<MontagemItemIncluido, Long> {

    List<MontagemItemIncluido> findByMontagemId(Long montagemId);
}
