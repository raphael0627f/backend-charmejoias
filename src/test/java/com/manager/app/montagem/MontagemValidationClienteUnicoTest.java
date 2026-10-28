package com.manager.app.montagem;

import com.manager.app.cliente.Cliente;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// regra: um cliente só pode ter UMA montagem em andamento (ABERTA ou EM_MONTAGEM) - validação pura, sem Spring e sem banco
class MontagemValidationClienteUnicoTest {

    private final MontagemValidation validation = new MontagemValidation();

    private static MontagemMostruario montagem(Long id, UUID clienteId, StatusMontagem status) {
        Cliente cliente = new Cliente();
        cliente.setId(clienteId);
        MontagemMostruario m = new MontagemMostruario();
        m.setId(id);
        m.setCliente(cliente);
        m.setStatus(status);
        return m;
    }

    @Test
    void statusEmAndamentoSaoSoAbertaEEmMontagem() {
        assertThat(MontagemValidation.STATUS_EM_ANDAMENTO)
                .containsExactlyInAnyOrder(StatusMontagem.ABERTA, StatusMontagem.EM_MONTAGEM);
    }

    @Test
    void criacaoSemMontagemEmAndamentoEPermitida() {
        assertThatCode(() -> validation.validarSemMontagemEmAndamento(Optional.empty())).doesNotThrowAnyException();
    }

    @Test
    void criacaoComMontagemAbertaEBarradaIndicandoOId() {
        Optional<MontagemMostruario> existente = Optional.of(montagem(42L, UUID.randomUUID(), StatusMontagem.ABERTA));
        assertThatThrownBy(() -> validation.validarSemMontagemEmAndamento(existente))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessage("O cliente já possui a montagem 42 com status ABERTA. Conclua ou cancele antes de criar outra");
    }

    @Test
    void criacaoComMontagemEmMontagemEBarrada() {
        Optional<MontagemMostruario> existente = Optional.of(montagem(7L, UUID.randomUUID(), StatusMontagem.EM_MONTAGEM));
        assertThatThrownBy(() -> validation.validarSemMontagemEmAndamento(existente))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("montagem 7 com status EM_MONTAGEM");
    }

    @Test
    void trocaParaClienteSemOutraMontagemEmAndamentoEPermitida() {
        MontagemMostruario atual = montagem(1L, UUID.randomUUID(), StatusMontagem.ABERTA);
        assertThatCode(() -> validation.validarTrocaCliente(atual, UUID.randomUUID(), Optional.empty())).doesNotThrowAnyException();
    }

    @Test
    void trocaParaClienteComOutraMontagemEmAndamentoEBarrada() {
        MontagemMostruario atual = montagem(1L, UUID.randomUUID(), StatusMontagem.ABERTA);
        UUID novoCliente = UUID.randomUUID();
        Optional<MontagemMostruario> outra = Optional.of(montagem(9L, novoCliente, StatusMontagem.ABERTA));
        assertThatThrownBy(() -> validation.validarTrocaCliente(atual, novoCliente, outra))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("montagem 9 com status ABERTA");
    }

    @Test
    void semTrocaDeClienteNaoConsultaARegra() {
        UUID cliente = UUID.randomUUID();
        MontagemMostruario atual = montagem(1L, cliente, StatusMontagem.EM_MONTAGEM);
        // mesmo cliente: retorna antes de qualquer validação, mesmo que "outra" venha preenchida
        Optional<MontagemMostruario> outra = Optional.of(montagem(2L, cliente, StatusMontagem.ABERTA));
        assertThatCode(() -> validation.validarTrocaCliente(atual, cliente, outra)).doesNotThrowAnyException();
    }

    @Test
    void trocaDeClienteContinuaExigindoStatusAberta() {
        MontagemMostruario atual = montagem(1L, UUID.randomUUID(), StatusMontagem.EM_MONTAGEM);
        assertThatThrownBy(() -> validation.validarTrocaCliente(atual, UUID.randomUUID(), Optional.empty()))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("ABERTA");
    }
}
