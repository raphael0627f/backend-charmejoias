package com.manager.app.montagem;

import com.manager.app.cliente.Cliente;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.mostruario.Mostruario;
import com.manager.app.montagem.dto.MontagemEtiquetaDTO;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// etiqueta da montagem: data do acerto vem do usuário e não exige consignação vinculada (sem Spring e sem banco)
class MontagemEtiquetaDataAcertoTest {

    private final MontagemValidation validation = new MontagemValidation();
    private final MontagemEtiquetaService service = new MontagemEtiquetaService(mock(MontagemMostruarioRepository.class), validation);

    private static MontagemMostruario montagem(StatusMontagem status) {
        Cliente cliente = new Cliente();
        cliente.setCodigo("C1");
        cliente.setNome("Maria Silva");
        Mostruario mostruario = new Mostruario();
        mostruario.setDescricao("MOSTRUARIO A");
        MontagemMostruario m = new MontagemMostruario();
        m.setId(5L);
        m.setCliente(cliente);
        m.setMostruario(mostruario);
        m.setStatus(status);
        m.setDataEntrega(LocalDate.of(2026, 11, 10));
        return m;
    }

    @Test
    void montagemSemConsignacaoPodeImprimirEmQualquerStatusExcetoCancelada() {
        for (StatusMontagem status : new StatusMontagem[]{StatusMontagem.ABERTA, StatusMontagem.EM_MONTAGEM, StatusMontagem.CONCLUIDA}) {
            MontagemMostruario m = montagem(status);
            assertThat(m.getConsignacoes()).isEmpty();
            assertThatCode(() -> validation.validarEtiqueta(m)).doesNotThrowAnyException();
        }
    }

    @Test
    void montagemCanceladaContinuaBarrada() {
        assertThatThrownBy(() -> validation.validarEtiqueta(montagem(StatusMontagem.CANCELADA)))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessage("Montagem 5 está CANCELADA e não pode ter a etiqueta impressa");
    }

    @Test
    void dataAcertoValidaEInterpretadaEmFormatoIso() {
        assertThat(validation.validarDataAcertoEtiqueta("2026-12-05")).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(validation.validarDataAcertoEtiqueta(" 2026-12-05 ")).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void semDataAcertoEBarrado() {
        assertThatThrownBy(() -> validation.validarDataAcertoEtiqueta(null))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("Informe a data do acerto");
        assertThatThrownBy(() -> validation.validarDataAcertoEtiqueta("  "))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("Informe a data do acerto");
    }

    @Test
    void dataAcertoEmFormatoInvalidoEBarrada() {
        for (String invalida : new String[]{"05/12/2026", "2026-13-40", "amanha", "20261205"}) {
            assertThatThrownBy(() -> validation.validarDataAcertoEtiqueta(invalida))
                    .isInstanceOf(ItemInvalidoException.class)
                    .hasMessage("Data do acerto inválida: '" + invalida + "'. Use o formato yyyy-MM-dd");
        }
    }

    @Test
    void montarDadosFormataADataRecebidaSemConsignacao() {
        MontagemEtiquetaDTO dados = service.montarDados(montagem(StatusMontagem.ABERTA), LocalDate.of(2026, 12, 5));
        assertThat(dados.getDataAcerto()).isEqualTo("05/12/2026");
        assertThat(dados.getDataEntrega()).isEqualTo("10/11/2026");
        assertThat(dados.getMostruario()).isEqualTo("MOSTRUARIO A");
        assertThat(dados.getSobrenomeCliente()).isEqualTo("Silva");
    }

    @Test
    void montarDadosIgnoraADataDaConsignacaoVinculada() {
        MontagemMostruario m = montagem(StatusMontagem.EM_MONTAGEM);
        Consignacao consignacao = new Consignacao();
        consignacao.setDataAcertoConsignacao(LocalDate.of(2027, 1, 20));
        m.getConsignacoes().add(consignacao);
        assertThat(service.montarDados(m, LocalDate.of(2026, 12, 5)).getDataAcerto()).isEqualTo("05/12/2026");
    }
}
