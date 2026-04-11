package com.manager.app.service;

import com.manager.app.dto.acerto.AcertoConsignacaoNovaResquestDTO;
import com.manager.app.dto.acerto.AcertoRequestDTO;
import com.manager.app.entity.*;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.infra.restEnum.TipoConsignacaoAcerto;
import com.manager.app.infra.validation.AcertoValidation;
import com.manager.app.repository.AcertoRepository;
import com.manager.app.repository.ConsignacaoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AcertoService {

    private final AcertoRepository repository;
    private final ConsignacaoRepository consignacaoRepository;
    private final ConsignacaoService consignacaoService;
    private final AcertoValidation validation;

    public List<Acerto> findAll(){
        return repository.findAll();
    }

    public Acerto findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Acerto não encontrada. id: " + id));
    }

    @Transactional
    public Acerto create(AcertoRequestDTO request){
        Acerto acerto = new Acerto();

        acerto.setValorConsignacoes(request.getValorConsignacoes());
        acerto.setValorMaleta(request.getValorMaleta());
        acerto.setValorAcessorios(request.getValorAcessorios());

        List<Consignacao> consignacoes = consignacaoRepository.findAllById(request.getConsignacoesIds());
        List<Consignacao> maletas = consignacaoRepository.findAllById(request.getMaletasIds());
        List<Consignacao> acessorios = consignacaoRepository.findAllById(request.getAcessoriosIds());


        for (Consignacao consignacao : consignacoes){
            AcertoConsignacao consignacaoAcerto = new AcertoConsignacao();

            consignacaoAcerto.setConsignacao(consignacao);
            consignacaoAcerto.setDescricao(consignacao.getDescricao());
            consignacaoAcerto.setQuantidadeProdutos(consignacao.getQuantidadeTotal());
            consignacaoAcerto.setValorTotal(consignacao.getValorTotal());
            consignacaoAcerto.setTipo(TipoConsignacaoAcerto.KIT);
            acerto.adicionarConsignacao(consignacaoAcerto);
        }

        for (Consignacao maleta : maletas){
            AcertoConsignacao consignacaoAcerto = new AcertoConsignacao();

            consignacaoAcerto.setConsignacao(maleta);
            consignacaoAcerto.setDescricao(maleta.getDescricao());
            consignacaoAcerto.setQuantidadeProdutos(maleta.getQuantidadeTotal());
            consignacaoAcerto.setValorTotal(maleta.getValorTotal());
            consignacaoAcerto.setTipo(TipoConsignacaoAcerto.MALETA);
            acerto.adicionarConsignacao(consignacaoAcerto);
        }

        for (Consignacao acessorio : acessorios){
            AcertoConsignacao consignacaoAcerto = new AcertoConsignacao();

            consignacaoAcerto.setConsignacao(acessorio);
            consignacaoAcerto.setDescricao(acessorio.getDescricao());
            consignacaoAcerto.setQuantidadeProdutos(acessorio.getQuantidadeTotal());
            consignacaoAcerto.setValorTotal(acessorio.getValorTotal());
            consignacaoAcerto.setTipo(TipoConsignacaoAcerto.ACESSORIO);
            acerto.adicionarConsignacao(consignacaoAcerto);
        }

        for(AcertoConsignacaoNovaResquestDTO consignacaoNova : request.getConsignacaoesNovas()){
            AcertoConsignacao consignacaoAcerto = new AcertoConsignacao();

            consignacaoAcerto.setConsignacao(null);
            consignacaoAcerto.setDescricao(consignacaoNova.getDescricao());
            consignacaoAcerto.setQuantidadeProdutos(consignacaoNova.getQuantidadeProdutos());
            consignacaoAcerto.setValorTotal(consignacaoNova.getValorTotal());
            consignacaoAcerto.setTipo(TipoConsignacaoAcerto.KIT_NOVO);
            acerto.adicionarConsignacao(consignacaoAcerto);
        }

        return repository.save(acerto);
    }

    @Transactional
    public Acerto update(AcertoRequestDTO request){
        Acerto acertoUpdate = this.findById(request.getId());

        acertoUpdate.setValorConsignacoes(request.getValorConsignacoes());
        acertoUpdate.setValorMaleta(request.getValorMaleta());
        acertoUpdate.setValorAcessorios(request.getValorAcessorios());

        List<AcertoConsignacao> consignacoesAtuais = new ArrayList<>(acertoUpdate.getConsignacoes());
        List<AcertoConsignacao> consignacoesRequest = new ArrayList<>();

        for (Long consignacaoId : request.getConsignacoesIds()){
            Consignacao consignacao = consignacaoService.findById(consignacaoId);
            AcertoConsignacao acertoConsignacao = new AcertoConsignacao();

            acertoConsignacao.setDescricao(consignacao.getDescricao());
            acertoConsignacao.setQuantidadeProdutos(consignacao.getQuantidadeTotal());
            acertoConsignacao.setTipo(TipoConsignacaoAcerto.KIT);
            acertoConsignacao.setValorTotal(consignacao.getValorTotal());
            acertoConsignacao.setConsignacao(consignacao);
            acertoConsignacao.setAcerto(acertoUpdate);

            consignacoesRequest.add(acertoConsignacao);
        }

        for (Long consignacaoId : request.getMaletasIds()){
            Consignacao consignacao = consignacaoService.findById(consignacaoId);
            AcertoConsignacao acertoConsignacao = new AcertoConsignacao();

            acertoConsignacao.setDescricao(consignacao.getDescricao());
            acertoConsignacao.setQuantidadeProdutos(consignacao.getQuantidadeTotal());
            acertoConsignacao.setTipo(TipoConsignacaoAcerto.MALETA);
            acertoConsignacao.setValorTotal(consignacao.getValorTotal());
            acertoConsignacao.setConsignacao(consignacao);
            acertoConsignacao.setAcerto(acertoUpdate);

            consignacoesRequest.add(acertoConsignacao);
        }

        for (Long consignacaoId : request.getAcessoriosIds()){
            Consignacao consignacao = consignacaoService.findById(consignacaoId);
            AcertoConsignacao acertoConsignacao = new AcertoConsignacao();

            acertoConsignacao.setDescricao(consignacao.getDescricao());
            acertoConsignacao.setQuantidadeProdutos(consignacao.getQuantidadeTotal());
            acertoConsignacao.setTipo(TipoConsignacaoAcerto.ACESSORIO);
            acertoConsignacao.setValorTotal(consignacao.getValorTotal());
            acertoConsignacao.setConsignacao(consignacao);
            acertoConsignacao.setAcerto(acertoUpdate);

            consignacoesRequest.add(acertoConsignacao);
        }

        for (AcertoConsignacao consignacaoRequest : consignacoesRequest){
            AcertoConsignacao consignacaoExistente = null;

            for (AcertoConsignacao consignacao : acertoUpdate.getConsignacoes()){
                if (consignacao.getConsignacao() != null && consignacao.getConsignacao().getId().equals(consignacaoRequest.getConsignacao().getId())) {
                    consignacaoExistente = consignacao;
                    break;
                }
            }

            if(consignacaoExistente != null){

                if (!consignacaoExistente.getDescricao().equals(consignacaoRequest.getDescricao())) {
                    consignacaoExistente.setDescricao(consignacaoRequest.getDescricao());
                }

                if (!consignacaoExistente.getQuantidadeProdutos().equals(consignacaoRequest.getQuantidadeProdutos())) {
                    consignacaoExistente.setQuantidadeProdutos(consignacaoRequest.getQuantidadeProdutos());
                }

                if (!consignacaoExistente.getTipo().equals(consignacaoRequest.getTipo())) {
                    consignacaoExistente.setTipo(consignacaoRequest.getTipo());
                }

                if (!consignacaoExistente.getValorTotal().equals(consignacaoRequest.getValorTotal())) {
                    consignacaoExistente.setValorTotal(consignacaoRequest.getValorTotal());
                }

                consignacoesAtuais.remove(consignacaoExistente);
            } else {
                acertoUpdate.adicionarConsignacao(consignacaoRequest);
            }
        }

        for (AcertoConsignacaoNovaResquestDTO consignacaoNovaDTO : request.getConsignacaoesNovas()){
            AcertoConsignacao consignacaoNova = new AcertoConsignacao();

            consignacaoNova.setDescricao(consignacaoNovaDTO.getDescricao());
            consignacaoNova.setQuantidadeProdutos(consignacaoNovaDTO.getQuantidadeProdutos());
            consignacaoNova.setTipo(TipoConsignacaoAcerto.KIT_NOVO);
            consignacaoNova.setValorTotal(consignacaoNovaDTO.getValorTotal());
            consignacaoNova.setConsignacao(null);

            acertoUpdate.adicionarConsignacao(consignacaoNova);
        }

        for (AcertoConsignacao removerConsignacao : consignacoesAtuais) {
            acertoUpdate.removerConsignacao(removerConsignacao);
        }

        return acertoUpdate;
    }
}


