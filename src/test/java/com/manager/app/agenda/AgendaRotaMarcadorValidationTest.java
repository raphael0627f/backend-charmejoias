package com.manager.app.agenda;

import com.manager.app.cidade.Cidade;
import com.manager.app.cidade.CidadeMapper;
import com.manager.app.cidade.CidadeRepository;
import com.manager.app.cidade.CidadeService;
import com.manager.app.cidade.CidadeTipo;
import com.manager.app.shared.TextoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgendaRotaMarcadorValidationTest {

    private AgendaMarcadorRepository marcadorRepository;
    private AgendaRotaRepository rotaRepository;
    private AgendaMarcadorValidation marcadorValidation;
    private AgendaRotaValidation rotaValidation;

    @BeforeEach
    void setUp() {
        marcadorRepository = mock(AgendaMarcadorRepository.class);
        rotaRepository = mock(AgendaRotaRepository.class);
        marcadorValidation = new AgendaMarcadorValidation(marcadorRepository);
        rotaValidation = new AgendaRotaValidation(rotaRepository, marcadorRepository);
    }

    private Cidade cidade(Long id, String nome, String uf, CidadeTipo tipo, String municipio) {
        Cidade c = new Cidade();
        c.setId(id);
        c.setNome(nome);
        c.setUf(uf);
        c.setTipo(tipo);
        c.setMunicipioNome(municipio);
        return c;
    }

    private AgendaMarcador marcador(AgendaMarcadorTipo tipo, String nome) {
        AgendaMarcador m = new AgendaMarcador();
        m.setTipo(tipo);
        m.setNome(nome);
        return m;
    }

    // ---------- marcador: campos por tipo ----------

    @Test
    void trabalhaExigeRota() {
        assertThatThrownBy(() -> marcadorValidation.camposPorTipo(marcador(AgendaMarcadorTipo.TRABALHA, null), null, null))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("exige uma rota");
    }

    @Test
    void trabalhaComRotaSemNomeEhValido() {
        assertThatCode(() -> marcadorValidation.camposPorTipo(marcador(AgendaMarcadorTipo.TRABALHA, null), 1L, List.of(5L)))
                .doesNotThrowAnyException();
    }

    @Test
    void folgaExigeNome() {
        assertThatThrownBy(() -> marcadorValidation.camposPorTipo(marcador(AgendaMarcadorTipo.NAO_TRABALHA, "  "), null, null))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("Nome do marcador é obrigatório");
    }

    @Test
    void folgaNaoPodeTerRota() {
        assertThatThrownBy(() -> marcadorValidation.camposPorTipo(marcador(AgendaMarcadorTipo.NAO_TRABALHA, "Feriado"), 1L, null))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("não pode ter rota");
    }

    @Test
    void folgaNaoPodeTerCidadesExtras() {
        assertThatThrownBy(() -> marcadorValidation.camposPorTipo(marcador(AgendaMarcadorTipo.NAO_TRABALHA, "Feriado"), null, List.of(3L)))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("cidades extras");
    }

    @Test
    void folgaComListaDeExtrasVaziaEhValida() {
        assertThatCode(() -> marcadorValidation.camposPorTipo(marcador(AgendaMarcadorTipo.NAO_TRABALHA, "Feriado"), null, List.of()))
                .doesNotThrowAnyException();
    }

    // ---------- extras x cidades da rota ----------

    @Test
    void extraQueJaPertenceARotaEhRecusada() {
        Cidade campinas = cidade(1L, "Campinas", "SP", CidadeTipo.MUNICIPIO, null);
        AgendaRota rota = new AgendaRota();
        rota.setCidades(new ArrayList<>(List.of(campinas, cidade(2L, "Jundiaí", "SP", CidadeTipo.MUNICIPIO, null))));
        assertThatThrownBy(() -> marcadorValidation.extrasForaDaRota(rota, List.of(campinas)))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("Campinas - SP").hasMessageContaining("já pertence à rota");
    }

    @Test
    void extraForaDaRotaEhAceita() {
        AgendaRota rota = new AgendaRota();
        rota.setCidades(new ArrayList<>(List.of(cidade(1L, "Campinas", "SP", CidadeTipo.MUNICIPIO, null))));
        assertThatCode(() -> marcadorValidation.extrasForaDaRota(rota, List.of(cidade(9L, "Santos", "SP", CidadeTipo.MUNICIPIO, null))))
                .doesNotThrowAnyException();
    }

    // ---------- rota ----------

    @Test
    void rotaExigeAoMenosUmaCidade() {
        assertThatThrownBy(() -> rotaValidation.cidadesObrigatorias(null)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> rotaValidation.cidadesObrigatorias(List.of())).isInstanceOf(ItemInvalidoException.class);
        assertThatCode(() -> rotaValidation.cidadesObrigatorias(List.of(1L))).doesNotThrowAnyException();
    }

    @Test
    void nomeDuplicadoNoCreateEhConflito() {
        when(rotaRepository.existsByNomeIgnoreCase("Rota A")).thenReturn(true);
        assertThatThrownBy(() -> rotaValidation.nomeExistenteCreate("Rota A")).isInstanceOf(ObjetoExistenteException.class);
    }

    @Test
    void nomeDuplicadoNoUpdateIgnoraAPropriaRota() {
        when(rotaRepository.existsByNomeIgnoreCaseAndIdNot("Rota A", 1L)).thenReturn(false);
        when(rotaRepository.existsByNomeIgnoreCaseAndIdNot("Rota A", 2L)).thenReturn(true);
        assertThatCode(() -> rotaValidation.nomeExistenteUpdate("Rota A", 1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> rotaValidation.nomeExistenteUpdate("Rota A", 2L)).isInstanceOf(ObjetoExistenteException.class);
    }

    @Test
    void rotaEmUsoNaoPodeSerExcluida() {
        when(marcadorRepository.countByRota_Id(7L)).thenReturn(2L);
        assertThatThrownBy(() -> rotaValidation.podeExcluir(7L))
                .isInstanceOf(ObjetoEmUsoException.class).hasMessageContaining("2 marcadores");
    }

    @Test
    void rotaSemUsoPodeSerExcluida() {
        when(marcadorRepository.countByRota_Id(7L)).thenReturn(0L);
        assertThatCode(() -> rotaValidation.podeExcluir(7L)).doesNotThrowAnyException();
    }

    // ---------- cidades: lista de ids, descricao e busca ----------

    @Test
    void cidadesRepetidasOuInexistentesSaoRecusadasEOrdemEhPreservada() {
        CidadeRepository repo = mock(CidadeRepository.class);
        CidadeService service = new CidadeService(repo);
        Cidade a = cidade(1L, "A", "SP", CidadeTipo.MUNICIPIO, null);
        Cidade b = cidade(2L, "B", "SP", CidadeTipo.MUNICIPIO, null);
        when(repo.findAllById(anyList())).thenReturn(List.of(a, b));

        assertThat(service.findAllOrdenado(List.of(2L, 1L))).containsExactly(b, a);
        assertThatThrownBy(() -> service.findAllOrdenado(List.of(1L, 1L)))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("repetida");
        assertThatThrownBy(() -> service.findAllOrdenado(List.of(1L, 3L)))
                .isInstanceOf(ObjetoNaoEncontradoException.class);
    }

    @Test
    void descricaoDeMunicipioEDistrito() {
        CidadeMapper mapper = new com.manager.app.cidade.CidadeMapperImpl();
        assertThat(mapper.descricao(cidade(1L, "Campinas", "SP", CidadeTipo.MUNICIPIO, null))).isEqualTo("Campinas - SP");
        assertThat(mapper.descricao(cidade(2L, "Água Rasa", "SP", CidadeTipo.DISTRITO, "São Paulo")))
                .isEqualTo("Água Rasa (distrito de São Paulo) - SP");
    }

    @Test
    void buscaNormalizadaSemAcentoEMinuscula() {
        assertThat(TextoUtils.normalizarBusca("  São   JOSÉ  do Rio Preto ")).isEqualTo("sao jose do rio preto");
        assertThat(TextoUtils.normalizarBusca(null)).isEmpty();
    }
}
