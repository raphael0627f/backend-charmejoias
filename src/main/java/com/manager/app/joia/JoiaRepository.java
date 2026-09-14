package com.manager.app.joia;

import com.manager.app.categoria.Categoria;
import com.manager.app.grupo.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JoiaRepository extends JpaRepository<Joia, UUID>, JpaSpecificationExecutor<Joia> {
    Optional<Joia> findByIdAndAtivoTrue(UUID id);
    Optional<Joia> findByCodigoAndAtivoTrue(String codigo);
    List<Joia> findAllByAtivoTrue();
    boolean existsByCategoria(Categoria categoria);
    boolean existsByCodigoAndIdNot(String codigo, UUID id);
    boolean existsByCodigo(String codigo);
    boolean existsByGrupos(Grupo grupo);

    // busca exata pra bipagem de código de barras: joia não tem variação nem código de barras,
    // só codigo e referencia
    @Query("select j from Joia j where j.ativo = true and upper(j.codigo) = upper(:termo)")
    List<Joia> buscarExataPorCodigo(@Param("termo") String termo);

    @Query("select j from Joia j where j.ativo = true and upper(j.referencia) = upper(:termo)")
    List<Joia> buscarExataPorReferencia(@Param("termo") String termo);
}
