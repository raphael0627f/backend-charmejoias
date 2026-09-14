package com.manager.app.comissao;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ComissaoService {

    private final ComissaoRepository repository;

    public Page<Comissao> findAll(Pageable pageable){
        return repository.findAll(pageable);
    }

    public Comissao findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Comissão não encontrada. id: " + id));
    }

    private void validarRange(Comissao comissao){
        if (comissao.getValorMaximo().compareTo(comissao.getValorMinimo()) <= 0) {
            throw new ItemInvalidoException("Valor máximo do range deve ser maior que o valor mínimo");
        }
    }

    @Transactional
    public Comissao create(Comissao comissao){
        validarRange(comissao);
        return repository.save(comissao);
    }

    @Transactional
    public Comissao update(Comissao comissao){
        validarRange(comissao);
        Comissao comissaoUpdate = this.findById(comissao.getId());
        comissaoUpdate.setPercentual(comissao.getPercentual());
        comissaoUpdate.setValorMinimo(comissao.getValorMinimo());
        comissaoUpdate.setValorMaximo(comissao.getValorMaximo());
        return comissaoUpdate;
    }

    @Transactional
    public void delete(Long id){
        Comissao comissao = this.findById(id);
        repository.delete(comissao);
    }
}
