package com.manager.app.notafiscal;

import com.manager.app.email.ConfiguracaoEmailService;
import com.manager.app.email.EmailErros;
import com.manager.app.email.EmailFalhaEnvioException;
import com.manager.app.email.EmailTemplateService;
import com.manager.app.notafiscal.dto.EnviarXmlEmailRequestDTO;
import com.manager.app.notafiscal.dto.EnviarXmlEmailResponseDTO;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.usuario.Usuario;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

// envia por e-mail (conta SMTP fixa da empresa) um zip com os XMLs das notas selecionadas. Sincrono.
@Slf4j
@Service
public class NotaFiscalEmailXmlService {

    static final int MAX_DESTINATARIOS = 10;
    private static final DateTimeFormatter FORMATO_NOME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    // formato simples: sem espacos, sem quebra de linha (protege o cabecalho), um @ e um dominio com ponto
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+([.][A-Za-z0-9-]+)*[.][A-Za-z]{2,}$");

    private final NotaFiscalXmlZipService zipService;
    private final ConfiguracaoEmailService configuracaoEmail;
    private final EmailTemplateService templateService;
    private final UsuarioAuditorAware usuarioAuditorAware;
    private final int maxNotas;

    public NotaFiscalEmailXmlService(NotaFiscalXmlZipService zipService,
                                     ConfiguracaoEmailService configuracaoEmail,
                                     EmailTemplateService templateService,
                                     UsuarioAuditorAware usuarioAuditorAware,
                                     @Value("${app.mail.max-notas:200}") int maxNotas) {
        this.zipService = zipService;
        this.configuracaoEmail = configuracaoEmail;
        this.templateService = templateService;
        this.usuarioAuditorAware = usuarioAuditorAware;
        this.maxNotas = maxNotas;
    }

    public EnviarXmlEmailResponseDTO enviar(EnviarXmlEmailRequestDTO request){
        // configuracao salva no banco (ou, sem ela, as variaveis MAIL_*); sem nenhuma, 503
        ConfiguracaoEmailService.EnvioEmail envio = configuracaoEmail.resolverParaEnvio();
        JavaMailSender sender = envio.sender();

        List<Long> notaIds = new ArrayList<>(new LinkedHashSet<>(request.getNotaIds()));
        if (notaIds.size() > maxNotas) {
            throw new ItemInvalidoException("Selecione no máximo " + maxNotas + " notas fiscais por envio.");
        }
        List<String> para = validarEnderecos(request.getPara(), "Para");
        List<String> cc = validarEnderecos(request.getCc(), "Cc");
        if (para.isEmpty()) {
            throw new ItemInvalidoException("Informe ao menos um destinatário.");
        }
        String assunto = request.getAssunto().trim();
        if (assunto.contains("\r") || assunto.contains("\n")) {
            throw new ItemInvalidoException("O assunto não pode conter quebras de linha.");
        }

        NotaFiscalXmlZipService.XmlZip xmlZip = zipService.montar(notaIds);
        if (xmlZip.incluidas().isEmpty()) {
            throw new ItemInvalidoException("Nenhuma das notas fiscais selecionadas possui XML disponível para envio.");
        }

        String nomeZip = "nfe-xmls-" + LocalDateTime.now().format(FORMATO_NOME) + ".zip";
        Usuario usuario = usuarioAuditorAware.getCurrentAuditor().orElse(null);
        EmailTemplateService.Conteudo conteudo = templateService.montar(modeloEmail(request, assunto, xmlZip, nomeZip));
        try {
            MimeMessage mensagem = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            ConfiguracaoEmailService.definirRemetente(helper, envio.config());
            helper.setTo(para.toArray(new String[0]));
            if (!cc.isEmpty()) {
                helper.setCc(cc.toArray(new String[0]));
            }
            if (usuario != null && usuario.getEmail() != null && EMAIL.matcher(usuario.getEmail().trim()).matches()) {
                helper.setReplyTo(usuario.getEmail().trim());
            }
            helper.setSubject(assunto);
            // HTML padronizado + texto simples (multipart/alternative); a mensagem do usuario e escapada no modelo
            templateService.aplicar(helper, conteudo);
            helper.addAttachment(nomeZip, new ByteArrayResource(xmlZip.conteudo()), "application/zip");
            sender.send(mensagem);
        } catch (MessagingException | MailException | java.io.UnsupportedEncodingException e) {
            // so a classe e a mensagem tecnica no log (o JavaMail nao inclui a senha); a resposta ao usuario nao detalha o servidor
            log.error("Falha ao enviar e-mail com XMLs de notas fiscais: usuario={}, notas={}, erro={}: {}",
                    nomeUsuario(usuario), xmlZip.incluidas(), e.getClass().getSimpleName(), e.getMessage());
            throw new EmailFalhaEnvioException("Não foi possível enviar o e-mail: " + EmailErros.descrever(e) + " Verifique a configuração do e-mail em Sistema ou tente novamente mais tarde.", e);
        }

        List<String> destinatarios = new ArrayList<>(para);
        destinatarios.addAll(cc);
        log.info("E-mail com XMLs de notas fiscais enviado: usuario={}, para={}, cc={}, notas={}, ignoradasSemXml={}, zipBytes={}",
                nomeUsuario(usuario), para, cc, xmlZip.incluidas(), xmlZip.ignoradasSemXml(), xmlZip.conteudo().length);
        return new EnviarXmlEmailResponseDTO(xmlZip.incluidas(), xmlZip.ignoradasSemXml(), xmlZip.conteudo().length, destinatarios);
    }

