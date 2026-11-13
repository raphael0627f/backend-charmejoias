package com.manager.app.email;

import com.manager.app.email.dto.ConfiguracaoEmailRequestDTO;
import com.manager.app.email.dto.ConfiguracaoEmailResponseDTO;
import com.manager.app.email.dto.ConfiguracaoEmailTesteRequestDTO;
import com.manager.app.email.dto.ConfiguracaoEmailTesteResponseDTO;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.usuario.Usuario;
import com.manager.app.usuario.UsuarioResumoMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.regex.Pattern;

// configuracao do SMTP de envio feita pelo usuario (banco, senha criptografada) + resolucao do que usar em cada envio:
// 1) linha salva no banco: ativa = usa; inativa = envio desativado (decisao explicita do administrador);
// 2) sem linha no banco: fallback para as variaveis de ambiente MAIL_* (compatibilidade); 3) nada = 503.
@Slf4j
@Service
public class ConfiguracaoEmailService {

    public static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+([.][A-Za-z0-9-]+)*[.][A-Za-z]{2,}$");
    private static final int TIMEOUT_PADRAO_MS = 30000;
    private static final int TIMEOUT_TESTE_MAX_MS = 10000;

    public record EnvioEmail(JavaMailSender sender, EmailConfigEfetiva config) {}

    private final ConfiguracaoEmailRepository repository;
    private final SegredoCriptografiaService criptografia;
    private final UsuarioAuditorAware usuarioAuditorAware;
    private final UsuarioResumoMapper usuarioResumoMapper;
    private final EmailTemplateService templateService;
    private final EmailConfigEfetiva ambiente;

    public ConfiguracaoEmailService(ConfiguracaoEmailRepository repository,
                                    SegredoCriptografiaService criptografia,
                                    UsuarioAuditorAware usuarioAuditorAware,
                                    UsuarioResumoMapper usuarioResumoMapper,
                                    EmailTemplateService templateService,
                                    @Value("${app.mail.host:}") String host,
                                    @Value("${app.mail.port:587}") int porta,
                                    @Value("${app.mail.username:}") String usuario,
                                    @Value("${app.mail.password:}") String senha,
                                    @Value("${app.mail.from:}") String remetente,
                                    @Value("${app.mail.starttls:true}") boolean starttls,
                                    @Value("${app.mail.ssl:false}") boolean ssl,
                                    @Value("${app.mail.timeout-ms:30000}") int timeoutMs) {
        this.repository = repository;
        this.criptografia = criptografia;
        this.usuarioAuditorAware = usuarioAuditorAware;
        this.usuarioResumoMapper = usuarioResumoMapper;
        this.templateService = templateService;
        String from = remetente != null && !remetente.isBlank() ? remetente.trim() : usuario;
        this.ambiente = host != null && !host.isBlank() && from != null && !from.isBlank()
                ? new EmailConfigEfetiva(host.trim(), porta, usuario, senha, from.trim(), null, starttls, ssl, timeoutMs)
                : null;
    }

    @Transactional(readOnly = true)
    public ConfiguracaoEmailResponseDTO obter(){
        ConfiguracaoEmail c = repository.findById(ConfiguracaoEmail.ID_UNICO).orElse(null);
        ConfiguracaoEmailResponseDTO dto = new ConfiguracaoEmailResponseDTO();
        if (c == null) {
            // padroes vazios: nada salvo ainda
            dto.setPorta(587);
            dto.setStarttls(true);
            dto.setSsl(false);
            dto.setTimeoutMs(TIMEOUT_PADRAO_MS);
            dto.setAtivo(false);
            dto.setUsandoVariaveisAmbiente(ambiente != null);
            return dto;
        }
        dto.setHost(c.getHost());
        dto.setPorta(c.getPorta());
        dto.setUsuario(c.getUsuario());
        dto.setSenhaConfigurada(c.getSenhaCriptografada() != null && !c.getSenhaCriptografada().isBlank());
        dto.setRemetente(c.getRemetente());
        dto.setNomeRemetente(c.getNomeRemetente());
        dto.setStarttls(c.isStarttls());
        dto.setSsl(c.isSsl());
        dto.setTimeoutMs(c.getTimeoutMs());
        dto.setAtivo(c.isAtivo());
        dto.setUsandoVariaveisAmbiente(false);
        dto.setAtualizadoEm(c.getAtualizadoEm());
        dto.setAtualizadoPor(usuarioResumoMapper.toResumoDTO(c.getAtualizadoPor()));
        return dto;
    }

