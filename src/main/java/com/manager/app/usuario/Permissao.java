package com.manager.app.usuario;

// conjunto fixo de permissões do sistema - uma por ação de escrita/sensível de cada módulo.
// Leitura (GET) não exige nenhuma permissão específica, só estar autenticado.
public enum Permissao {
    USUARIO_GERENCIAR,
    CLIENTE_GERENCIAR,
    PRODUTO_GERENCIAR,
    JOIA_GERENCIAR,
    CATEGORIA_GERENCIAR,
    FORNECEDOR_GERENCIAR,
    GRUPO_GERENCIAR,
    NCM_GERENCIAR,
    TRANSPORTADORA_GERENCIAR,
    EMPRESA_GERENCIAR,
    MOSTRUARIO_GERENCIAR,
    COMISSAO_GERENCIAR,
    CONSIGNACAO_GERENCIAR,
    ACERTO_CONSIGNACAO_GERENCIAR,
    ACERTO_JOIA_GERENCIAR,
    NOTA_FISCAL_GERENCIAR,
    NOTA_FISCAL_EMITIR,
    NOTA_FISCAL_CANCELAR,
    NOTA_FISCAL_CORRIGIR,
    SPC_CONSULTAR,
    SPC_REGISTRAR,
    PLANEJADOR_ROTA_GERENCIAR,
    BACKUP_GERENCIAR,
    // configuração do e-mail de envio (SMTP) na tela de Sistema: só o perfil ADMIN (guarda credenciais)
    CONFIGURACAO_EMAIL_GERENCIAR,
    MONTAGEM_GERENCIAR,
    AGENDA_GERENCIAR,
    // só existe para a tela de perfis: o backend não a verifica (o GET da agenda exige apenas login)
    AGENDA_VISUALIZAR,
    // financeiro 6.0 (contas a pagar, cadastros financeiros e movimentação de contas): concedidas só ao perfil ADMIN na V64
    CONTA_PAGAR_VISUALIZAR,
    CONTA_PAGAR_GERENCIAR,
    CONTA_PAGAR_PAGAR,
    CONTA_PAGAR_ESTORNAR,
    CADASTRO_FINANCEIRO_GERENCIAR,
    MOVIMENTACAO_VISUALIZAR,
    MOVIMENTACAO_GERENCIAR,
    // financeiro 6.1 (contas a receber): concedidas só ao perfil ADMIN na V67; CONTA_RECEBER_ESTORNAR isolada não dá acesso às telas
    CONTA_RECEBER_VISUALIZAR,
    CONTA_RECEBER_GERENCIAR,
    CONTA_RECEBER_RECEBER,
    CONTA_RECEBER_ESTORNAR
}
