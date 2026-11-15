package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.CategoriaReceitaRequestDTO;
import com.manager.app.financeiro.dto.CategoriaReceitaResponseDTO;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// regras das categorias de receita (dois níveis, nome único por pai, desativação/reativação) com o repositório simulado
class CategoriaReceitaServiceTest {

    private CategoriaReceitaRepository repository;
    private CategoriaReceitaService service;

    @BeforeEach
    void preparar() {
        repository = mock(CategoriaReceitaRepository.class);
        service = new CategoriaReceitaService(repository, new FinanceiroMapper());
    }

    private static CategoriaReceita categoria(long id, String nome, CategoriaReceita pai, boolean ativo) {
        CategoriaReceita c = new CategoriaReceita();
        c.setId(id);
        c.setNome(nome);
        c.setPai(pai);
        c.setAtivo(ativo);
        return c;
    }

    private void existe(CategoriaReceita c) {
        when(repository.findById(c.getId())).thenReturn(Optional.of(c));
    }

    @Test
    void criaGrupoESubgrupoAtivos() {
        CategoriaReceita grupo = categoria(1, "Vendas", null, true);
        existe(grupo);
        CategoriaReceitaResponseDTO g = service.criar(new CategoriaReceitaRequestDTO("  Serviços  ", null, 3));
        assertThat(g.nome()).isEqualTo("Serviços");
        assertThat(g.paiId()).isNull();
        assertThat(g.ativo()).isTrue();
        assertThat(g.ordem()).isEqualTo(3);
        CategoriaReceitaResponseDTO s = service.criar(new CategoriaReceitaRequestDTO("Vendas de joias", 1L, null));
        assertThat(s.paiId()).isEqualTo(1L);
        assertThat(s.paiNome()).isEqualTo("Vendas");
        assertThat(s.ordem()).isZero();
    }

    @Test
    void recusaTerceiroNivel() {
        CategoriaReceita grupo = categoria(1, "Vendas", null, true);
        CategoriaReceita sub = categoria(2, "Vendas de joias", grupo, true);
        existe(grupo);
        existe(sub);
        assertThatThrownBy(() -> service.criar(new CategoriaReceitaRequestDTO("Terceiro", 2L, null)))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("dois níveis");
    }

    @Test
    void grupoComSubgruposNaoPodeTornarseSubgrupo() {
        CategoriaReceita a = categoria(1, "Vendas", null, true);
        CategoriaReceita b = categoria(5, "Serviços", null, true);
        existe(a);
        existe(b);
        when(repository.existsByPaiId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.atualizar(1L, new CategoriaReceitaRequestDTO("Vendas", 5L, null)))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("não pode se tornar subgrupo");
    }

    @Test
    void categoriaNaoPodeSerPaiDeSiMesma() {
        existe(categoria(1, "Vendas", null, true));
        assertThatThrownBy(() -> service.atualizar(1L, new CategoriaReceitaRequestDTO("Vendas", 1L, null)))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("pai de si mesma");
    }

