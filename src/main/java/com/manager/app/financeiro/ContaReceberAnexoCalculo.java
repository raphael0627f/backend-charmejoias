package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;

// regras dos anexos do título a receber sem acesso a banco de dados (testáveis em JUnit puro)
public final class ContaReceberAnexoCalculo {

    public static final String PERMISSAO_DOCUMENTO = "CONTA_RECEBER_GERENCIAR";
    public static final String PERMISSAO_COMPROVANTE = "CONTA_RECEBER_RECEBER";

    private ContaReceberAnexoCalculo() {}

    // no título só se anexa DOCUMENTO; o comprovante é anexado ao recebimento
    public static void validarTipoNoTitulo(TipoAnexo tipo) {
        if (tipo == null) throw new ItemInvalidoException("Informe o tipo do anexo: DOCUMENTO.");
        if (tipo == TipoAnexo.COMPROVANTE) throw new ItemInvalidoException("O comprovante deve ser anexado diretamente ao recebimento.");
        if (tipo != TipoAnexo.DOCUMENTO) throw new ItemInvalidoException("Tipo de anexo inválido. Informe DOCUMENTO.");
    }

    // título cancelado só permite download; o limite de 10 soma documentos e comprovantes
    public static void validarAnexavel(SituacaoContaReceber situacao, long quantidadeAtual) {
        if (situacao == SituacaoContaReceber.CANCELADO) throw new ItemInvalidoException("O título cancelado não aceita novos anexos.");
        if (quantidadeAtual >= AnexoValidacao.MAXIMO_POR_TITULO) {
            throw new ObjetoEmUsoException("O título já possui o número máximo de " + AnexoValidacao.MAXIMO_POR_TITULO + " anexos.");
        }
    }

    public static void validarRecebimentoAceitaComprovante(boolean estornado) {
        if (estornado) throw new ItemInvalidoException("O recebimento está estornado e não aceita novos comprovantes.");
    }

    public static void validarRemovivel(SituacaoContaReceber situacao) {
        if (situacao == SituacaoContaReceber.CANCELADO) throw new ItemInvalidoException("O título cancelado não permite remover anexos.");
    }

    // comprovante exige a permissão de recebimento; documento, a de gerência
    public static String permissaoExigida(TipoAnexo tipo) {
        return tipo == TipoAnexo.COMPROVANTE ? PERMISSAO_COMPROVANTE : PERMISSAO_DOCUMENTO;
    }

    public static String pasta(Long tituloId) {
        return "receber/" + tituloId;
    }
}
