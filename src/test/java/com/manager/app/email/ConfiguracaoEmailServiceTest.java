package com.manager.app.email;

import com.manager.app.email.dto.ConfiguracaoEmailRequestDTO;
import com.manager.app.email.dto.ConfiguracaoEmailResponseDTO;
import com.manager.app.email.dto.ConfiguracaoEmailTesteRequestDTO;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.usuario.Usuario;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfiguracaoEmailServiceTest {

    ConfiguracaoEmailRepository repository = mock(ConfiguracaoEmailRepository.class);
    SegredoCriptografiaService criptografia = new SegredoCriptografiaService("segredo-de-teste-com-mais-de-32-bytes-ok", "jwt");
    UsuarioAuditorAware auditor = mock(UsuarioAuditorAware.class);
    UsuarioResumoMapper resumoMapper = mock(UsuarioResumoMapper.class);
    ConfiguracaoEmail banco;

    @BeforeEach
    void setUp() {
        banco = null;
        when(repository.findById(1L)).thenAnswer(i -> Optional.ofNullable(banco));
        when(repository.saveAndFlush(any())).thenAnswer(i -> {
            banco = i.getArgument(0);
            return banco;
        });
        Usuario u = new Usuario();
        u.setUsuario("admin");
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(u));
    }

    private ConfiguracaoEmailService service(String hostAmbiente) {
        return new ConfiguracaoEmailService(repository, criptografia, auditor, resumoMapper,
                hostAmbiente, 587, "user-env", "senha-env", "env@empresa.com.br", true, false, 30000);
    }

    private ConfiguracaoEmailRequestDTO request() {
        ConfiguracaoEmailRequestDTO r = new ConfiguracaoEmailRequestDTO();
        r.setHost("smtp.exemplo.com");
        r.setPorta(587);
        r.setUsuario("conta@exemplo.com");
        r.setSenha("segredo123");
        r.setRemetente("conta@exemplo.com");
        r.setStarttls(true);
        r.setSsl(false);
        r.setAtivo(true);
        return r;
    }

    @Test
    void criptografiaIdaEVoltaComIvDiferenteACadaVezEChaveErradaFalha() {
        String a = criptografia.criptografar("minha-senha");
        String b = criptografia.criptografar("minha-senha");
        assertThat(a).startsWith("v1:").isNotEqualTo(b).doesNotContain("minha-senha");
        assertThat(criptografia.descriptografar(a)).isEqualTo("minha-senha");
        SegredoCriptografiaService outra = new SegredoCriptografiaService("outro-segredo-completamente-diferente-123", "");
        assertThatThrownBy(() -> outra.descriptografar(a)).isInstanceOf(SegredoCriptografiaService.SegredoIlegivelException.class)
                .hasMessageContaining("Informe a senha novamente");
        assertThatThrownBy(() -> criptografia.descriptografar("lixo")).isInstanceOf(SegredoCriptografiaService.SegredoIlegivelException.class);
    }

    @Test
    void semSegredoDedicadoUsaOSegredoDoJwt() {
        SegredoCriptografiaService viaJwt = new SegredoCriptografiaService("", "jwt-secret-com-mais-de-32-bytes-0123456789");
        assertThat(viaJwt.descriptografar(viaJwt.criptografar("x"))).isEqualTo("x");
        assertThatThrownBy(() -> new SegredoCriptografiaService("", "")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void salvaComSenhaCriptografadaENuncaDevolveASenha() {
        ConfiguracaoEmailResponseDTO dto = service("").salvar(request());
        assertThat(banco.getSenhaCriptografada()).startsWith("v1:").doesNotContain("segredo123");
        assertThat(dto.isSenhaConfigurada()).isTrue();
        assertThat(dto.toString()).doesNotContain("segredo123");
        assertThat(dto.getHost()).isEqualTo("smtp.exemplo.com");
    }

    @Test
    void senhaVaziaOuOmitidaMantemAAnterior() {
        ConfiguracaoEmailService s = service("");
        s.salvar(request());
        String original = banco.getSenhaCriptografada();
        ConfiguracaoEmailRequestDTO r = request();
        r.setSenha("");
        r.setHost("outro.exemplo.com");
        s.salvar(r);
        assertThat(banco.getSenhaCriptografada()).isEqualTo(original);
        assertThat(banco.getHost()).isEqualTo("outro.exemplo.com");
        r.setSenha(null);
        s.salvar(r);
        assertThat(banco.getSenhaCriptografada()).isEqualTo(original);
        r.setSenha("nova-senha");
        s.salvar(r);
        assertThat(banco.getSenhaCriptografada()).isNotEqualTo(original);
        assertThat(criptografia.descriptografar(banco.getSenhaCriptografada())).isEqualTo("nova-senha");
    }

    @Test
    void senhaPreenchidaSemUsuarioDa400ComMensagemFormalNoSalvarENoTestar() {
        ConfiguracaoEmailService s = service("");
        ConfiguracaoEmailRequestDTO r = request();
        r.setUsuario("");
        assertThatThrownBy(() -> s.salvar(r)).isInstanceOf(ItemInvalidoException.class).hasMessage("Informe o usuário para armazenar a senha.");
        r.setUsuario(null);
        assertThatThrownBy(() -> s.salvar(r)).isInstanceOf(ItemInvalidoException.class).hasMessage("Informe o usuário para armazenar a senha.");
        assertThat(banco).isNull();

        ConfiguracaoEmailTesteRequestDTO t = new ConfiguracaoEmailTesteRequestDTO();
        t.setPara("ok@exemplo.com");
        t.setConfiguracao(r);
        assertThatThrownBy(() -> s.testar(t)).isInstanceOf(ItemInvalidoException.class).hasMessage("Informe o usuário para armazenar a senha.");
    }

    @Test
    void semUsuarioNaoGuardaSenha() {
        ConfiguracaoEmailService s = service("");
        s.salvar(request());
        ConfiguracaoEmailRequestDTO r = request();
        r.setUsuario("");
        r.setSenha("");
        assertThat(s.salvar(r).isSenhaConfigurada()).isFalse();
    }

    @Test
    void validacoes() {
        ConfiguracaoEmailService s = service("");
        ConfiguracaoEmailRequestDTO semHost = request();
        semHost.setHost(" ");
        assertThatThrownBy(() -> s.salvar(semHost)).isInstanceOf(ItemInvalidoException.class).hasMessage("O campo Servidor SMTP é obrigatório.");

        ConfiguracaoEmailRequestDTO portaRuim = request();
        portaRuim.setPorta(70000);
        assertThatThrownBy(() -> s.salvar(portaRuim)).hasMessage("A porta deve estar entre 1 e 65535.");
        portaRuim.setPorta(0);
        assertThatThrownBy(() -> s.salvar(portaRuim)).hasMessage("A porta deve estar entre 1 e 65535.");

        ConfiguracaoEmailRequestDTO semPorta = request();
        semPorta.setPorta(null);
        assertThatThrownBy(() -> s.salvar(semPorta)).hasMessage("O campo Porta é obrigatório.");

        ConfiguracaoEmailRequestDTO remetenteRuim = request();
        remetenteRuim.setRemetente("sem-arroba");
        assertThatThrownBy(() -> s.salvar(remetenteRuim)).hasMessageContaining("remetente é inválido");

        ConfiguracaoEmailRequestDTO ambos = request();
        ambos.setSsl(true);
        assertThatThrownBy(() -> s.salvar(ambos)).hasMessageContaining("STARTTLS e SSL não podem");

        // inativa: pode salvar incompleta (rascunho), mas o que vier tem de ser valido
        ConfiguracaoEmailRequestDTO rascunho = new ConfiguracaoEmailRequestDTO();
        rascunho.setAtivo(false);
        s.salvar(rascunho);
        assertThat(banco.isAtivo()).isFalse();
    }

    @Test
    void getSemConfiguracaoDevolvePadroesVaziosEIndicaFallbackDeAmbiente() {
        ConfiguracaoEmailResponseDTO sem = service("").obter();
        assertThat(sem.getHost()).isNull();
        assertThat(sem.getPorta()).isEqualTo(587);
        assertThat(sem.isSenhaConfigurada()).isFalse();
        assertThat(sem.isAtivo()).isFalse();
        assertThat(sem.isUsandoVariaveisAmbiente()).isFalse();
        assertThat(service("smtp.env.com").obter().isUsandoVariaveisAmbiente()).isTrue();
    }

    @Test
    void resolucaoBancoAtivoDepoisFallbackDeAmbienteDepois503() {
        // nada no banco, sem ambiente: 503 com a mensagem para apontar a tela de Sistema
        assertThatThrownBy(() -> service("").resolverParaEnvio()).isInstanceOf(EmailNaoConfiguradoException.class)
                .hasMessage("Envio de e-mail não configurado. Solicite ao administrador que configure o e-mail em Sistema.");
        // nada no banco, com ambiente: fallback
        assertThat(service("smtp.env.com").resolverParaEnvio().config().host()).isEqualTo("smtp.env.com");
        // banco ativo: vale o banco, com a senha descriptografada
        ConfiguracaoEmailService s = service("smtp.env.com");
        s.salvar(request());
        ConfiguracaoEmailService.EnvioEmail envio = s.resolverParaEnvio();
        assertThat(envio.config().host()).isEqualTo("smtp.exemplo.com");
        assertThat(envio.config().senha()).isEqualTo("segredo123");
        assertThat(envio.config().toString()).doesNotContain("segredo123");
        // banco inativo: envio desativado de proposito, sem cair no ambiente
        ConfiguracaoEmailRequestDTO inativa = request();
        inativa.setAtivo(false);
        s.salvar(inativa);
        assertThatThrownBy(s::resolverParaEnvio).isInstanceOf(EmailNaoConfiguradoException.class);
    }

    @Test
    void chaveTrocadaPedeParaInformarASenhaDeNovo() {
        ConfiguracaoEmailService s = service("");
        s.salvar(request());
        SegredoCriptografiaService outra = new SegredoCriptografiaService("outro-segredo-completamente-diferente-123", "");
        ConfiguracaoEmailService comOutraChave = new ConfiguracaoEmailService(repository, outra, auditor, resumoMapper,
                "", 587, "", "", "", true, false, 30000);
        assertThatThrownBy(comOutraChave::resolverParaEnvio).isInstanceOf(EmailNaoConfiguradoException.class)
                .hasMessageContaining("Informe a senha novamente");
    }

    @Test
    void testeValidaDestinoEExigeConfiguracao() {
        ConfiguracaoEmailService s = service("");
        ConfiguracaoEmailTesteRequestDTO t = new ConfiguracaoEmailTesteRequestDTO();
        t.setPara("invalido");
        assertThatThrownBy(() -> s.testar(t)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("inválido");
        t.setPara("ok@exemplo.com");
        assertThatThrownBy(() -> s.testar(t)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("Não há configuração de e-mail salva");
    }

    @Test
    void falhaDeConexaoNoTesteVemEmPortuguesSemCredenciais() {
        ConfiguracaoEmailService s = service("");
        ConfiguracaoEmailTesteRequestDTO t = new ConfiguracaoEmailTesteRequestDTO();
        t.setPara("ok@exemplo.com");
        ConfiguracaoEmailRequestDTO c = request();
        c.setHost("127.0.0.1");
        c.setPorta(1); // porta fechada
        c.setTimeoutMs(1500);
        t.setConfiguracao(c);
        assertThatThrownBy(() -> s.testar(t)).isInstanceOf(EmailFalhaEnvioException.class)
                .hasMessageStartingWith("Não foi possível enviar o e-mail de teste: ")
                .hasMessageNotContaining("segredo123");
    }
}
