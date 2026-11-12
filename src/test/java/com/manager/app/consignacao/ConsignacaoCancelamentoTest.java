package com.manager.app.consignacao;

import com.manager.app.acertoconsignacao.AcertoConsignacaoRepository;
import com.manager.app.consignacao.dto.ConsignacaoRequestDTO;
import com.manager.app.montagem.MontagemMostruario;
import com.manager.app.montagem.MontagemMostruarioRepository;
import com.manager.app.montagem.StatusMontagem;
import com.manager.app.preacerto.PreAcertoConsignacaoRepository;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsignacaoCancelamentoTest {

    @Mock ConsignacaoRepository repository;
    @Mock AcertoConsignacaoRepository acertoConsignacaoRepository;
    @Mock PreAcertoConsignacaoRepository preAcertoConsignacaoRepository;
    @Mock MontagemMostruarioRepository montagemMostruarioRepository;
    @Mock UsuarioAuditorAware usuarioAuditorAware;
    @InjectMocks ConsignacaoService service;

    private Consignacao consignacao;

    @BeforeEach
    void setUp() {
        consignacao = new Consignacao();
        consignacao.setId(7L);
        consignacao.setConfirmada(false);
        consignacao.setCancelada(false);
        when(repository.findById(7L)).thenReturn(Optional.of(consignacao));
        when(montagemMostruarioRepository.findByConsignacoes_Id(anyLong())).thenReturn(Optional.empty());
        when(usuarioAuditorAware.getCurrentAuditor()).thenReturn(Optional.of(new Usuario()));
    }

    @Test
    void cancelaNaoConfirmadaGravandoMotivoQuandoEQuem() {
        Consignacao c = service.cancelar(7L, "  Cliente desistiu  ");
        assertThat(c.getCancelada()).isTrue();
        assertThat(c.getMotivoCancelamento()).isEqualTo("Cliente desistiu");
        assertThat(c.getCanceladaEm()).isNotNull();
        assertThat(c.getCanceladaPor()).isNotNull();
    }

    @Test
    void motivoObrigatorioEComMinimoDeCincoCaracteres() {
        assertThatThrownBy(() -> service.cancelar(7L, "  ")).isInstanceOf(ItemInvalidoException.class)
                .hasMessage("O campo Motivo é obrigatório.");
        assertThatThrownBy(() -> service.cancelar(7L, " abc ")).isInstanceOf(ItemInvalidoException.class)
                .hasMessage("O motivo do cancelamento deve ter ao menos 5 caracteres.");
        assertThat(consignacao.getCancelada()).isFalse();
    }

    @Test
    void confirmadaNaoPodeSerCancelada() {
        consignacao.setConfirmada(true);
        assertThatThrownBy(() -> service.cancelar(7L, "motivo valido")).isInstanceOf(ObjetoEmUsoException.class)
                .hasMessage("Não é possível cancelar uma consignação confirmada.");
    }

    @Test
    void cancelarDuasVezesRecusa() {
        service.cancelar(7L, "motivo valido");
        assertThatThrownBy(() -> service.cancelar(7L, "motivo valido")).isInstanceOf(ItemInvalidoException.class)
                .hasMessage("A consignação já está cancelada.");
    }

    @Test
    void comPreAcertoOuAcertoRecusa409() {
        when(preAcertoConsignacaoRepository.existsByConsignacaoId(7L)).thenReturn(true);
        assertThatThrownBy(() -> service.cancelar(7L, "motivo valido")).isInstanceOf(ObjetoEmUsoException.class)
                .hasMessageContaining("pré-acerto ou acerto");
        when(preAcertoConsignacaoRepository.existsByConsignacaoId(7L)).thenReturn(false);
        when(acertoConsignacaoRepository.existsByConsignacaoId(7L)).thenReturn(true);
        assertThatThrownBy(() -> service.cancelar(7L, "motivo valido")).isInstanceOf(ObjetoEmUsoException.class);
    }

    @Test
    void montagemEmAndamentoRecusaMasConcluidaOuCanceladaNao() {
        MontagemMostruario m = new MontagemMostruario();
        m.setStatus(StatusMontagem.EM_MONTAGEM);
        when(montagemMostruarioRepository.findByConsignacoes_Id(7L)).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.cancelar(7L, "motivo valido")).isInstanceOf(ObjetoEmUsoException.class)
                .hasMessageContaining("montagem em andamento");
        m.setStatus(StatusMontagem.ABERTA);
        assertThatThrownBy(() -> service.cancelar(7L, "motivo valido")).isInstanceOf(ObjetoEmUsoException.class);
        m.setStatus(StatusMontagem.CANCELADA);
        assertThat(service.cancelar(7L, "motivo valido").getCancelada()).isTrue();
    }

    @Test
    void canceladaNaoPodeSerConfirmadaNemEditadaNemReceberProdutos() {
        service.cancelar(7L, "motivo valido");
        assertThatThrownBy(() -> service.confirmar(7L)).isInstanceOf(ItemInvalidoException.class)
                .hasMessage("Não é possível confirmar uma consignação cancelada.");
        ConsignacaoRequestDTO req = new ConsignacaoRequestDTO();
        req.setId(7L);
        assertThatThrownBy(() -> service.update(req)).isInstanceOf(ObjetoEmUsoException.class)
                .hasMessage("Não é possível editar uma consignação cancelada.");
        assertThatThrownBy(() -> service.adicionarProdutos(7L, java.util.List.of())).isInstanceOf(ObjetoEmUsoException.class)
                .hasMessage("Não é possível editar uma consignação cancelada.");
    }

    @Test
    void outrosModulosRecusamConsignacaoCancelada() {
        consignacao.setCancelada(true);
        assertThatThrownBy(() -> ConsignacaoValidation.exigirNaoCancelada(consignacao)).isInstanceOf(ItemInvalidoException.class)
                .hasMessage("Não é possível utilizar uma consignação cancelada.");
        ConsignacaoValidation.exigirNaoCancelada(new Consignacao());
        ConsignacaoValidation.exigirNaoCancelada(null);
    }
}