    @Test
    void recusaNomeVazioOuMuitoLongo() {
        assertThatThrownBy(() -> service.criar(new CategoriaReceitaRequestDTO("   ", null, null)))
                .isInstanceOf(ItemInvalidoException.class).hasMessage("O nome é obrigatório.");
        assertThatThrownBy(() -> service.criar(new CategoriaReceitaRequestDTO("x".repeat(101), null, null)))
                .isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void nomeDuplicadoNoMesmoPaiEhRecusadoSemDiferenciarCaixa() {
        when(repository.existsByNomeIgnoreCaseAndPaiIsNull("VENDAS")).thenReturn(true);
        assertThatThrownBy(() -> service.criar(new CategoriaReceitaRequestDTO("VENDAS", null, null)))
                .isInstanceOf(ObjetoExistenteException.class).hasMessage("Já existe um grupo de categorias de receita com o nome \"VENDAS\".");
        CategoriaReceita grupo = categoria(1, "Vendas", null, true);
        existe(grupo);
        when(repository.existsByNomeIgnoreCaseAndPaiId("vendas de joias", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.criar(new CategoriaReceitaRequestDTO("vendas de joias", 1L, null)))
                .isInstanceOf(ObjetoExistenteException.class).hasMessage("Já existe uma categoria de receita com o nome \"vendas de joias\" no grupo \"Vendas\".");
        verify(repository, never()).save(any());
    }

    @Test
    void mesmoNomeEmPaisDiferentesEhPermitido() {
        CategoriaReceita grupo = categoria(1, "Outras receitas", null, true);
        existe(grupo);
        // "Outras receitas" já existe como grupo, mas não dentro do grupo "Outras receitas"
        when(repository.existsByNomeIgnoreCaseAndPaiIsNull("Outras receitas")).thenReturn(true);
        when(repository.existsByNomeIgnoreCaseAndPaiId("Outras receitas", 1L)).thenReturn(false);
        assertThat(service.criar(new CategoriaReceitaRequestDTO("Outras receitas", 1L, null)).paiId()).isEqualTo(1L);
    }

    @Test
    void naoCriaSubgrupoEmGrupoDesativado() {
        existe(categoria(1, "Vendas", null, false));
        assertThatThrownBy(() -> service.criar(new CategoriaReceitaRequestDTO("Vendas de joias", 1L, null)))
                .isInstanceOf(ItemInvalidoException.class).hasMessage("O grupo \"Vendas\" está desativado.");
    }

    @Test
    void desativarGrupoComSubgrupoAtivoEhRecusado() {
        existe(categoria(1, "Vendas", null, true));
        when(repository.existsByPaiIdAndAtivoTrue(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.inativar(1L)).isInstanceOf(ObjetoEmUsoException.class)
                .hasMessage("Não é possível desativar o grupo \"Vendas\", pois ele possui subgrupos ativos. Desative os subgrupos primeiro.");
    }

    @Test
    void desativaGrupoSemSubgruposAtivosEReativa() {
        CategoriaReceita grupo = categoria(1, "Vendas", null, true);
        existe(grupo);
        assertThat(service.inativar(1L).ativo()).isFalse();
        assertThat(service.ativar(1L).ativo()).isTrue();
    }

    @Test
    void reativarSubgrupoComGrupoDesativadoEhRecusado() {
        CategoriaReceita grupo = categoria(1, "Vendas", null, false);
        CategoriaReceita sub = categoria(2, "Vendas de joias", grupo, false);
        existe(sub);
        assertThatThrownBy(() -> service.ativar(2L)).isInstanceOf(ObjetoEmUsoException.class)
                .hasMessage("Não é possível reativar o subgrupo, pois o grupo \"Vendas\" está desativado. Reative o grupo antes de reativar o subgrupo.");
        assertThat(sub.isAtivo()).isFalse();
    }

    @Test
    void excluirGrupoComSubgruposEhRecusadoESemUsoEhPermitido() {
        CategoriaReceita grupo = categoria(1, "Vendas", null, true);
        existe(grupo);
        when(repository.existsByPaiId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.excluir(1L)).isInstanceOf(ObjetoEmUsoException.class).hasMessageContaining("possui subgrupos");
        verify(repository, never()).delete(any());

        CategoriaReceita sub = categoria(2, "Vendas de joias", grupo, true);
        existe(sub);
        service.excluir(2L);
        verify(repository).delete(sub);
    }

    @Test
    void arvoreMostraGruposComSubgruposEFiltraPorAtivo() {
        CategoriaReceita g1 = categoria(1, "Vendas", null, true);
        CategoriaReceita g2 = categoria(2, "Serviços", null, false);
        CategoriaReceita s1 = categoria(3, "Vendas de joias", g1, true);
        CategoriaReceita s2 = categoria(4, "Vendas de semijoias", g1, false);
        when(repository.findAll()).thenReturn(List.of(g1, g2, s1, s2));
        List<CategoriaReceitaResponseDTO> todas = service.arvore(null);
        assertThat(todas).extracting(CategoriaReceitaResponseDTO::nome).containsExactly("Serviços", "Vendas");
        assertThat(todas.get(1).subcategorias()).extracting(CategoriaReceitaResponseDTO::nome).containsExactly("Vendas de joias", "Vendas de semijoias");
        List<CategoriaReceitaResponseDTO> ativas = service.arvore(true);
        assertThat(ativas).extracting(CategoriaReceitaResponseDTO::nome).containsExactly("Vendas");
        assertThat(ativas.get(0).subcategorias()).extracting(CategoriaReceitaResponseDTO::nome).containsExactly("Vendas de joias");
    }
}
