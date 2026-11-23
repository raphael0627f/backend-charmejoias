package com.manager.app.infinitepay;

import com.manager.app.infinitepay.dto.InfinitePayDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class InfinitePaySistemaServiceTest {

    private final InfinitePayConfiguracaoService configuracao = mock(InfinitePayConfiguracaoService.class);
    private final InfinitePayAutomaticoService automatico = mock(InfinitePayAutomaticoService.class);
    private final InfinitePaySistemaService service = new InfinitePaySistemaService(configuracao, automatico);

    private static final InfinitePayDTOs.ConfiguracaoRequest CONF = new InfinitePayDTOs.ConfiguracaoRequest(null, null, null, null, null, null, null, null, null, null, null, null);
    private static final InfinitePayDTOs.AutomaticoRequest AUTO = new InfinitePayDTOs.AutomaticoRequest(true, null, null, null, null, null, null, null, null);

    @Test
    void salvaPrimeiroAConfiguracaoEDepoisABaixaAutomatica() {
        var respConf = mock(InfinitePayDTOs.ConfiguracaoResponse.class);
        var respAuto = mock(InfinitePayDTOs.AutomaticoResponse.class);
        when(configuracao.salvar(CONF)).thenReturn(respConf);
        when(automatico.salvar(AUTO)).thenReturn(respAuto);
        var r = service.salvar(new InfinitePayDTOs.SistemaRequest(CONF, AUTO));
        assertSame(respConf, r.configuracao());
        assertSame(respAuto, r.automatico());
        InOrder ordem = inOrder(configuracao, automatico);
        ordem.verify(configuracao).salvar(CONF);
        ordem.verify(automatico).salvar(AUTO);
    }

    @Test
    void parteAusenteMantemOQueEstaSalvo() {
        var respConf = mock(InfinitePayDTOs.ConfiguracaoResponse.class);
        when(configuracao.obter()).thenReturn(respConf);
        when(automatico.salvar(AUTO)).thenReturn(mock(InfinitePayDTOs.AutomaticoResponse.class));
        var r = service.salvar(new InfinitePayDTOs.SistemaRequest(null, AUTO));
        assertSame(respConf, r.configuracao());
        verify(configuracao, never()).salvar(any());
    }

    @Test
    void erroNaBaixaAutomaticaPropagaParaDesfazerTudo() {
        when(configuracao.salvar(CONF)).thenReturn(mock(InfinitePayDTOs.ConfiguracaoResponse.class));
        when(automatico.salvar(AUTO)).thenThrow(new ObjetoEmUsoException("Não é possível ligar a baixa automática: pendência."));
        assertThrows(ObjetoEmUsoException.class, () -> service.salvar(new InfinitePayDTOs.SistemaRequest(CONF, AUTO)));
    }

    @Test
    void corpoVazioEhInvalido() {
        assertThrows(ItemInvalidoException.class, () -> service.salvar(null));
        assertThrows(ItemInvalidoException.class, () -> service.salvar(new InfinitePayDTOs.SistemaRequest(null, null)));
    }
}
