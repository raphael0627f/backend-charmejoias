package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.BancoResponseDTO;
import com.manager.app.financeiro.dto.CadastroSimplesResponseDTO;
import com.manager.app.financeiro.dto.CategoriaDespesaResponseDTO;
import com.manager.app.financeiro.dto.CategoriaReceitaResponseDTO;
import com.manager.app.financeiro.dto.MovimentacaoResponseDTO;
import com.manager.app.usuario.Usuario;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

// conversão manual entidade -> DTO dos cadastros financeiros e da movimentação
@Component
public class FinanceiroMapper {

    public UsuarioResumoDTO usuario(Usuario u) {
        if (u == null) return null;
        UsuarioResumoDTO dto = new UsuarioResumoDTO();
        dto.setId(u.getId());
        dto.setUsuario(u.getUsuario());
        dto.setPrimeiroNome(u.getPrimeiroNome());
        dto.setSobrenome(u.getSobrenome());
        return dto;
    }

    // saldoAtual nulo = usuário sem permissão de ver saldos: saldoInicial e saldoAtual saem nulos
    public BancoResponseDTO banco(Banco b, BigDecimal saldoAtual) {
        boolean semSaldo = saldoAtual == null;
        return new BancoResponseDTO(b.getId(), b.getNome(), b.getTipo(), b.getCodigoBanco(), b.getAgencia(), b.getConta(),
                semSaldo ? null : CalculoFinanceiro.arredondar(b.getSaldoInicial()), b.getDataSaldoInicial(), b.getObservacao(), b.isAtivo(),
                semSaldo ? null : CalculoFinanceiro.arredondar(saldoAtual), b.getCreatedAt(), b.getUpdatedAt(), usuario(b.getCreatedBy()), usuario(b.getUpdatedBy()));
    }

    public CategoriaDespesaResponseDTO categoria(CategoriaDespesa c, List<CategoriaDespesaResponseDTO> subcategorias) {
        CategoriaDespesa pai = c.getPai();
        return new CategoriaDespesaResponseDTO(c.getId(), c.getNome(), pai == null ? null : pai.getId(), pai == null ? null : pai.getNome(),
                c.getTipo(), c.getOrdem(), c.isAtivo(), c.getCreatedAt(), c.getUpdatedAt(), usuario(c.getCreatedBy()), usuario(c.getUpdatedBy()),
                subcategorias);
    }

    public CategoriaReceitaResponseDTO categoriaReceita(CategoriaReceita c, List<CategoriaReceitaResponseDTO> subcategorias) {
        CategoriaReceita pai = c.getPai();
        return new CategoriaReceitaResponseDTO(c.getId(), c.getNome(), pai == null ? null : pai.getId(), pai == null ? null : pai.getNome(),
                c.getOrdem(), c.isAtivo(), c.getCreatedAt(), c.getUpdatedAt(), usuario(c.getCreatedBy()), usuario(c.getUpdatedBy()), subcategorias);
    }

    public CadastroSimplesResponseDTO formaPagamento(FormaPagamento f) {
        return new CadastroSimplesResponseDTO(f.getId(), f.getNome(), f.isAtivo(), f.getCreatedAt(), f.getUpdatedAt(),
                usuario(f.getCreatedBy()), usuario(f.getUpdatedBy()));
    }

    public CadastroSimplesResponseDTO centroCusto(CentroCusto c) {
        return new CadastroSimplesResponseDTO(c.getId(), c.getNome(), c.isAtivo(), c.getCreatedAt(), c.getUpdatedAt(),
                usuario(c.getCreatedBy()), usuario(c.getUpdatedBy()));
    }

    public MovimentacaoResponseDTO movimentacao(MovimentacaoBanco m, BigDecimal saldoApos) {
        return new MovimentacaoResponseDTO(m.getId(), m.getBanco().getId(), m.getBanco().getNome(), m.getData(), m.getTipo(),
                CalculoFinanceiro.arredondar(m.getValor()), m.getOrigem(), m.getDescricao(),
                m.getTransferencia() == null ? null : m.getTransferencia().getId(), m.isEstornada(),
                m.getEstornoDe() == null ? null : m.getEstornoDe().getId(), m.getMotivoEstorno(), m.getEstornadaEm(),
                usuario(m.getEstornadaPor()), usuario(m.getUsuario()), m.getCriadoEm(), saldoApos, null, null, m.getPagamentoId(), null, m.getRecebimentoId());
    }
}
