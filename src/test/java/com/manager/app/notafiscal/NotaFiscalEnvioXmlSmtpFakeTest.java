package com.manager.app.notafiscal;

import com.manager.app.email.ConfiguracaoEmailService;
import com.manager.app.email.EmailConfigEfetiva;
import com.manager.app.email.EmailTemplateService;
import com.manager.app.empresa.Empresa;
import com.manager.app.empresa.EmpresaRepository;
import com.manager.app.notafiscal.dto.EnviarXmlEmailRequestDTO;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// envio REAL (SMTP de verdade, sem mock do JavaMailSender) para um servidor SMTP de teste local - so roda se
// EMAIL_SMTP_FAKE=host:porta estiver definido (ex.: localhost:2525); nao usa a configuracao salva no banco
class NotaFiscalEnvioXmlSmtpFakeTest {

    @TempDir Path pasta;

    @Test
    @EnabledIfEnvironmentVariable(named = "EMAIL_SMTP_FAKE", matches = ".+:[0-9]+")
    void enviaEmailHtmlComAnexoParaOSmtpDeTeste() {
        String[] alvo = System.getenv("EMAIL_SMTP_FAKE").split(":");
        NotaFiscalRepository repository = mock(NotaFiscalRepository.class);
        NotaFiscalXmlStorageService storage = new NotaFiscalXmlStorageService(pasta.toString());
        NotaFiscalXmlZipService zip = new NotaFiscalXmlZipService(repository, storage);
        org.springframework.test.util.ReflectionTestUtils.setField(zip, "maxZipBytes", 20L * 1024 * 1024);

        Empresa empresa = new Empresa();
        empresa.setId(1L);
        empresa.setNomeFantasia("Charme Jóias");
        empresa.setRazaoSocial("Charme Comércio de Jóias LTDA");
        empresa.setCnpj("12345678000190");
        empresa.setTelefone("(24) 3323-5374");
        NotaFiscal nota = new NotaFiscal();
        nota.setId(1L);
        nota.setNumero(300L);
        nota.setChaveAcesso("33260907953085000171550020000003001847569988");
        nota.setStatus(StatusNotaFiscal.AUTORIZADA);
        nota.setEmpresa(empresa);
        storage.salvar(nota, "<nfe>exemplo</nfe>".getBytes(StandardCharsets.UTF_8));
        when(repository.findAllById(any())).thenReturn(List.of(nota));

        EmpresaRepository empresaRepository = mock(EmpresaRepository.class);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        EmailTemplateService template = new EmailTemplateService(empresaRepository, "./storage/email/logo.png");

        EmailConfigEfetiva config = new EmailConfigEfetiva(alvo[0], Integer.parseInt(alvo[1]), null, null, "nfe@empresa.teste", "Fiscal Charme Jóias", false, false, 10000);
        ConfiguracaoEmailService configuracao = mock(ConfiguracaoEmailService.class);
        when(configuracao.resolverParaEnvio()).thenReturn(new ConfiguracaoEmailService.EnvioEmail(config.criarSender(), config));
        UsuarioAuditorAware auditor = mock(UsuarioAuditorAware.class);
        Usuario usuario = new Usuario();
        usuario.setUsuario("admin");
        usuario.setEmail("usuario@empresa.teste");
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(usuario));

        EnviarXmlEmailRequestDTO request = new EnviarXmlEmailRequestDTO();
        request.setNotaIds(List.of(1L));
        request.setPara(List.of("fiscal@cliente.teste"));
        request.setCc(List.of("copia@cliente.teste"));
        request.setAssunto("XMLs das notas fiscais – outubro");
        request.setCorpo("Prezados,\n\nSegue em anexo o zip com os XMLs.\nVeja também https://exemplo.com.br/duvidas.\n\n<script>alert(1)</script> Atenciosamente.");

        new NotaFiscalEmailXmlService(zip, configuracao, template, auditor, 200).enviar(request);
    }
}
