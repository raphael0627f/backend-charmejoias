package com.manager.app.ncm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NcmRepository extends JpaRepository<Ncm, String>, JpaSpecificationExecutor<Ncm> {
}