    // quadro de resumo: N nota(s) fiscal(is) em anexo, numeros, nome e tamanho do zip (e as que ficaram de fora por falta de XML)
    private EmailTemplateService.Modelo modeloEmail(EnviarXmlEmailRequestDTO request, String assunto, NotaFiscalXmlZipService.XmlZip zip, String nomeZip){
        List<EmailTemplateService.Linha> linhas = new ArrayList<>();
        linhas.add(new EmailTemplateService.Linha(zip.incluidas().size() == 1 ? "Nota fiscal" : "Notas fiscais", numeros(zip.incluidas())));
        linhas.add(new EmailTemplateService.Linha("Arquivo", nomeZip + " (" + tamanho(zip.conteudo().length) + ")"));
        if (!zip.ignoradasSemXml().isEmpty()) {
            linhas.add(new EmailTemplateService.Linha("Sem XML disponível", numeros(zip.ignoradasSemXml()) + " (não incluídas)"));
        }
        return new EmailTemplateService.Modelo(assunto, request.getCorpo(), tituloResumo(zip.incluidas().size()),
                linhas, templateService.empresaPorId(zip.empresaId()));
    }

    static String tituloResumo(int n){
        return n == 1 ? "1 nota fiscal em anexo" : n + " notas fiscais em anexo";
    }

    private static String numeros(List<Long> numeros){
        return numeros.stream().map(n -> "NF " + n).collect(java.util.stream.Collectors.joining(", "));
    }

    static String tamanho(long bytes){
        if (bytes < 1024) return bytes + " bytes";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String nomeUsuario(Usuario usuario){
        return usuario != null ? usuario.getUsuario() : "desconhecido";
    }

    // sem repetidos (ignora maiusculas), formato valido e no maximo MAX_DESTINATARIOS
    private List<String> validarEnderecos(List<String> enderecos, String campo){
        List<String> validos = new ArrayList<>();
        if (enderecos == null) return validos;
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (String bruto : enderecos) {
            String endereco = bruto == null ? "" : bruto.trim();
            if (!EMAIL.matcher(endereco).matches()) {
                throw new ItemInvalidoException("O e-mail informado em " + campo + " é inválido: " + endereco);
            }
            if (vistos.add(endereco.toLowerCase())) {
                validos.add(endereco);
            }
        }
        if (validos.size() > MAX_DESTINATARIOS) {
            throw new ItemInvalidoException("Informe no máximo " + MAX_DESTINATARIOS + " e-mails em " + campo + ".");
        }
        return validos;
    }
}
