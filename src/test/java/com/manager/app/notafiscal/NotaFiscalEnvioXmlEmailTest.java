package com.manager.app.notafiscal;

import com.manager.app.email.ConfiguracaoEmailService;
import com.manager.app.email.EmailConfigEfetiva;
import com.manager.app.email.EmailFalhaEnvioException;
import com.manager.app.email.EmailTemplateService;
import com.manager.app.email.EmailNaoConfiguradoException;
import com.manager.app.notafiscal.dto.EnviarXmlEmailRequestDTO;
import com.manager.app.notafiscal.dto.EnviarXmlEmailResponseDTO;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.usuario.Usuario;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotaFiscalEnvioXmlEmailTest {

    @TempDir Path pasta;

    NotaFiscalRepository repository = mock(NotaFiscalRepository.class);
    NotaFiscalXmlStorageService storage;
    NotaFiscalXmlZipService zipService;
    JavaMailSender sender = mock(JavaMailSender.class);
    UsuarioAuditorAware auditor = mock(UsuarioAuditorAware.class);
    ConfiguracaoEmailService configuracao = mock(ConfiguracaoEmailService.class);
    NotaFiscalEmailXmlService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        storage = new NotaFiscalXmlStorageService(pasta.toString());
        zipService = new NotaFiscalXmlZipService(repository, storage);
        ReflectionTestUtils.setField(zipService, "maxZipBytes", 20L * 1024 * 1024);
        when(repository.findAllById(any())).thenAnswer(i -> {
            List<Long> ids = new ArrayList<>();
            ((Iterable<Long>) i.getArgument(0)).forEach(ids::add);
            return notas.stream().filter(n -> ids.contains(n.getId())).toList();
        });
        when(configuracao.resolverParaEnvio()).thenReturn(new ConfiguracaoEmailService.EnvioEmail(sender,
                new EmailConfigEfetiva("smtp.local", 25, null, null, "nfe@empresa.com.br", "Fiscal Empresa", false, false, 1000)));
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        Usuario u = new Usuario();
        u.setUsuario("admin");
        u.setEmail("admin@empresa.com.br");
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(u));
        service = new NotaFiscalEmailXmlService(zipService, configuracao, new EmailTemplateService(modeloEmpresaRepo(), "./nao-existe/logo.png"), auditor, 200);
    }


    private static com.manager.app.empresa.EmpresaRepository modeloEmpresaRepo() {
        com.manager.app.empresa.EmpresaRepository r = mock(com.manager.app.empresa.EmpresaRepository.class);
        when(r.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
        when(r.findById(any())).thenReturn(java.util.Optional.empty());
        return r;
    }

    private NotaFiscal nota(Long id, Long numero, StatusNotaFiscal status) {
        NotaFiscal n = new NotaFiscal();
        n.setId(id);
        n.setNumero(numero);
        n.setChaveAcesso("CHAVE" + numero);
        n.setStatus(status);
        notas.add(n);
        return n;
    }

    private final List<NotaFiscal> notas = new ArrayList<>();

    private EnviarXmlEmailRequestDTO request(Long... ids) {
        EnviarXmlEmailRequestDTO r = new EnviarXmlEmailRequestDTO();
        r.setNotaIds(List.of(ids));
        r.setPara(List.of("cliente@dominio.com.br"));
        r.setAssunto("XMLs das notas");
        r.setCorpo("Linha 1\nLinha 2");
        return r;
    }

    private List<String> entradas(byte[] zip) throws Exception {
        List<String> nomes = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) nomes.add(e.getName());
        }
        return nomes;
    }

    @Test
    void zipTemPastaPorNotaComAutorizadoCancelamentoECartasAceitas() throws Exception {
        NotaFiscal n = nota(1L, 100L, StatusNotaFiscal.CANCELADA);
        storage.salvar(n, "<nfe/>".getBytes(StandardCharsets.UTF_8));
        storage.salvarCancelamento(n, "<canc/>".getBytes(StandardCharsets.UTF_8));
        NotaFiscalCorrecao aceita = new NotaFiscalCorrecao();
        aceita.setSequencia(1);
        aceita.setStatus(StatusCorrecao.ACEITA);
        NotaFiscalCorrecao rejeitada = new NotaFiscalCorrecao();
        rejeitada.setSequencia(2);
        rejeitada.setStatus(StatusCorrecao.REJEITADA);
        n.getCorrecoes().add(aceita);
        n.getCorrecoes().add(rejeitada);
        storage.salvarCorrecao(n, 1, "<cce/>".getBytes(StandardCharsets.UTF_8));

        NotaFiscalXmlZipService.XmlZip zip = zipService.montar(List.of(1L));

        assertThat(entradas(zip.conteudo())).containsExactlyInAnyOrder("NF-100/CHAVE100-nfe.xml", "NF-100/cancelamento.xml", "NF-100/cce-1.xml");
        assertThat(zip.incluidas()).containsExactly(100L);
        assertThat(zip.ignoradasSemXml()).isEmpty();
    }

    @Test
    void notaSemNenhumXmlFicaForaEEInformada() throws Exception {
        NotaFiscal com = nota(1L, 100L, StatusNotaFiscal.AUTORIZADA);
        storage.salvar(com, "<nfe/>".getBytes(StandardCharsets.UTF_8));
        nota(2L, 200L, StatusNotaFiscal.AUTORIZADA);

        NotaFiscalXmlZipService.XmlZip zip = zipService.montar(List.of(1L, 2L));

        assertThat(zip.incluidas()).containsExactly(100L);
        assertThat(zip.ignoradasSemXml()).containsExactly(200L);
        assertThat(entradas(zip.conteudo())).containsExactly("NF-100/CHAVE100-nfe.xml");
    }

    @Test
    void notaInexistenteDa404EIdsRepetidosSaoUnificados() {
        NotaFiscal n = nota(1L, 100L, StatusNotaFiscal.AUTORIZADA);
        storage.salvar(n, "<nfe/>".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> zipService.montar(List.of(1L, 99L))).isInstanceOf(ObjetoNaoEncontradoException.class);
        assertThat(zipService.montar(List.of(1L, 1L)).incluidas()).containsExactly(100L);
    }

    @Test
    void zipMaiorQueOLimiteERecusadoComOrientacao() {
        NotaFiscal n = nota(1L, 100L, StatusNotaFiscal.AUTORIZADA);
        storage.salvar(n, "<nfe/>".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(zipService, "maxZipBytes", 10L);
        assertThatThrownBy(() -> zipService.montar(List.of(1L))).isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("excede o tamanho máximo").hasMessageContaining("Divida o envio");
    }

    @Test
    void enviaComAnexoZipRemetenteFixoEReplyToDoUsuario() throws Exception {
        NotaFiscal n = nota(1L, 100L, StatusNotaFiscal.AUTORIZADA);
        storage.salvar(n, "<nfe/>".getBytes(StandardCharsets.UTF_8));
        EnviarXmlEmailRequestDTO r = request(1L);
        r.setCc(List.of("copia@dominio.com.br", "COPIA@dominio.com.br"));

        EnviarXmlEmailResponseDTO resposta = service.enviar(r);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage m = captor.getValue();
        m.saveChanges();
        assertThat(((jakarta.mail.internet.InternetAddress) m.getFrom()[0]).getAddress()).isEqualTo("nfe@empresa.com.br");
        assertThat(((jakarta.mail.internet.InternetAddress) m.getFrom()[0]).getPersonal()).isEqualTo("Fiscal Empresa");
        assertThat(m.getReplyTo()[0].toString()).isEqualTo("admin@empresa.com.br");
        assertThat(m.getSubject()).isEqualTo("XMLs das notas");
        assertThat(m.getContent()).isInstanceOf(MimeMultipart.class);
        List<String> tipos = new ArrayList<>();
        tipos(m, tipos);
        assertThat(tipos).contains("multipart/alternative", "text/plain", "text/html", "application/zip");
        String html = parteDe(m, "text/html");
        String texto = parteDe(m, "text/plain");
        assertThat(html).contains("1 nota fiscal em anexo").contains("NF 100").contains("nfe-xmls-").contains("Linha 1<br>Linha 2");
        assertThat(texto).contains("Linha 1\nLinha 2").contains("NF 100");
        assertThat(resposta.getEnviadas()).containsExactly(100L);
        assertThat(resposta.getDestinatarios()).containsExactly("cliente@dominio.com.br", "copia@dominio.com.br");
        assertThat(resposta.getTamanhoZipBytes()).isPositive();
    }

    @Test
    void corpoComHtmlEScriptDoUsuarioNaoChegaCruNoEmail() throws Exception {
        NotaFiscal n = nota(1L, 100L, StatusNotaFiscal.AUTORIZADA);
        storage.salvar(n, "<nfe/>".getBytes(StandardCharsets.UTF_8));
        EnviarXmlEmailRequestDTO r = request(1L);
        r.setCorpo("<script>alert(1)</script>Olá <b>você</b>");
        service.enviar(r);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        captor.getValue().saveChanges();
        String html = parteDe(captor.getValue(), "text/html");
        assertThat(html).doesNotContain("<script>alert").doesNotContain("<b>você</b>").contains("&lt;script&gt;alert(1)&lt;/script&gt;Olá &lt;b&gt;você&lt;/b&gt;");
    }

    @Test
    void concordanciaETamanhoLegiveis() {
        assertThat(NotaFiscalEmailXmlService.tituloResumo(1)).isEqualTo("1 nota fiscal em anexo");
        assertThat(NotaFiscalEmailXmlService.tituloResumo(3)).isEqualTo("3 notas fiscais em anexo");
        assertThat(NotaFiscalEmailXmlService.tamanho(249)).isEqualTo("249 bytes");
        assertThat(NotaFiscalEmailXmlService.tamanho(12698)).isEqualTo("12,4 KB");
        assertThat(NotaFiscalEmailXmlService.tamanho(3355443)).isEqualTo("3,2 MB");
    }

    private void tipos(jakarta.mail.Part parte, List<String> tipos) throws Exception {
        tipos.add(parte.getContentType().toLowerCase().split(";")[0].trim());
        if (parte.getContent() instanceof jakarta.mail.Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) tipos(mp.getBodyPart(i), tipos);
        }
    }

    private String parteDe(jakarta.mail.Part parte, String tipo) throws Exception {
        if (parte.getContentType().toLowerCase().startsWith(tipo) && parte.getContent() instanceof String s) return s;
        if (parte.getContent() instanceof jakarta.mail.Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                String achado = parteDe(mp.getBodyPart(i), tipo);
                if (achado != null) return achado;
            }
        }
        return null;
    }

    @Test
    void semConfiguracaoDoServidorResponde503Claro() {
        when(configuracao.resolverParaEnvio()).thenThrow(new EmailNaoConfiguradoException());
        assertThatThrownBy(() -> service.enviar(request(1L))).isInstanceOf(EmailNaoConfiguradoException.class)
                .hasMessageContaining("Envio de e-mail não configurado").hasMessageContaining("em Sistema");
    }

    @Test
    void validaDestinatariosAssuntoELimites() {
        EnviarXmlEmailRequestDTO r = request(1L);
        r.setPara(List.of("invalido"));
        assertThatThrownBy(() -> service.enviar(r)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("inválido");

        EnviarXmlEmailRequestDTO muitos = request(1L);
        List<String> onze = new ArrayList<>();
        for (int i = 0; i < 11; i++) onze.add("a" + i + "@dominio.com.br");
        muitos.setPara(onze);
        assertThatThrownBy(() -> service.enviar(muitos)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("no máximo 10");

        EnviarXmlEmailRequestDTO cc = request(1L);
        cc.setCc(onze);
        assertThatThrownBy(() -> service.enviar(cc)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("em Cc");

        EnviarXmlEmailRequestDTO assunto = request(1L);
        assunto.setAssunto("a\nBcc: x@y.com");
        assertThatThrownBy(() -> service.enviar(assunto)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("quebras de linha");

        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= 201; i++) ids.add(i);
        EnviarXmlEmailRequestDTO notasDemais = request(1L);
        notasDemais.setNotaIds(ids);
        assertThatThrownBy(() -> service.enviar(notasDemais)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("no máximo 200");
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void nenhumaNotaComXmlRecusa() {
        nota(1L, 100L, StatusNotaFiscal.AUTORIZADA);
        assertThatThrownBy(() -> service.enviar(request(1L))).isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("Nenhuma das notas fiscais selecionadas possui XML");
    }

    @Test
    void falhaDoSmtpViraErroSemDetalhesTecnicos() {
        NotaFiscal n = nota(1L, 100L, StatusNotaFiscal.AUTORIZADA);
        storage.salvar(n, "<nfe/>".getBytes(StandardCharsets.UTF_8));
        org.mockito.Mockito.doThrow(new MailSendException("535 senha incorreta smtp.exemplo.com")).when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> service.enviar(request(1L))).isInstanceOf(EmailFalhaEnvioException.class)
                .hasMessageNotContaining("535").hasMessageNotContaining("smtp.exemplo.com");
    }
}
