package com.manager.app.transportadora;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransportadoraRepository extends JpaRepository<Transportadora, Long>, JpaSpecificationExecutor<Transportadora> {
}
