package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// regras dos anexos do título a receber (a validação do arquivo em si é de AnexoValidacao, já coberta em RecorrenciaAnexoCalculoTest)
class ContaReceberAnexoCalculoTest {

    @Test
    void noTituloSoSeAnexaDocumento() {
        ContaReceberAnexoCalculo.validarTipoNoTitulo(TipoAnexo.DOCUMENTO);
        assertThatThrownBy(() -> ContaReceberAnexoCalculo.validarTipoNoTitulo(TipoAnexo.COMPROVANTE))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("recebimento");
        assertThatThrownBy(() -> ContaReceberAnexoCalculo.validarTipoNoTitulo(TipoAnexo.BOLETO)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> ContaReceberAnexoCalculo.validarTipoNoTitulo(null)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void limiteDeDezAnexosSomaDocumentosEComprovantes() {
        ContaReceberAnexoCalculo.validarAnexavel(SituacaoContaReceber.ABERTO, 9);
        assertThatThrownBy(() -> ContaReceberAnexoCalculo.validarAnexavel(SituacaoContaReceber.ABERTO, 10))
                .isInstanceOf(ObjetoEmUsoException.class).hasMessage("O título já possui o número máximo de 10 anexos.");
    }

    @Test
    void tituloRecebidoAceitaAnexosECanceladoNao() {
        ContaReceberAnexoCalculo.validarAnexavel(SituacaoContaReceber.RECEBIDO, 0);
        ContaReceberAnexoCalculo.validarAnexavel(SituacaoContaReceber.RECEBIDO_PARCIAL, 0);
        assertThatThrownBy(() -> ContaReceberAnexoCalculo.validarAnexavel(SituacaoContaReceber.CANCELADO, 0))
                .isInstanceOf(ItemInvalidoException.class).hasMessage("O título cancelado não aceita novos anexos.");
        ContaReceberAnexoCalculo.validarRemovivel(SituacaoContaReceber.RECEBIDO);
        assertThatThrownBy(() -> ContaReceberAnexoCalculo.validarRemovivel(SituacaoContaReceber.CANCELADO)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void recebimentoEstornadoNaoAceitaNovoComprovante() {
        ContaReceberAnexoCalculo.validarRecebimentoAceitaComprovante(false);
        assertThatThrownBy(() -> ContaReceberAnexoCalculo.validarRecebimentoAceitaComprovante(true))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("estornado");
    }

    @Test
    void permissaoExigidaDependeDoTipo() {
        assertThat(ContaReceberAnexoCalculo.permissaoExigida(TipoAnexo.DOCUMENTO)).isEqualTo("CONTA_RECEBER_GERENCIAR");
        assertThat(ContaReceberAnexoCalculo.permissaoExigida(TipoAnexo.COMPROVANTE)).isEqualTo("CONTA_RECEBER_RECEBER");
    }

    @Test
    void pastaDoTituloAReceberNuncaColideComADoTituloAPagar() {
        assertThat(ContaReceberAnexoCalculo.pasta(12L)).isEqualTo("receber/12");
        assertThat(ContaReceberAnexoCalculo.pasta(12L)).isNotEqualTo("12");
    }
}