    @Transactional
    public ConfiguracaoEmailResponseDTO salvar(ConfiguracaoEmailRequestDTO request){
        boolean ativo = Boolean.TRUE.equals(request.getAtivo());
        validar(request, ativo);

        ConfiguracaoEmail c = repository.findById(ConfiguracaoEmail.ID_UNICO).orElseGet(ConfiguracaoEmail::new);
        c.setId(ConfiguracaoEmail.ID_UNICO);
        c.setHost(textoOuNulo(request.getHost()));
        c.setPorta(request.getPorta());
        c.setUsuario(textoOuNulo(request.getUsuario()));
        c.setRemetente(textoOuNulo(request.getRemetente()));
        c.setNomeRemetente(textoOuNulo(request.getNomeRemetente()));
        c.setStarttls(request.getStarttls() == null || request.getStarttls());
        c.setSsl(Boolean.TRUE.equals(request.getSsl()));
        c.setTimeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : TIMEOUT_PADRAO_MS);
        c.setAtivo(ativo);
        if (c.getUsuario() == null) {
            // sem usuario = SMTP sem autenticacao: nao ha o que guardar
            c.setSenhaCriptografada(null);
        } else if (request.getSenha() != null && !request.getSenha().isBlank()) {
            c.setSenhaCriptografada(criptografia.criptografar(request.getSenha()));
        } // senha vazia/omitida: mantem a anterior

