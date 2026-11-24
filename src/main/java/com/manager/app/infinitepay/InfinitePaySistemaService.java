package com.manager.app.infinitepay;

import com.manager.app.infinitepay.dto.InfinitePayDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// "um único Salvar" de Sistema > InfinitePay: integração (InfiniteTag, chave, padrões do link) e baixa automática (receptor, banco/formas, interruptor)
// numa só transação. Valida a configuração primeiro e depois a baixa automática; qualquer erro desfaz tudo (nada é gravado pela metade).
@Service
@RequiredArgsConstructor
public class InfinitePaySistemaService {

    private final InfinitePayConfiguracaoService configuracaoService;
    private final InfinitePayAutomaticoService automaticoService;

    @Transactional
    public InfinitePayDTOs.SistemaResponse salvar(InfinitePayDTOs.SistemaRequest request) {
        if (request == null || (request.configuracao() == null && request.automatico() == null)) {
            throw new ItemInvalidoException("Informe os dados da configuração.");
        }
        // parte ausente: mantém o que está salvo (um request vazio é um "não alterar")
        InfinitePayDTOs.ConfiguracaoResponse configuracao = request.configuracao() != null
                ? configuracaoService.salvar(request.configuracao()) : configuracaoService.obter();
        InfinitePayDTOs.AutomaticoResponse automatico = request.automatico() != null
                ? automaticoService.salvar(request.automatico()) : automaticoService.obter();
        return new InfinitePayDTOs.SistemaResponse(configuracao, automatico);
    }
}
