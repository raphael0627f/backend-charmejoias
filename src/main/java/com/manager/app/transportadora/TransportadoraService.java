package com.manager.app.transportadora;

import com.manager.app.notafiscal.NotaFiscalRepository;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransportadoraService {

    private final TransportadoraRepository repository;
    private final NotaFiscalRepository notaFiscalRepository;

    public Page<Transportadora> findAll(String busca, Pageable pageable){
        return repository.findAll(comBusca(busca), pageable);
    }

    private Specification<Transportadora> comBusca(String busca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("razaoSocial"), busca);
    }

    public Transportadora findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Transportadora não encontrada. id: " + id));
    }

    @Transactional
    public Transportadora create(Transportadora transportadora){
        return repository.save(transportadora);
    }

    @Transactional
    public Transportadora update(Transportadora transportadora){
        Transportadora transportadoraUpdate = this.findById(transportadora.getId());
        transportadoraUpdate.setRazaoSocial(transportadora.getRazaoSocial());
        transportadoraUpdate.setDocumento(transportadora.getDocumento());
        transportadoraUpdate.setInscricaoEstadual(transportadora.getInscricaoEstadual());
        transportadoraUpdate.setEndereco(transportadora.getEndereco());
        transportadoraUpdate.setMunicipio(transportadora.getMunicipio());
        transportadoraUpdate.setUf(transportadora.getUf());
        transportadoraUpdate.setPlacaVeiculo(transportadora.getPlacaVeiculo());
        transportadoraUpdate.setUfVeiculo(transportadora.getUfVeiculo());
        transportadoraUpdate.setRntc(transportadora.getRntc());
        transportadoraUpdate.setAtivo(transportadora.isAtivo());
        return transportadoraUpdate;
    }

    @Transactional
    public Transportadora activate(Long id){
        Transportadora transportadora = this.findById(id);
        transportadora.setAtivo(true);
        return transportadora;
    }

    @Transactional
    public void deactivate(Long id){
        Transportadora transportadora = this.findById(id);
        if (notaFiscalRepository.existsByTransportadora(transportadora)) {
            throw new ObjetoEmUsoException("Transportadora está em uso e não pode ser desativada");
        }
        transportadora.setAtivo(false);
    }
}
