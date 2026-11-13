package com.manager.app.shared.exception.handler;

import com.manager.app.backup.BackupException;
import com.manager.app.email.EmailFalhaEnvioException;
import com.manager.app.email.EmailNaoConfiguradoException;
import com.manager.app.notafiscal.DanfeApiException;
import com.manager.app.notafiscal.client.ContoraApiException;
import com.manager.app.spc.client.SpcApiException;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.SqlErros;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Date;
import java.util.List;

@Slf4j
@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> databaseError(DataAccessException exception, WebRequest request) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, "Banco de dados indisponível. Tente novamente em instantes.",request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    // violação de integridade: o Spring escolhe o handler mais específico, então este vale antes do de DataAccessException.
    // unique/FK -> 409, valor maior que a coluna -> 400; o resto continua 503
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RestErrorMenssage> integridade(DataIntegrityViolationException exception, WebRequest request) {
        String sqlState = SqlErros.sqlState(exception);
        HttpStatus status;
        String mensagem;
        if ("23505".equals(sqlState)) {
            status = HttpStatus.CONFLICT;
            mensagem = "Já existe um registro com os dados informados.";
        } else if ("23503".equals(sqlState)) {
            status = HttpStatus.CONFLICT;
            mensagem = "Operação não permitida. O registro está em uso ou faz referência a outro registro inexistente.";
        } else if ("22001".equals(sqlState)) {
            status = HttpStatus.BAD_REQUEST;
            mensagem = "Um dos campos de texto excede o tamanho máximo permitido.";
        } else {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            mensagem = "Banco de dados indisponível. Tente novamente em instantes.";
        }
        return ResponseEntity.status(status).body(new RestErrorMenssage(new Date(), status, mensagem, request.getDescription(false)));
    }

    @ExceptionHandler(ObjetoNaoEncontradoException.class)
    private ResponseEntity<RestErrorMenssage> objetoNaoEncontradoHandler(ObjetoNaoEncontradoException exception, WebRequest request){
        HttpStatus status = HttpStatus.NOT_FOUND;
        log.info("{}: {}", exception.getClass().getSimpleName(), exception.getMessage());
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, semIdentificador(exception.getMessage()), request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ObjetoExistenteException.class)
    private ResponseEntity<RestErrorMenssage> objetoExistente(ObjetoExistenteException exception, WebRequest request){
        HttpStatus status = HttpStatus.CONFLICT;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, exception.getMessage(),request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ObjetoEmUsoException.class)
    private ResponseEntity<RestErrorMenssage> objetoEmUso(ObjetoEmUsoException exception, WebRequest request){
        HttpStatus status = HttpStatus.CONFLICT;
        log.info("{}: {}", exception.getClass().getSimpleName(), exception.getMessage());
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, semIdentificador(exception.getMessage()), request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ItemInvalidoException.class)
    private ResponseEntity<RestErrorMenssage> itemInvalido(ItemInvalidoException exception, WebRequest request){
        HttpStatus status = HttpStatus.BAD_REQUEST;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, exception.getMessage(),request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ContoraApiException.class)
    private ResponseEntity<RestErrorMenssage> contoraApiError(ContoraApiException exception, WebRequest request){
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, exception.getMessage(),request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(DanfeApiException.class)
    private ResponseEntity<RestErrorMenssage> danfeApiError(DanfeApiException exception, WebRequest request){
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, exception.getMessage(),request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    // e-mail: sem configuracao do servidor SMTP (503) ou falha do servidor (502); mensagens ja sem credenciais
    @ExceptionHandler(EmailNaoConfiguradoException.class)
    private ResponseEntity<RestErrorMenssage> emailNaoConfigurado(EmailNaoConfiguradoException exception, WebRequest request){
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, exception.getMessage(),request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(EmailFalhaEnvioException.class)
    private ResponseEntity<RestErrorMenssage> emailFalhaEnvio(EmailFalhaEnvioException exception, WebRequest request){
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, exception.getMessage(),request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(BackupException.class)
    private ResponseEntity<RestErrorMenssage> backupError(BackupException exception, WebRequest request){
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, exception.getMessage(),request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(SpcApiException.class)
    private ResponseEntity<RestErrorMenssage> spcApiError(SpcApiException exception, WebRequest request){
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, exception.getMessage(),request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    // cobre falha de autenticação lançada dentro de um fluxo normal de controller (ex:
    // AuthenticationManager.authenticate() dentro de AuthService.login) - erros de autenticação
    // que nascem no filtro do Spring Security (antes do Spring MVC) são tratados à parte pelo
    // AuthenticationEntryPoint configurado em SecurityConfig, não passam por aqui
    @ExceptionHandler(AuthenticationException.class)
    private ResponseEntity<RestErrorMenssage> authenticationError(AuthenticationException exception, WebRequest request){
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, "Credenciais inválidas.", request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    // idem para AccessDeniedException disparada por @PreAuthorize de método, depois de já
    // autenticado - a que nasce no filtro (rota nem chega no controller) é tratada pelo
    // AccessDeniedHandler de SecurityConfig
    @ExceptionHandler(AccessDeniedException.class)
    private ResponseEntity<RestErrorMenssage> accessDeniedError(AccessDeniedException exception, WebRequest request){
        HttpStatus status = HttpStatus.FORBIDDEN;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, "Acesso negado.", request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    // ---- erros do próprio Spring MVC (corpo/parâmetros inválidos, rota inexistente): mesmo padrão, em português ----

    private ResponseEntity<Object> erro(HttpStatusCode status, String mensagem, WebRequest request, List<RestErrorMenssage.CampoInvalido> campos) {
        HttpStatus http = HttpStatus.valueOf(status.value());
        return ResponseEntity.status(http).body(new RestErrorMenssage(new Date(), http, mensagem, request.getDescription(false), campos));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<RestErrorMenssage.CampoInvalido> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(this::campo)
                .sorted(java.util.Comparator.comparing(RestErrorMenssage.CampoInvalido::field))
                .toList();
        String mensagem = campos.isEmpty() ? "Os dados informados são inválidos." : campos.get(0).defaultMessage();
        return erro(HttpStatus.BAD_REQUEST, mensagem, request, campos);
    }

    private RestErrorMenssage.CampoInvalido campo(FieldError e) {
        String msg = e.getDefaultMessage() != null ? e.getDefaultMessage() : "Valor inválido.";
        return new RestErrorMenssage.CampoInvalido(e.getField(), msg);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<RestErrorMenssage.CampoInvalido> campos = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(e -> new RestErrorMenssage.CampoInvalido(
                        r.getMethodParameter().getParameterName() != null ? r.getMethodParameter().getParameterName() : "parametro",
                        e.getDefaultMessage() != null ? e.getDefaultMessage() : "Valor inválido.")))
                .toList();
        String mensagem = campos.isEmpty() ? "Os dados informados são inválidos." : campos.get(0).defaultMessage();
        return erro(HttpStatus.BAD_REQUEST, mensagem, request, campos);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return erro(HttpStatus.BAD_REQUEST, "O corpo da requisição está ausente ou mal formatado. Revise os dados informados.", request, null);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String campo = ex.getPropertyName() != null ? ex.getPropertyName() : "parâmetro";
        return erro(HttpStatus.BAD_REQUEST, "O valor informado para " + campo + " é inválido.", request, null);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return erro(HttpStatus.BAD_REQUEST, "O parâmetro " + ex.getParameterName() + " é obrigatório.", request, null);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return erro(HttpStatus.NOT_FOUND, "Recurso não encontrado.", request, null);
    }

    // demais erros do MVC (método não permitido, tipo de mídia etc.): mensagem genérica em português pelo status
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        String mensagem = switch (statusCode.value()) {
            case 405 -> "Método não permitido para este recurso.";
            case 406, 415 -> "Formato de conteúdo não suportado.";
            case 404 -> "Recurso não encontrado.";
            case 400 -> "Requisição inválida.";
            default -> statusCode.is5xxServerError() ? "Erro interno. Tente novamente em instantes." : "Não foi possível processar a requisição.";
        };
        return erro(statusCode, mensagem, request, null);
    }

    // 404/"em uso": o identificador técnico (Id: N, Ids: ..., Código: X) fica só no log; a mensagem ao usuário não o exibe
    public static String semIdentificador(String mensagem) {
        if (mensagem == null) return null;
        String limpa = mensagem.replaceAll("\\s*\\b(?:Id|Ids|Código|SKU):\\s.*$", "").trim();
        return limpa.isEmpty() ? mensagem : limpa;
    }
}
