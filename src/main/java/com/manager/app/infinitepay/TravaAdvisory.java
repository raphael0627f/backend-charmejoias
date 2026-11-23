package com.manager.app.infinitepay;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

// trava consultiva do PostgreSQL por transação (pg_advisory_xact_lock): serializa o processamento de uma mesma transação da InfinitePay
// mesmo que dois ciclos (busca automática e botão) cheguem juntos. Liberada sozinha no fim da transação. Deve ser chamada dentro de uma transação.
@Component
public class TravaAdvisory {

    @PersistenceContext
    private EntityManager entityManager;

    public void travar(String chave) {
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(hashtext(:chave)) as text)").setParameter("chave", chave).getSingleResult();
    }
}
