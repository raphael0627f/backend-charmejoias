package com.manager.app.cidade;

import com.manager.app.shared.TextoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.specification.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CidadeService {

    private final CidadeRepository repository;

    public Page<Cidade> findAll(String busca, String uf, CidadeTipo tipo, Pageable pageable){
        Specification<Cidade> spec = null;
        String normalizada = TextoUtils.normalizarBusca(busca);
        if (!normalizada.isEmpty()) {
            // cada palavra precisa aparecer no nome (e no município, no caso do distrito), em qualquer ordem
            for (String palavra : normalizada.split(" ")) {
                String padrao = "%" + escaparLike(palavra) + "%";
                spec = SpecificationUtils.and(spec, (root, query, cb) -> cb.like(root.get("nomeBusca"), padrao, '\\'));
            }
        }
        if (uf != null && !uf.isBlank()) {
            String ufUpper = uf.trim().toUpperCase();
            spec = SpecificationUtils.and(spec, (root, query, cb) -> cb.equal(root.get("uf"), ufUpper));
        }
        if (tipo != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> cb.equal(root.get("tipo"), tipo));
        }
        return repository.findAll(spec, pageable);
    }

    public Cidade findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cidade não encontrada. id: " + id));
    }

    // carrega várias cidades de uma vez (uma consulta), preservando a ordem recebida; erro se repetir id ou se algum não existir
    public List<Cidade> findAllOrdenado(List<Long> ids){
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        Set<Long> vistos = new HashSet<>();
        for (Long id : ids) {
            if (id == null) throw new ItemInvalidoException("Cidade inválida: id nulo");
            if (!vistos.add(id)) throw new ItemInvalidoException("Cidade repetida na lista. id: " + id);
        }
        Map<Long, Cidade> porId = repository.findAllById(ids).stream().collect(Collectors.toMap(Cidade::getId, Function.identity()));
        List<Cidade> resultado = new ArrayList<>();
        for (Long id : ids) {
            Cidade cidade = porId.get(id);
            if (cidade == null) throw new ObjetoNaoEncontradoException("Cidade não encontrada. id: " + id);
            resultado.add(cidade);
        }
        return resultado;
    }

    private static String escaparLike(String texto){
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
