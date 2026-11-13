package com.manager.app.email;

import com.manager.app.empresa.Empresa;
import com.manager.app.empresa.EmpresaRepository;
import com.manager.app.endereco.Endereco;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.domain.PageImpl;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailTemplateServiceTest {

    @TempDir Path pasta;

    EmpresaRepository empresaRepository = mock(EmpresaRepository.class);

    EmailTemplateService service(String caminhoLogo) {
        when(empresaRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        return new EmailTemplateService(empresaRepository, caminhoLogo);
    }

    EmailTemplateService.EmpresaEmail empresaCompleta() {
        return new EmailTemplateService.EmpresaEmail("Charme Jóias", "Charme Comércio de Jóias LTDA", "12.345.678/0001-90",
                "(24) 3323-5374", "Rua das Flores, 100 - Centro - Volta Redonda/RJ - CEP 27200-000");
    }

    EmailTemplateService.Modelo modelo(String mensagem, EmailTemplateService.EmpresaEmail empresa) {
        return new EmailTemplateService.Modelo("Envio de XMLs", mensagem, "2 nota(s) fiscal(is) em anexo",
                List.of(new EmailTemplateService.Linha("Notas fiscais", "NF 300, NF 301"),
                        new EmailTemplateService.Linha("Arquivo", "nfe-xmls-20261113-0300.zip (4,1 KB)")), empresa);
    }

    @Test
    void htmlDoUsuarioEScriptsSaoEscapados() {
        EmailTemplateService.Conteudo c = service("./nao-existe/logo.png")
                .montar(modelo("<script>alert('x')</script> & <b>negrito</b> \"aspas\"", empresaCompleta()));
        assertThat(c.html()).doesNotContain("<script>").doesNotContain("<b>negrito</b>")
                .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &lt;b&gt;negrito&lt;/b&gt; &quot;aspas&quot;");
    }

    @Test
    void tituloEResumoTambemSaoEscapados() {
        EmailTemplateService.Modelo m = new EmailTemplateService.Modelo("<img src=x onerror=alert(1)>", "oi", "Resumo <i>",
                List.of(new EmailTemplateService.Linha("<b>r</b>", "<u>v</u>")), empresaCompleta());
        String html = service("./nao-existe/logo.png").montar(m).html();
        assertThat(html).doesNotContain("<img src=x").doesNotContain("<i>").doesNotContain("<b>r</b>").doesNotContain("<u>v</u>");
    }

    @Test
    void quebrasDeLinhaViramParagrafosEBr() {
        String html = service("./nao-existe/logo.png").montar(modelo("Primeira linha\nsegunda linha\n\nOutro paragrafo", empresaCompleta())).html();
        assertThat(html).contains("Primeira linha<br>segunda linha</p>").contains(">Outro paragrafo</p>");
        assertThat(EmailTemplateService.paragrafos("a\r\nb\r\n\r\nc")).containsExactly("a\nb", "c");
    }

    @Test
    void linksHttpViramLinksSegurosEOutrosEsquemasNao() {
        String html = service("./nao-existe/logo.png")
                .montar(modelo("Veja https://exemplo.com.br/a?x=1&y=2. Ou javascript:alert(1) e <a href=\"http://x\">y</a>", empresaCompleta())).html();
        assertThat(html).contains("<a href=\"https://exemplo.com.br/a?x=1&amp;y=2\" target=\"_blank\" rel=\"noopener noreferrer\"")
                .contains(">https://exemplo.com.br/a?x=1&amp;y=2</a>.")
                .doesNotContain("<a href=\"javascript")
                .contains("&lt;a href=&quot;");
        // a aspa escapada nao escapa do atributo href
        assertThat(EmailTemplateService.comLinks("http://a.com/&quot;onclick=x")).contains("href=\"http://a.com/\"");
    }

    @Test
    void rodapeOmiteCamposVaziosESempreTemAFraseAutomatica() {
        EmailTemplateService s = service("./nao-existe/logo.png");
        String completo = s.montar(modelo("oi", empresaCompleta())).html();
        assertThat(completo).contains("Charme Comércio de Jóias LTDA").contains("CNPJ 12.345.678/0001-90").contains("Tel. (24) 3323-5374")
                .contains("Rua das Flores, 100").contains(EmailTemplateService.FRASE_AUTOMATICA);

        String vazio = s.montar(modelo("oi", new EmailTemplateService.EmpresaEmail("Charme Jóias", null, " ", null, null))).html();
        assertThat(vazio).doesNotContain("CNPJ").doesNotContain("Tel.").contains(EmailTemplateService.FRASE_AUTOMATICA);

        EmailTemplateService.Conteudo parcial = s.montar(modelo("oi", new EmailTemplateService.EmpresaEmail("Charme", null, "12.345.678/0001-90", null, null)));
        assertThat(parcial.html()).contains("CNPJ 12.345.678/0001-90").doesNotContain("Tel.");
        assertThat(parcial.texto()).contains("CNPJ 12.345.678/0001-90").doesNotContain("Tel.").contains(EmailTemplateService.FRASE_AUTOMATICA);
    }

    @Test
    void semLogoUsaONomeDaEmpresaEmTextoESemCid() {
        EmailTemplateService.Conteudo c = service("./nao-existe/logo.png").montar(modelo("oi", empresaCompleta()));
        assertThat(c.logo()).isNull();
        assertThat(c.html()).doesNotContain("cid:").contains(">Charme Jóias</td>");
    }

    @Test
    void comLogoEmbuteInlinePorCidComAltEPaletaDoModelo() throws Exception {
        Path png = pasta.resolve("logo.png");
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
        EmailTemplateService.Conteudo c = service(png.toString()).montar(modelo("oi", empresaCompleta()));
        assertThat(c.logo()).isNotEmpty();
        assertThat(c.logoTipo()).isEqualTo("image/png");
        assertThat(c.html()).contains("src=\"cid:logo\"").contains("alt=\"Logotipo de Charme Jóias\"")
                .contains("lang=\"pt-BR\"").contains("name=\"viewport\"").contains("charset=\"UTF-8\"")
                .contains("#1F1F23").contains("#D4AF37").contains("#F4F4F5").contains("max-width:600px")
                .doesNotContain("<script").doesNotContain("<link").doesNotContain("http://").doesNotContain("src=\"http");
    }

    @Test
    void logoDeFormatoNaoSuportadoOuInexistenteEIgnorado() throws Exception {
        Path svg = pasta.resolve("logo.svg");
        Files.writeString(svg, "<svg/>");
        assertThat(service(svg.toString()).montar(modelo("oi", empresaCompleta())).logo()).isNull();
    }

    @Test
    void mensagemMultipartTemTextoHtmlLogoInlineEAnexo() throws Exception {
        Path png = pasta.resolve("logo.png");
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
        EmailTemplateService s = service(png.toString());
        MimeMessage msg = new JavaMailSenderImpl().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
        helper.setTo("a@b.com");
        helper.setSubject("Relatório de áreas e ações");
        s.aplicar(helper, s.montar(modelo("Linha 1\nLinha 2", empresaCompleta())));
        helper.addAttachment("nfe-xmls.zip", new org.springframework.core.io.ByteArrayResource(new byte[] {1, 2, 3}), "application/zip");
        msg.saveChanges();

        List<String> tipos = new ArrayList<>();
        List<String> cids = new ArrayList<>();
        percorrer(msg, tipos, cids);
        assertThat(tipos).contains("multipart/alternative", "text/plain", "text/html", "image/png", "application/zip");
        assertThat(cids).contains("<logo>");
        assertThat(msg.getSubject()).isEqualTo("Relatório de áreas e ações");
    }

    @Test
    void textoSimplesTemTituloMensagemResumoERodape() {
        EmailTemplateService.Conteudo c = service("./nao-existe/logo.png").montar(modelo("Linha 1\nLinha 2", empresaCompleta()));
        assertThat(c.texto()).startsWith("Envio de XMLs\n\nLinha 1\nLinha 2")
                .contains("2 nota(s) fiscal(is) em anexo").contains("- Notas fiscais: NF 300, NF 301")
                .contains("Charme Comércio de Jóias LTDA").contains(EmailTemplateService.FRASE_AUTOMATICA);
    }

    @Test
    void dadosDaEmpresaSaoFormatados() {
        Empresa e = new Empresa();
        e.setNomeFantasia("Charme");
        e.setRazaoSocial("Charme LTDA");
        e.setCnpj("12345678000190");
        e.setTelefone("2433235374");
        Endereco en = new Endereco();
        en.setLogradouro("Rua A");
        en.setNumero("10");
        en.setBairro("Centro");
        en.setMunicipio("Volta Redonda");
        en.setUf("RJ");
        en.setCep("27200000");
        e.setEndereco(en);
        EmailTemplateService.EmpresaEmail dados = EmailTemplateService.deEmpresa(e);
        assertThat(dados.nome()).isEqualTo("Charme");
        assertThat(dados.cnpj()).isEqualTo("12.345.678/0001-90");
        assertThat(dados.endereco()).isEqualTo("Rua A, 10 - Centro - Volta Redonda/RJ - CEP 27200000");
        e.setEndereco(null);
        e.setNomeFantasia(" ");
        assertThat(EmailTemplateService.deEmpresa(e).nome()).isEqualTo("Charme LTDA");
        assertThat(EmailTemplateService.deEmpresa(e).endereco()).isNull();
    }

    private void percorrer(Part parte, List<String> tipos, List<String> cids) throws Exception {
        String tipo = parte.getContentType().toLowerCase().split(";")[0].trim();
        tipos.add(tipo);
        if (parte instanceof jakarta.mail.internet.MimePart mp && mp.getContentID() != null) cids.add(mp.getContentID());
        if (parte.getContent() instanceof Multipart mpart) {
            for (int i = 0; i < mpart.getCount(); i++) {
                BodyPart bp = mpart.getBodyPart(i);
                percorrer(bp, tipos, cids);
            }
        }
    }

    // gera o preview HTML para avaliacao visual: EMAIL_PREVIEW_PATH=<arquivo.html> ./mvnw test -Dtest=EmailTemplateServiceTest#gerarPreview
    @Test
    @EnabledIfEnvironmentVariable(named = "EMAIL_PREVIEW_PATH", matches = ".+")
    void gerarPreview() throws Exception {
        // logo de demonstracao: recorte do cabecalho de relatorio existente (so o logotipo, sem telefone/titulo)
        Path logo = pasta.resolve("logo.png");
        File origem = new File("../relatorio/cabecalhoCharme.png");
        String caminho = "./nao-existe/logo.png";
        if (origem.exists()) {
            BufferedImage img = ImageIO.read(origem);
            ImageIO.write(img.getSubimage(0, 0, Math.min(255, img.getWidth()), Math.min(272, img.getHeight())), "png", logo.toFile());
            caminho = logo.toString();
        }
        EmailTemplateService s = service(caminho);
        EmailTemplateService.Modelo m = new EmailTemplateService.Modelo("Notas fiscais de outubro",
                "Prezados,\n\nSegue em anexo o arquivo com os XMLs das notas fiscais emitidas em outubro, conforme combinado.\nQualquer dúvida, estamos à disposição: https://exemplo.com.br/contato.\n\nAtenciosamente,\nEquipe Fiscal",
                "2 nota(s) fiscal(is) em anexo",
                List.of(new EmailTemplateService.Linha("Notas fiscais", "NF 300, NF 301"),
                        new EmailTemplateService.Linha("Arquivo", "nfe-xmls-20261113-0300.zip (4,1 KB)")), empresaCompleta());
        EmailTemplateService.Conteudo c = s.montar(m);
        String html = c.html();
        if (c.logo() != null) {
            html = html.replace("cid:logo", "data:" + c.logoTipo() + ";base64," + Base64.getEncoder().encodeToString(c.logo()));
        }
        Files.write(Path.of(System.getenv("EMAIL_PREVIEW_PATH")), html.getBytes(StandardCharsets.UTF_8));
    }
}