        ConfiguracaoEmail salva = repository.saveAndFlush(c);
        Usuario quem = usuarioAuditorAware.getCurrentAuditor().orElse(null);
        // sem a senha: so o que identifica a alteracao
        log.info("Configuração de e-mail atualizada: usuario={}, host={}, porta={}, ativo={}", quem != null ? quem.getUsuario() : "desconhecido",
                salva.getHost(), salva.getPorta(), salva.isAtivo());
        return obter();
    }

    // configuracao a usar num envio (o chamador monta a mensagem com o sender e a config devolvidos)
    @Transactional(readOnly = true)
    public EnvioEmail resolverParaEnvio(){
        ConfiguracaoEmail c = repository.findById(ConfiguracaoEmail.ID_UNICO).orElse(null);
        if (c != null) {
            if (!c.isAtivo()) {
                throw new EmailNaoConfiguradoException();
            }
            EmailConfigEfetiva config = daLinha(c, null);
            return new EnvioEmail(config.criarSender(), config);
        }
        if (ambiente != null) {
            return new EnvioEmail(ambiente.criarSender(), ambiente);
        }
        throw new EmailNaoConfiguradoException();
    }

    // e-mail de teste: usa os dados do corpo (senha vazia = a salva) ou, sem corpo, a configuracao salva (mesmo inativa)
    @Transactional(readOnly = true)
    public ConfiguracaoEmailTesteResponseDTO testar(ConfiguracaoEmailTesteRequestDTO request){
        String para = request.getPara().trim();
        if (!EMAIL.matcher(para).matches()) {
            throw new ItemInvalidoException("O e-mail informado para o teste é inválido: " + para);
        }
        ConfiguracaoEmail salva = repository.findById(ConfiguracaoEmail.ID_UNICO).orElse(null);

        EmailConfigEfetiva config;
        ConfiguracaoEmailRequestDTO corpo = request.getConfiguracao();
        if (corpo != null && corpo.getHost() != null && !corpo.getHost().isBlank()) {
            validar(corpo, true);
            String senha = corpo.getSenha();
            if ((senha == null || senha.isBlank()) && salva != null && corpo.getUsuario() != null && !corpo.getUsuario().isBlank()) {
                senha = lerSenha(salva);
            }
            config = new EmailConfigEfetiva(corpo.getHost().trim(), corpo.getPorta(), textoOuNulo(corpo.getUsuario()), senha,
                    corpo.getRemetente().trim(), textoOuNulo(corpo.getNomeRemetente()),
                    corpo.getStarttls() == null || corpo.getStarttls(), Boolean.TRUE.equals(corpo.getSsl()),
                    corpo.getTimeoutMs() != null ? corpo.getTimeoutMs() : TIMEOUT_PADRAO_MS);
        } else if (salva != null) {
            if (salva.getHost() == null || salva.getHost().isBlank() || salva.getPorta() == null || salva.getRemetente() == null) {
                throw new ItemInvalidoException("Não há configuração de e-mail salva. Preencha os dados do servidor antes de testar.");
            }
            config = daLinha(salva, null);
        } else if (ambiente != null) {
            config = ambiente;
        } else {
            throw new ItemInvalidoException("Não há configuração de e-mail salva. Preencha os dados do servidor antes de testar.");
        }

        // timeout curto: o teste nao deve prender a tela
        int timeout = Math.min(config.timeoutMs(), TIMEOUT_TESTE_MAX_MS);
        EmailConfigEfetiva paraTeste = new EmailConfigEfetiva(config.host(), config.porta(), config.usuario(), config.senha(),
                config.remetente(), config.nomeRemetente(), config.starttls(), config.ssl(), timeout);
        JavaMailSender sender = paraTeste.criarSender();
        try {
            MimeMessage mensagem = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            definirRemetente(helper, paraTeste);
            helper.setTo(para);
            helper.setSubject("Teste de configuração de e-mail");
            templateService.aplicar(helper, templateService.montar(modeloTeste(paraTeste)));
            sender.send(mensagem);
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            log.warn("Falha no e-mail de teste: host={}, porta={}, erro={}", paraTeste.host(), paraTeste.porta(), e.getClass().getSimpleName());
            throw new EmailFalhaEnvioException("Não foi possível enviar o e-mail de teste: " + EmailErros.descrever(e), e);
        }
        return new ConfiguracaoEmailTesteResponseDTO(true, "E-mail de teste enviado para " + para + ".");
    }

    // e-mail de teste: explica que e uma mensagem de teste e mostra servidor/porta/seguranca (nunca a senha)
    private EmailTemplateService.Modelo modeloTeste(EmailConfigEfetiva c){
        String seguranca = c.ssl() ? "SSL" : (c.starttls() ? "STARTTLS" : "Sem criptografia");
        String remetente = c.nomeRemetente() != null && !c.nomeRemetente().isBlank() ? c.nomeRemetente() + " <" + c.remetente() + ">" : c.remetente();
        List<EmailTemplateService.Linha> linhas = List.of(
                new EmailTemplateService.Linha("Servidor", c.host() + ":" + c.porta()),
                new EmailTemplateService.Linha("Segurança", seguranca),
                new EmailTemplateService.Linha("Autenticação", c.usuario() != null && !c.usuario().isBlank() ? "Com usuário e senha" : "Sem autenticação"),
                new EmailTemplateService.Linha("Remetente", remetente));
        return new EmailTemplateService.Modelo("Teste de configuração de e-mail",
                "Esta é uma mensagem de teste da configuração de e-mail do sistema. Se você a recebeu, o servidor informado está funcionando corretamente para o envio de mensagens.\n\nNenhuma ação é necessária.",
                "Configuração utilizada", linhas, templateService.empresaPadrao());
    }

    public static void definirRemetente(MimeMessageHelper helper, EmailConfigEfetiva config) throws MessagingException, UnsupportedEncodingException {
        if (config.nomeRemetente() != null && !config.nomeRemetente().isBlank()) {
            helper.setFrom(config.remetente(), config.nomeRemetente());
        } else {
            helper.setFrom(config.remetente());
        }
    }

    private EmailConfigEfetiva daLinha(ConfiguracaoEmail c, String senhaInformada){
        String senha = senhaInformada;
        if (senha == null && c.getUsuario() != null && !c.getUsuario().isBlank()) {
            senha = lerSenha(c);
        }
        return new EmailConfigEfetiva(c.getHost(), c.getPorta(), c.getUsuario(), senha, c.getRemetente(), c.getNomeRemetente(),
                c.isStarttls(), c.isSsl(), c.getTimeoutMs() != null ? c.getTimeoutMs() : TIMEOUT_PADRAO_MS);
    }

    // senha salva; se nao der para ler (chave trocada), pede para informar de novo (503 no envio / 400 no teste)
    private String lerSenha(ConfiguracaoEmail c){
        if (c.getSenhaCriptografada() == null || c.getSenhaCriptografada().isBlank()) return null;
        try {
            return criptografia.descriptografar(c.getSenhaCriptografada());
        } catch (SegredoCriptografiaService.SegredoIlegivelException e) {
            throw new EmailNaoConfiguradoException(e.getMessage());
        }
    }

    private void validar(ConfiguracaoEmailRequestDTO r, boolean obrigatorios){
        boolean starttls = r.getStarttls() == null || r.getStarttls();
        if (starttls && Boolean.TRUE.equals(r.getSsl())) {
            throw new ItemInvalidoException("As opções STARTTLS e SSL não podem ser utilizadas ao mesmo tempo. Escolha apenas uma.");
        }
        // senha sem usuario nao tem como ser usada (sem usuario o SMTP e sem autenticacao): recusa em vez de descartar em silencio
        if (r.getSenha() != null && !r.getSenha().isBlank() && (r.getUsuario() == null || r.getUsuario().isBlank())) {
            throw new ItemInvalidoException("Informe o usuário para armazenar a senha.");
        }
        if (obrigatorios) {
            if (r.getHost() == null || r.getHost().isBlank()) {
                throw new ItemInvalidoException("O campo Servidor SMTP é obrigatório.");
            }
            if (r.getPorta() == null) {
                throw new ItemInvalidoException("O campo Porta é obrigatório.");
            }
            if (r.getRemetente() == null || r.getRemetente().isBlank()) {
                throw new ItemInvalidoException("O campo E-mail do remetente é obrigatório.");
            }
        }
        if (r.getPorta() != null && (r.getPorta() < 1 || r.getPorta() > 65535)) {
            throw new ItemInvalidoException("A porta deve estar entre 1 e 65535.");
        }
        if (r.getRemetente() != null && !r.getRemetente().isBlank() && !EMAIL.matcher(r.getRemetente().trim()).matches()) {
            throw new ItemInvalidoException("O e-mail do remetente é inválido: " + r.getRemetente().trim());
        }
        if (r.getTimeoutMs() != null && (r.getTimeoutMs() < 1000 || r.getTimeoutMs() > 120000)) {
            throw new ItemInvalidoException("O tempo limite deve estar entre 1000 e 120000 milissegundos.");
        }
        limitar(r.getHost(), "Servidor SMTP", 255);
        limitar(r.getUsuario(), "Usuário", 255);
        limitar(r.getSenha(), "Senha", 255);
        limitar(r.getNomeRemetente(), "Nome do remetente", 100);
        if (r.getNomeRemetente() != null && (r.getNomeRemetente().contains("\r") || r.getNomeRemetente().contains("\n"))) {
            throw new ItemInvalidoException("O nome do remetente não pode conter quebras de linha.");
        }
        if (r.getHost() != null && (r.getHost().contains(" ") || r.getHost().contains("\n") || r.getHost().contains("\r"))) {
            throw new ItemInvalidoException("O servidor SMTP informado é inválido.");
        }
    }

    private void limitar(String valor, String campo, int max){
        if (valor != null && valor.length() > max) {
            throw new ItemInvalidoException("O campo " + campo + " deve ter no máximo " + max + " caracteres.");
        }
    }

    private String textoOuNulo(String valor){
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
