package com.manager.app.agenda;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgendaMarcadorValidationTest {

    private static final LocalDate DIA = LocalDate.of(2026, 3, 12);

    private AgendaMarcadorRepository repository;
    private AgendaMarcadorValidation validation;

    @BeforeEach
    void setUp() {
        repository = mock(AgendaMarcadorRepository.class);
        validation = new AgendaMarcadorValidation(repository);
    }

    private AgendaMarcador marcador(AgendaMarcadorTipo tipo, LocalDate data, LocalDate conferencia, LocalDate acerto) {
        AgendaMarcador m = new AgendaMarcador();
        m.setNome("TESTE");
        m.setTipo(tipo);
        m.setData(data);
        m.setDataConferencia(conferencia);
        m.setDataAcerto(acerto);
        return m;
    }

    // ---------- TRABALHA ----------

    @Test
    void trabalhaComDatasEmOrdemEPermitido() {
        AgendaMarcador m = marcador(AgendaMarcadorTipo.TRABALHA, DIA, DIA.plusDays(11), DIA.plusDays(28));
        assertThatCode(() -> validation.datasPorTipo(m)).doesNotThrowAnyException();
    }

    @Test
    void trabalhaComConferenciaEAcertoNoProprioDiaEPermitido() {
        AgendaMarcador m = marcador(AgendaMarcadorTipo.TRABALHA, DIA, DIA, DIA);
        assertThatCode(() -> validation.datasPorTipo(m)).doesNotThrowAnyException();
    }

    @Test
    void trabalhaPodeAtravessarAnoDezembroParaFevereiro() {
        AgendaMarcador m = marcador(AgendaMarcadorTipo.TRABALHA,
                LocalDate.of(2026, 12, 10), LocalDate.of(2027, 1, 20), LocalDate.of(2027, 2, 15));
        assertThatCode(() -> validation.datasPorTipo(m)).doesNotThrowAnyException();
    }

    @Test
    void trabalhaSemConferenciaEBarrado() {
        AgendaMarcador m = marcador(AgendaMarcadorTipo.TRABALHA, DIA, null, DIA.plusDays(5));
        assertThatThrownBy(() -> validation.datasPorTipo(m))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessage("Marcador do tipo TRABALHA exige data da conferência e data do acerto");
    }

    @Test
    void trabalhaSemAcertoEBarrado() {
        AgendaMarcador m = marcador(AgendaMarcadorTipo.TRABALHA, DIA, DIA.plusDays(5), null);
        assertThatThrownBy(() -> validation.datasPorTipo(m))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessage("Marcador do tipo TRABALHA exige data da conferência e data do acerto");
    }

    @Test
    void trabalhaSemNenhumaDataEBarrado() {
        AgendaMarcador m = marcador(AgendaMarcadorTipo.TRABALHA, DIA, null, null);
        assertThatThrownBy(() -> validation.datasPorTipo(m)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void trabalhaComConferenciaAntesDoDiaEBarrado() {
        AgendaMarcador m = marcador(AgendaMarcadorTipo.TRABALHA, DIA, DIA.minusDays(1), DIA.plusDays(5));
        assertThatThrownBy(() -> validation.datasPorTipo(m))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessage("A data da conferência não pode ser anterior à data do marcador");
    }

    @Test
    void trabalhaComAcertoAntesDaConferenciaEBarrado() {
        AgendaMarcador m = marcador(AgendaMarcadorTipo.TRABALHA, DIA, DIA.plusDays(10), DIA.plusDays(9));
        assertThatThrownBy(() -> validation.datasPorTipo(m))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessage("A data do acerto não pode ser anterior à data da conferência");
    }

    // ---------- NAO_TRABALHA ----------

    @Test
    void naoTrabalhaSemDatasEPermitido() {
        AgendaMarcador m = marcador(AgendaMarcadorTipo.NAO_TRABALHA, DIA, null, null);
        assertThatCode(() -> validation.datasPorTipo(m)).doesNotThrowAnyException();
    }

    @Test
    void naoTrabalhaComConferenciaEBarrado() {
        AgendaMarcador m = marcador(AgendaMarcadorTipo.NAO_TRABALHA, DIA, DIA.plusDays(1), null);
        assertThatThrownBy(() -> validation.datasPorTipo(m))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessage("Marcador do tipo NAO_TRABALHA não pode ter data da conferência nem data do acerto");
    }

    @Test
    void naoTrabalhaComAcertoEBarrado() {
        AgendaMarcador m = marcador(AgendaMarcadorTipo.NAO_TRABALHA, DIA, null, DIA.plusDays(1));
        assertThatThrownBy(() -> validation.datasPorTipo(m)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void naoTrabalhaComAmbasAsDatasEBarrado() {
        AgendaMarcador m = marcador(AgendaMarcadorTipo.NAO_TRABALHA, DIA, DIA.plusDays(1), DIA.plusDays(2));
        assertThatThrownBy(() -> validation.datasPorTipo(m)).isInstanceOf(ItemInvalidoException.class);
    }

    // ---------- 1 marcador por dia ----------

    @Test
    void createEmDiaLivreNaoLanca() {
        when(repository.existsByData(DIA)).thenReturn(false);
        assertThatCode(() -> validation.dataExistenteCreate(DIA)).doesNotThrowAnyException();
    }

    @Test
    void createEmDiaOcupadoLancaConflito() {
        when(repository.existsByData(DIA)).thenReturn(true);
        assertThatThrownBy(() -> validation.dataExistenteCreate(DIA))
                .isInstanceOf(ObjetoExistenteException.class)
                .hasMessage("Já existe um marcador na data 2026-03-12");
    }

    @Test
    void updateIgnoraOProprioMarcador() {
        when(repository.existsByDataAndIdNot(DIA, 7L)).thenReturn(false);
        assertThatCode(() -> validation.dataExistenteUpdate(DIA, 7L)).doesNotThrowAnyException();
    }

    @Test
    void updateParaDiaDeOutroMarcadorLancaConflito() {
        when(repository.existsByDataAndIdNot(DIA, 7L)).thenReturn(true);
        assertThatThrownBy(() -> validation.dataExistenteUpdate(DIA, 7L))
                .isInstanceOf(ObjetoExistenteException.class);
    }

    // ---------- periodo do GET ----------

    @Test
    void periodoValidoENaoLanca() {
        assertThatCode(() -> validation.periodo(DIA, DIA.plusDays(30))).doesNotThrowAnyException();
        assertThatCode(() -> validation.periodo(DIA, DIA)).doesNotThrowAnyException();
    }

    @Test
    void periodoSemInicioOuFimLanca() {
        assertThatThrownBy(() -> validation.periodo(null, DIA)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> validation.periodo(DIA, null)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void periodoComFimAntesDoInicioLanca() {
        assertThatThrownBy(() -> validation.periodo(DIA, DIA.minusDays(1)))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessage("Data final não pode ser anterior à data inicial");
    }
}
