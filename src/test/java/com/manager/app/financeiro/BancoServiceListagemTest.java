package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.BancoResponseDTO;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// ordenação da lista de bancos: whitelist de campos expostos; campo inexistente = 400 formal
class BancoServiceListagemTest {

    private BancoService service;

    private static Banco banco(long id, String nome) {
        Banco b = new Banco();
        b.setId(id);
        b.setNome(nome);
        b.setAtivo(true);
        return b;
    }

    @BeforeEach
    void preparar() {
        BancoRepository repo = mock(BancoRepository.class);
        FinanceiroMapper mapper = mock(FinanceiroMapper.class);
        SaldoVisibilidade visibilidade = mock(SaldoVisibilidade.class);
        when(visibilidade.podeVerSaldo()).thenReturn(false);
        when(repo.findAll()).thenReturn(List.of(banco(2, "Beta"), banco(3, "alfa"), banco(1, "Gama")));
        when(mapper.banco(any(), any())).thenAnswer(i -> {
            Banco b = i.getArgument(0);
            return new BancoResponseDTO(b.getId(), b.getNome(), null, null, null, null, null, null, null, true, null, LocalDateTime.now(), null, null, null);
        });
        service = new BancoService(repo, mock(MovimentacaoBancoRepository.class), null, mapper, visibilidade);
    }

    private List<String> nomes(String sort) {
        return service.listar(null, null, sort).stream().map(BancoResponseDTO::nome).toList();
    }

    @Test
    void semSortOrdenaPorNome() {
        assertEquals(List.of("alfa", "Beta", "Gama"), nomes(null));
    }

    @Test
    void sortPorIdENomeComDirecao() {
        assertEquals(List.of("Gama", "Beta", "alfa"), nomes("id"));
        assertEquals(List.of("alfa", "Beta", "Gama"), nomes("id,desc"));
        assertEquals(List.of("Gama", "Beta", "alfa"), nomes("nome,desc"));
    }

    @Test
    void campoOuDirecaoInvalidosSao400() {
        assertThrows(ItemInvalidoException.class, () -> nomes("xpto"));
        assertThrows(ItemInvalidoException.class, () -> nomes("nome,cima"));
    }
}
