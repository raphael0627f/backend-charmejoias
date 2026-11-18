package com.manager.app.financeiro;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// o @PreAuthorize só roda depois que o Spring converte e valida o corpo da requisição; com corpo vazio ou inválido o usuário sem permissão
// receberia 400 em vez de 403. Este interceptor avalia a MESMA anotação @PreAuthorize do método antes da leitura do corpo, de modo uniforme
// em todos os endpoints do Financeiro (autorização antes da validação). O @PreAuthorize continua valendo como segunda camada.
@Configuration
public class FinanceiroAutorizacaoInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final PreAuthorizeAuthorizationManager manager = new PreAuthorizeAuthorizationManager();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/bancos/**", "/categorias-despesa/**", "/formas-pagamento/**", "/centros-custo/**",
                "/movimentacoes-banco/**", "/contas-pagar/**", "/categorias-receita/**", "/contas-receber/**",
                "/pre-acertos-consignacao/*/financeiro", "/pre-acertos-consignacao/*/titulos",
                "/acertos-consignacao/*/financeiro", "/acertos-consignacao/*/titulos", "/infinitepay/**", "/links-pagamento/**", "/excedentes-acerto/**", "/clientes/*/credito/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) return true;
        if (metodo.getMethodAnnotation(PreAuthorize.class) == null) return true;
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null) return true; // sem autenticação quem responde (401) é a cadeia de filtros
        SimpleMethodInvocation invocacao = new SimpleMethodInvocation(metodo.getBean(), metodo.getMethod(), new Object[metodo.getMethod().getParameterCount()]);
        AuthorizationResult resultado = manager.authorize(() -> autenticacao, invocacao);
        if (resultado != null && !resultado.isGranted()) throw new AccessDeniedException("Acesso negado.");
        return true;
    }
}
