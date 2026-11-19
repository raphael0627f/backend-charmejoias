package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.CategoriaDespesaRequestDTO;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// o grupo reservado "Devoluções a clientes" (e subgrupos) não pode ser renomeado, movido, desativado, excluído nem receber subgrupos
class CategoriaDespesaReservadaServiceTest {

    private CategoriaDespesaRepository repository;
    private CategoriaDespesaService service;
    private CategoriaDespesa reservada;
    private CategoriaDespesa subReservada;
    private CategoriaDespesa comum;

    @BeforeEach
    void preparar() {
        repository = mock(CategoriaDespesaRepository.class);
        service = new CategoriaDespesaService(repository, new FinanceiroMapper());
        reservada = cat(1, "Devoluções a clientes", null);
        subReservada = cat(2, "Antigo", reservada);
        comum = cat(3, "Aluguel", null);
        for (CategoriaDespesa c : List.of(reservada, subReservada, comum)) when(repository.findById(c.getId())).thenReturn(Optional.of(c));
        when(repository.findAll()).thenReturn(List.of(reservada, subReservada, comum));
    }

    private static CategoriaDespesa cat(long id, String nome, CategoriaDespesa pai) {
        CategoriaDespesa c = new CategoriaDespesa();
        c.setId(id);
        c.setNome(nome);
        c.setPai(pai);
        c.setAtivo(true);
        c.setTipo(TipoCategoriaDespesa.OUTROS);
        return c;
    }

    private static CategoriaDespesaRequestDTO req(String nome, Long paiId) {
        return new CategoriaDespesaRequestDTO(nome, paiId, TipoCategoriaDespesa.OUTROS, 8);
    }

    @Test
    void naoRenomeiaNemMoveNemInativaNemExclui() {
        assertThatThrownBy(() -> service.atualizar(1L, req("Outro nome", null))).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("reservada");
        assertThatThrownBy(() -> service.atualizar(1L, req("Devoluções a clientes", 3L))).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("reservada");
        assertThatThrownBy(() -> service.inativar(1L)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("reservada");
        assertThatThrownBy(() -> service.excluir(1L)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("reservada");
        assertThatThrownBy(() -> service.atualizar(2L, req("Novo", 1L))).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> service.inativar(2L)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("reservada");
        assertThatThrownBy(() -> service.excluir(2L)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("reservada");
        verify(repository, never()).delete(any());
        verify(repository, never()).save(any());
    }

    @Test
    void naoRecebeSubgrupoNemAdotaOutraCategoria() {
        assertThatThrownBy(() -> service.criar(req("Novo sub", 1L))).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("reservada");
        assertThatThrownBy(() -> service.atualizar(3L, req("Aluguel", 1L))).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("reservada");
    }

    @Test
    void aConsultaContinuaEOutrasCategoriasSeguemEditaveis() {
        assertThat(service.obter(1L).nome()).isEqualTo("Devoluções a clientes");
        assertThat(service.plana(null, null)).hasSize(3);
        assertThat(service.plana(null, null, true)).hasSize(1);
        assertThat(service.atualizar(3L, req("Aluguel de loja", null)).nome()).isEqualTo("Aluguel de loja");
        assertThat(service.inativar(3L).ativo()).isFalse();
    }

    @Test
    void permiteAlterarApenasOrdemETipoDoGrupoReservado() {
        assertThat(service.atualizar(1L, req("Devoluções a clientes", null)).nome()).isEqualTo("Devoluções a clientes");
    }
}
