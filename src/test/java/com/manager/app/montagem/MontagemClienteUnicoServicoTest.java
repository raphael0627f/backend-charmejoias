package com.manager.app.montagem;

import com.manager.app.cliente.Cliente;
import com.manager.app.cliente.ClienteService;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoRepository;
import com.manager.app.consignacao.ConsignacaoService;
import com.manager.app.montagem.dto.MontagemRequestDTO;
import com.manager.app.mostruario.MostruarioService;
import com.manager.app.pedido.PedidoRepository;
import com.manager.app.pedido.PedidoService;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// complementa MontagemValidationClienteUnicoTest: casos de borda da regra "uma montagem em andamento por cliente"
// na validação (status/vínculos/ordem das regras) e na ligação da regra com o serviço (consulta feita, nada gravado ao barrar)
class MontagemClienteUnicoServicoTest {

    private final MontagemValidation validation = new MontagemValidation();

    private MontagemMostruarioRepository repository;
    private ClienteService clienteService;
    private MontagemMostruarioService service;

    @BeforeEach
    void preparar() {
        repository = mock(MontagemMostruarioRepository.class);
        clienteService = mock(ClienteService.class);
        service = new MontagemMostruarioService(repository, validation, clienteService, mock(MostruarioService.class),
                mock(UsuarioAuditorAware.class), mock(ConsignacaoService.class), mock(PedidoService.class), mock(Validator.class),
                mock(MontagemConferencia.class), mock(ConsignacaoRepository.class), mock(PedidoRepository.class),
                mock(MontagemItemIncluidoRepository.class));
    }

    private static Cliente cliente(UUID id) {
        Cliente c = new Cliente();
        c.setId(id);
        return c;
    }

    private static MontagemMostruario montagem(Long id, UUID clienteId, StatusMontagem status) {
        MontagemMostruario m = new MontagemMostruario();
        m.setId(id);
        m.setCliente(cliente(clienteId));
        m.setStatus(status);
        return m;
    }

    private static MontagemRequestDTO request(Long id, UUID clienteId) {
        MontagemRequestDTO r = new MontagemRequestDTO();
        r.setId(id);
        r.setClienteId(clienteId);
        r.setDataEntrega(LocalDate.of(2031, 5, 10));
        return r;
    }

    // ---------- validação: bordas ----------

    @Test
    void montagemSemStatusNaoPodeTrocarDeCliente() {
        MontagemMostruario atual = montagem(1L, UUID.randomUUID(), null);
        assertThatThrownBy(() -> validation.validarTrocaCliente(atual, UUID.randomUUID(), Optional.empty()))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("ABERTA");
    }

    @Test
    void mensagemDaRegraAntigaTemPrioridadeSobreClienteOcupadoQuandoNaoEstaAberta() {
        // montagem EM_MONTAGEM tentando ir para cliente ocupado: o motivo informado é o status, não o cliente ocupado
        MontagemMostruario atual = montagem(1L, UUID.randomUUID(), StatusMontagem.EM_MONTAGEM);
        Optional<MontagemMostruario> ocupado = Optional.of(montagem(9L, UUID.randomUUID(), StatusMontagem.ABERTA));
        assertThatThrownBy(() -> validation.validarTrocaCliente(atual, UUID.randomUUID(), ocupado))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("só pode ser alterado enquanto a montagem está ABERTA");
    }

    @Test
    void vinculosTemPrioridadeSobreClienteOcupado() {
        MontagemMostruario atual = montagem(1L, UUID.randomUUID(), StatusMontagem.ABERTA);
        atual.getConsignacoes().add(new Consignacao());
        Optional<MontagemMostruario> ocupado = Optional.of(montagem(9L, UUID.randomUUID(), StatusMontagem.ABERTA));
        assertThatThrownBy(() -> validation.validarTrocaCliente(atual, UUID.randomUUID(), ocupado))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("Remova os pedidos e consignações vinculados");
    }

    @Test
    void trocaParaOMesmoClienteNaoExigeStatusNemConsultaMesmoSendoConcluida() {
        // retorna antes de qualquer regra: quem barra edição de CONCLUIDA/CANCELADA é validarEditavel
        UUID cliente = UUID.randomUUID();
        MontagemMostruario concluida = montagem(1L, cliente, StatusMontagem.CONCLUIDA);
        assertThatCode(() -> validation.validarTrocaCliente(concluida, cliente, Optional.empty())).doesNotThrowAnyException();
    }

    @Test
    void mensagemDeClienteOcupadoCitaOIdEOStatusDaOutraMontagem() {
        Optional<MontagemMostruario> existente = Optional.of(montagem(1234L, UUID.randomUUID(), StatusMontagem.EM_MONTAGEM));
        assertThatThrownBy(() -> validation.validarSemMontagemEmAndamento(existente))
                .hasMessage("O cliente já possui a montagem 1234 com status EM_MONTAGEM. Conclua ou cancele antes de criar outra");
    }

