package com.manager.app.email;

import com.manager.app.empresa.Empresa;
import com.manager.app.empresa.EmpresaRepository;
import com.manager.app.endereco.Endereco;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// modelo visual padronizado dos e-mails enviados pelo sistema (HTML + texto simples, multipart/alternative).
// Para criar um novo e-mail: monte um Modelo (titulo, mensagem, resumo opcional, empresa), chame montar() e aplicar()
// (ver docs/email-modelo-html.md). Layout em tabelas com estilos inline (Gmail/Outlook), sem CSS/JS/imagem externos.
@Slf4j
@Service
public class EmailTemplateService {

    public static final String FRASE_AUTOMATICA = "Mensagem enviada automaticamente pelo sistema. Em caso de dúvida, responda a este e-mail.";
    public static final String CID_LOGO = "logo";

    // paleta (dourado = cor primária do sistema, --brand-pink #d4af37 no frontend). O dourado escuro serve para texto
    // e links sobre fundo claro (contraste AA); o dourado da marca fica para faixas e detalhes sobre o cabeçalho escuro.
    static final String DOURADO = "#D4AF37";
    static final String DOURADO_ESCURO = "#7A5C00";
    static final String CABECALHO = "#1F1F23";
    static final String FUNDO = "#F4F4F5";
    static final String BORDA = "#E4E4E7";
    static final String TEXTO = "#27272A";
    static final String SECUNDARIO = "#71717A";
    static final String RODAPE = "#52525B";
    static final String RESUMO_FUNDO = "#FBF8EC";
    static final String FONTE = "font-family:Arial,Helvetica,sans-serif;";

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"]+");
    static final String LOGO_CLASSPATH = "/images/email-logo.png";
    private static final long LOGO_TAMANHO_MAXIMO = 512 * 1024;

    public record EmpresaEmail(String nome, String razaoSocial, String cnpj, String telefone, String endereco) {}

    public record Linha(String rotulo, String valor) {}

    // titulo = assunto; mensagem = texto livre do usuario (pode ser nulo); resumo = quadro opcional (resumoTitulo + linhas)
    public record Modelo(String titulo, String mensagem, String resumoTitulo, List<Linha> resumo, EmpresaEmail empresa) {}

    // logo: bytes da imagem a embutir por CID (nulo = sem logotipo); logoTipo: image/png, image/jpeg ou image/gif
    public record Conteudo(String html, String texto, byte[] logo, String logoTipo) {}

    private final EmpresaRepository empresaRepository;
    private final String caminhoLogo;

    public EmailTemplateService(EmpresaRepository empresaRepository,
                                @Value("${app.mail.logo-path:}") String caminhoLogo) {
        this.empresaRepository = empresaRepository;
        this.caminhoLogo = caminhoLogo;
    }

    // ---- dados da empresa (cadastro de Empresa: razão social, nome fantasia, CNPJ, telefone e endereço) ----

    @Transactional(readOnly = true)
    public EmpresaEmail empresaPorId(Long id){
        if (id == null) return empresaPadrao();
        return empresaRepository.findById(id).map(EmailTemplateService::deEmpresa).orElseGet(this::empresaPadrao);
    }

    // sem nota de referencia (ex.: e-mail de teste): a primeira empresa cadastrada; sem nenhuma, so um nome generico
    @Transactional(readOnly = true)
    public EmpresaEmail empresaPadrao(){
        return empresaRepository.findAll(PageRequest.of(0, 1, Sort.by("id"))).stream().findFirst()
                .map(EmailTemplateService::deEmpresa)
                .orElse(new EmpresaEmail("Sistema ERP", null, null, null, null));
    }

    static EmpresaEmail deEmpresa(Empresa e){
        String nome = naoVazio(e.getNomeFantasia()) ? e.getNomeFantasia().trim() : (naoVazio(e.getRazaoSocial()) ? e.getRazaoSocial().trim() : "Sistema ERP");
        return new EmpresaEmail(nome, texto(e.getRazaoSocial()), formatarCnpj(e.getCnpj()), texto(e.getTelefone()), formatarEndereco(e.getEndereco()));
    }

    static String formatarCnpj(String cnpj){
        if (!naoVazio(cnpj)) return null;
        String d = cnpj.replaceAll("[^0-9]", "");
        if (d.length() != 14) return cnpj.trim();
        return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/" + d.substring(8, 12) + "-" + d.substring(12);
    }

    static String formatarEndereco(Endereco en){
        if (en == null) return null;
        List<String> partes = new ArrayList<>();
        String rua = naoVazio(en.getLogradouro()) ? en.getLogradouro().trim() : null;
        if (rua != null && naoVazio(en.getNumero())) rua += ", " + en.getNumero().trim();
        if (rua != null) partes.add(rua);
        if (naoVazio(en.getBairro())) partes.add(en.getBairro().trim());
        String cidade = naoVazio(en.getMunicipio()) ? en.getMunicipio().trim() : null;
        if (cidade != null && naoVazio(en.getUf())) cidade += "/" + en.getUf().trim();
        if (cidade != null) partes.add(cidade);
        if (naoVazio(en.getCep())) partes.add("CEP " + en.getCep().trim());
        return partes.isEmpty() ? null : String.join(" - ", partes);
    }

    // ---- montagem ----

    public Conteudo montar(Modelo modelo){
        byte[] logo = lerLogo();
        String logoTipo = logo == null ? null : (caminhoLogo == null || caminhoLogo.isBlank() ? "image/png" : tipoDaImagem(caminhoLogo));
        return new Conteudo(html(modelo, logo != null), texto(modelo), logo, logoTipo);
    }

    // texto simples + HTML (multipart/alternative) e o logotipo inline por CID. O helper deve ter sido criado com
    // MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED (permite alternative + imagem inline + anexo).
    public void aplicar(MimeMessageHelper helper, Conteudo conteudo) throws MessagingException {
        helper.setText(conteudo.texto(), conteudo.html());
        if (conteudo.logo() != null) {
            helper.addInline(CID_LOGO, new ByteArrayResource(conteudo.logo()), conteudo.logoTipo());
        }
    }

    private byte[] lerLogo(){
        if (caminhoLogo == null || caminhoLogo.isBlank()) {
            // padrao: logo leve (240 px) dentro do jar
            try (java.io.InputStream in = EmailTemplateService.class.getResourceAsStream(LOGO_CLASSPATH)) {
                return in != null ? in.readAllBytes() : null;
            } catch (IOException e) {
                log.warn("Logotipo padrão do e-mail não pôde ser lido: {}", e.getClass().getSimpleName());
                return null;
            }
        }
        try {
            Path arquivo = Path.of(caminhoLogo);
            if (!Files.isRegularFile(arquivo) || tipoDaImagem(caminhoLogo) == null) return null;
            if (Files.size(arquivo) > LOGO_TAMANHO_MAXIMO) {
                log.warn("Logotipo do e-mail ignorado: arquivo maior que {} KB ({})", LOGO_TAMANHO_MAXIMO / 1024, caminhoLogo);
                return null;
            }
            return Files.readAllBytes(arquivo);
        } catch (IOException | RuntimeException e) {
            log.warn("Logotipo do e-mail não pôde ser lido ({}): {}", caminhoLogo, e.getClass().getSimpleName());
            return null;
        }
    }

    private static String tipoDaImagem(String caminho){
        String nome = caminho.toLowerCase(Locale.ROOT);
        if (nome.endsWith(".png")) return "image/png";
        if (nome.endsWith(".jpg") || nome.endsWith(".jpeg")) return "image/jpeg";
        if (nome.endsWith(".gif")) return "image/gif";
        return null; // SVG nao e suportado pelos clientes de e-mail
    }

    // ---- HTML ----

    private String html(Modelo m, boolean comLogo){
        EmpresaEmail empresa = m.empresa() != null ? m.empresa() : new EmpresaEmail("Sistema ERP", null, null, null, null);
        String titulo = escapar(m.titulo());
        StringBuilder h = new StringBuilder(4096);
        h.append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n")
                .append("<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n<meta name=\"x-apple-disable-message-reformatting\">\n")
                .append("<title>").append(titulo).append("</title>\n</head>\n");
        h.append("<body style=\"margin:0;padding:0;background-color:").append(FUNDO).append(";\">\n");
        h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background-color:").append(FUNDO).append(";\">\n<tr><td align=\"center\" style=\"padding:24px 12px;\">\n");
        // cartao de 600px (Outlook respeita o atributo width; os demais, o max-width)
        h.append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"width:100%;max-width:600px;\">\n");

        // cabecalho: faixa dourada fina + faixa escura com logotipo (ou nome) e nome da empresa
        h.append("<tr><td style=\"background-color:").append(DOURADO).append(";height:4px;line-height:4px;font-size:0;border-radius:6px 6px 0 0;\">&nbsp;</td></tr>\n");
        h.append("<tr><td align=\"center\" style=\"background-color:").append(CABECALHO).append(";padding:24px;\">\n");
        if (comLogo) {
            // so a logo, centralizada, direto sobre o escuro (imagem 2x para telas retina, exibida a 126x140)
            h.append("<img src=\"cid:").append(CID_LOGO).append("\" alt=\"Logotipo de ").append(escapar(empresa.nome()))
                    .append("\" width=\"126\" height=\"140\" style=\"display:block;margin:0 auto;width:126px;height:140px;border:0;\">");
        } else {
            h.append("<span style=\"").append(FONTE).append("color:").append(DOURADO).append(";font-size:22px;font-weight:bold;line-height:30px;\">")
                    .append(escapar(empresa.nome())).append("</span>");
        }
        h.append("\n</td></tr>\n");

        // corpo: titulo, mensagem, resumo
        h.append("<tr><td style=\"background-color:#FFFFFF;border-left:1px solid ").append(BORDA).append(";border-right:1px solid ").append(BORDA)
                .append(";padding:28px 24px 8px 24px;\">\n");
        h.append("<h1 style=\"margin:0 0 16px 0;").append(FONTE).append("color:").append(TEXTO).append(";font-size:20px;line-height:28px;font-weight:bold;\">")
                .append(titulo).append("</h1>\n");
        if (naoVazio(m.mensagem())) {
            for (String paragrafo : paragrafos(m.mensagem())) {
                h.append("<p style=\"margin:0 0 14px 0;").append(FONTE).append("color:").append(TEXTO).append(";font-size:15px;line-height:24px;word-break:break-word;overflow-wrap:anywhere;\">")
                        .append(comLinks(escapar(paragrafo)).replace("\n", "<br>")).append("</p>\n");
            }
        }
        h.append("</td></tr>\n");
        if (m.resumo() != null && !m.resumo().isEmpty()) {
            h.append("<tr><td style=\"background-color:#FFFFFF;border-left:1px solid ").append(BORDA).append(";border-right:1px solid ").append(BORDA)
                    .append(";padding:0 24px 24px 24px;\">\n");
            h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background-color:").append(RESUMO_FUNDO)
                    .append(";border:1px solid ").append(BORDA).append(";border-left:4px solid ").append(DOURADO).append(";\">\n");
            if (naoVazio(m.resumoTitulo())) {
                h.append("<tr><td colspan=\"2\" style=\"padding:14px 16px 6px 16px;").append(FONTE).append("color:").append(DOURADO_ESCURO)
                        .append(";font-size:13px;font-weight:bold;\">").append(escapar(m.resumoTitulo())).append("</td></tr>\n");
            }
            for (Linha linha : m.resumo()) {
                h.append("<tr><td valign=\"top\" width=\"32%\" style=\"padding:6px 8px 6px 16px;").append(FONTE).append("color:").append(SECUNDARIO)
                        .append(";font-size:13px;line-height:20px;\">").append(escapar(linha.rotulo())).append("</td>")
                        .append("<td valign=\"top\" style=\"padding:6px 16px 6px 0;").append(FONTE).append("color:").append(TEXTO)
                        .append(";font-size:14px;line-height:20px;word-break:break-word;overflow-wrap:anywhere;\">").append(escapar(linha.valor()).replace("\n", "<br>")).append("</td></tr>\n");
            }
            h.append("<tr><td colspan=\"2\" style=\"height:8px;font-size:0;line-height:8px;\">&nbsp;</td></tr>\n</table>\n</td></tr>\n");
        } else {
            h.append("<tr><td style=\"background-color:#FFFFFF;border-left:1px solid ").append(BORDA).append(";border-right:1px solid ").append(BORDA)
                    .append(";height:16px;font-size:0;line-height:16px;\">&nbsp;</td></tr>\n");
        }
        h.append("<tr><td style=\"background-color:#FFFFFF;border:1px solid ").append(BORDA).append(";border-top:0;height:1px;font-size:0;line-height:1px;border-radius:0 0 6px 6px;\">&nbsp;</td></tr>\n");

        // rodape: dados da empresa (so o que estiver preenchido) e a frase automatica
        h.append("<tr><td align=\"center\" style=\"padding:20px 16px 4px 16px;").append(FONTE).append("color:").append(RODAPE).append(";font-size:12px;line-height:18px;\">\n");
        for (String linha : linhasRodape(empresa)) {
            h.append(escapar(linha)).append("<br>\n");
        }
        h.append("<span style=\"display:block;margin-top:10px;\">").append(escapar(FRASE_AUTOMATICA)).append("</span>\n</td></tr>\n");
        h.append("</table>\n</td></tr>\n</table>\n</body>\n</html>\n");
        return h.toString();
    }

    // razao social, CNPJ, telefone e endereco - omite o que estiver vazio
    private List<String> linhasRodape(EmpresaEmail e){
        List<String> linhas = new ArrayList<>();
        if (naoVazio(e.razaoSocial())) linhas.add(e.razaoSocial());
        List<String> ids = new ArrayList<>();
        if (naoVazio(e.cnpj())) ids.add("CNPJ " + e.cnpj());
        if (naoVazio(e.telefone())) ids.add("Tel. " + e.telefone());
        if (!ids.isEmpty()) linhas.add(String.join(" · ", ids));
        if (naoVazio(e.endereco())) linhas.add(e.endereco());
        return linhas;
    }

    // ---- texto simples ----

    private String texto(Modelo m){
        EmpresaEmail empresa = m.empresa() != null ? m.empresa() : new EmpresaEmail("Sistema ERP", null, null, null, null);
        StringBuilder t = new StringBuilder();
        t.append(m.titulo()).append("\n\n");
        if (naoVazio(m.mensagem())) {
            t.append(normalizarQuebras(m.mensagem()).trim()).append("\n\n");
        }
        if (m.resumo() != null && !m.resumo().isEmpty()) {
            if (naoVazio(m.resumoTitulo())) t.append(m.resumoTitulo()).append("\n");
            for (Linha l : m.resumo()) {
                t.append("- ").append(l.rotulo()).append(": ").append(l.valor().replace("\n", ", ")).append("\n");
            }
            t.append("\n");
        }
        t.append("--\n").append(empresa.nome()).append("\n");
        for (String linha : linhasRodape(empresa)) {
            t.append(linha).append("\n");
        }
        t.append("\n").append(FRASE_AUTOMATICA).append("\n");
        return t.toString();
    }

    // ---- utilidades (package-private para teste) ----

    // todo texto do usuario passa por aqui: nenhum HTML/script dele chega ao e-mail
    static String escapar(String valor){
        if (valor == null) return "";
        StringBuilder sb = new StringBuilder(valor.length() + 16);
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String normalizarQuebras(String valor){
        return valor.replace("\r\n", "\n").replace('\r', '\n');
    }

    // paragrafos separados por linha em branco; quebras simples dentro do paragrafo viram <br> depois
    static List<String> paragrafos(String mensagem){
        List<String> lista = new ArrayList<>();
        for (String p : normalizarQuebras(mensagem).trim().split("\n[ \t]*\n+")) {
            if (!p.isBlank()) lista.add(p.trim());
        }
        return lista;
    }

    // recebe texto JA escapado: links http(s) viram <a> seguros (sem javascript:, rel noopener); pontuacao final fica fora do link
    static String comLinks(String escapado){
        Matcher m = URL.matcher(escapado);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String url = m.group();
            int corte = url.length();
            for (String marca : new String[] {"&quot;", "&lt;", "&gt;", "&#39;"}) {
                int i = url.indexOf(marca);
                if (i >= 0 && i < corte) corte = i;
            }
            url = url.substring(0, corte);
            String resto = m.group().substring(corte);
            while (!url.isEmpty() && ".,;:!?)".indexOf(url.charAt(url.length() - 1)) >= 0) {
                resto = url.charAt(url.length() - 1) + resto;
                url = url.substring(0, url.length() - 1);
            }
            String link = "<a href=\"" + url + "\" target=\"_blank\" rel=\"noopener noreferrer\" style=\"color:" + DOURADO_ESCURO + ";text-decoration:underline;\">" + url + "</a>";
            m.appendReplacement(sb, Matcher.quoteReplacement(link + resto));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean naoVazio(String v){
        return v != null && !v.isBlank();
    }

    private static String texto(String v){
        return naoVazio(v) ? v.trim() : null;
    }
}
