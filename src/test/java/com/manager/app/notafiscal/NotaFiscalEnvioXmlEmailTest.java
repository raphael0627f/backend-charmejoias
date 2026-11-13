package com.manager.app.notafiscal;

import com.manager.app.email.EmailFalhaEnvioException;
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
import org.springframework.beans.factory.ObjectProvider;
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
    @SuppressWarnings("unchecked")
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
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
        when(provider.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        Usuario u = new Usuario();
        u.setUsuario("admin");
        u.setEmail("admin@empresa.com.br");
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(u));
        service = new NotaFiscalEmailXmlService(zipService, provider, auditor, "nfe@empresa.com.br", "", 200);
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
        assertThat(m.getFrom()[0].toString()).isEqualTo("nfe@empresa.com.br");
        assertThat(m.getReplyTo()[0].toString()).isEqualTo("admin@empresa.com.br");
        assertThat(m.getSubject()).isEqualTo("XMLs das notas");
        assertThat(m.getContent()).isInstanceOf(MimeMultipart.class);
        assertThat(resposta.getEnviadas()).containsExactly(100L);
        assertThat(resposta.getDestinatarios()).containsExactly("cliente@dominio.com.br", "copia@dominio.com.br");
        assertThat(resposta.getTamanhoZipBytes()).isPositive();
    }

    @Test
    void semConfiguracaoDoServidorResponde503Claro() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertThatThrownBy(() -> service.enviar(request(1L))).isInstanceOf(EmailNaoConfiguradoException.class)
                .hasMessageContaining("Envio de e-mail não configurado");
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