    // ---------- serviço: a regra está ligada ----------

    @Test
    void criacaoConsultaOClienteDoRequestSoComAbertaEEmMontagem() {
        UUID clienteId = UUID.randomUUID();
        when(repository.findFirstByCliente_IdAndStatusInOrderById(any(), any())).thenReturn(Optional.empty());
        when(clienteService.findByIdAndAtivo(clienteId)).thenReturn(cliente(clienteId));
        when(repository.save(any(MontagemMostruario.class))).thenAnswer(i -> i.getArgument(0));

        service.create(request(null, clienteId));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<StatusMontagem>> status = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findFirstByCliente_IdAndStatusInOrderById(eq(clienteId), status.capture());
        // CONCLUIDA e CANCELADA nao podem estar na consulta, senao bloqueariam uma nova montagem
        assertThat(status.getValue()).containsExactlyInAnyOrder(StatusMontagem.ABERTA, StatusMontagem.EM_MONTAGEM);
    }

    @Test
    void criacaoBarradaNaoBuscaClienteNemGrava() {
        UUID clienteId = UUID.randomUUID();
        when(repository.findFirstByCliente_IdAndStatusInOrderById(any(), any()))
                .thenReturn(Optional.of(montagem(5L, clienteId, StatusMontagem.ABERTA)));

        assertThatThrownBy(() -> service.create(request(null, clienteId)))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("montagem 5");

        verifyNoInteractions(clienteService);
        verify(repository, never()).save(any());
    }

    @Test
    void trocaDeClienteConsultaOutrasMontagensIgnorandoAPropria() {
        UUID atualId = UUID.randomUUID();
        UUID novoId = UUID.randomUUID();
        MontagemMostruario atual = montagem(10L, atualId, StatusMontagem.ABERTA);
        when(repository.findById(10L)).thenReturn(Optional.of(atual));
        when(repository.findFirstByCliente_IdAndStatusInAndIdNotOrderById(any(), any(), anyLong())).thenReturn(Optional.empty());
        when(clienteService.findByIdAndAtivo(novoId)).thenReturn(cliente(novoId));

        service.update(request(10L, novoId));

        verify(repository).findFirstByCliente_IdAndStatusInAndIdNotOrderById(eq(novoId), eq(MontagemValidation.STATUS_EM_ANDAMENTO), eq(10L));
        assertThat(atual.getCliente().getId()).isEqualTo(novoId);
    }

    @Test
    void trocaBarradaMantemOClienteOriginal() {
        UUID atualId = UUID.randomUUID();
        UUID ocupadoId = UUID.randomUUID();
        MontagemMostruario atual = montagem(10L, atualId, StatusMontagem.ABERTA);
        when(repository.findById(10L)).thenReturn(Optional.of(atual));
        when(repository.findFirstByCliente_IdAndStatusInAndIdNotOrderById(any(), any(), anyLong()))
                .thenReturn(Optional.of(montagem(11L, ocupadoId, StatusMontagem.EM_MONTAGEM)));

        assertThatThrownBy(() -> service.update(request(10L, ocupadoId)))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("montagem 11 com status EM_MONTAGEM");

        assertThat(atual.getCliente().getId()).isEqualTo(atualId);
        verifyNoInteractions(clienteService);
    }

    @Test
    void edicaoSemTrocarClienteNaoBarraMesmoComDuplicataLegada() {
        // dados antigos podem ter 2 montagens em andamento do mesmo cliente: editar uma delas continua permitido
        UUID clienteId = UUID.randomUUID();
        MontagemMostruario atual = montagem(10L, clienteId, StatusMontagem.ABERTA);
        when(repository.findById(10L)).thenReturn(Optional.of(atual));
        when(repository.findFirstByCliente_IdAndStatusInAndIdNotOrderById(any(), any(), anyLong()))
                .thenReturn(Optional.of(montagem(12L, clienteId, StatusMontagem.ABERTA)));

        assertThatCode(() -> service.update(request(10L, clienteId))).doesNotThrowAnyException();

        assertThat(atual.getDataEntrega()).isEqualTo(LocalDate.of(2031, 5, 10));
        verify(clienteService, never()).findByIdAndAtivo(any());
    }

    @Test
    void edicaoDeMontagemConcluidaOuCanceladaBarraAntesDeConsultarOCliente() {
        for (StatusMontagem status : List.of(StatusMontagem.CONCLUIDA, StatusMontagem.CANCELADA)) {
            MontagemMostruario encerrada = montagem(20L, UUID.randomUUID(), status);
            when(repository.findById(20L)).thenReturn(Optional.of(encerrada));

            assertThatThrownBy(() -> service.update(request(20L, UUID.randomUUID())))
                    .isInstanceOf(ItemInvalidoException.class)
                    .hasMessageContaining("está " + status + " e não pode ser alterada");
        }
        verify(repository, never()).findFirstByCliente_IdAndStatusInAndIdNotOrderById(any(), any(), anyLong());
    }
}
