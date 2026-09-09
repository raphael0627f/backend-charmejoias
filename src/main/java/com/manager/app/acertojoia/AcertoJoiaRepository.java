package com.manager.app.acertojoia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcertoJoiaRepository extends JpaRepository<AcertoJoia, Long>, JpaSpecificationExecutor<AcertoJoia> {
}
