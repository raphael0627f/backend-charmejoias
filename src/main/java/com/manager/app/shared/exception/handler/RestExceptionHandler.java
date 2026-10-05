package com.manager.app.shared.exception.handler;

import com.manager.app.backup.BackupException;
import com.manager.app.notafiscal.DanfeApiException;
import com.manager.app.notafiscal.client.ContoraApiException;
import com.manager.app.spc.client.SpcApiException;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Date;

@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> databaseError(DataAccessException exception, WebRequest request) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, "Banco de dados indisponível",request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ObjetoNaoEncontradoException.class)
    private ResponseEntity<RestErrorMenssage> objetoNaoEncontradoHandler(ObjetoNaoEncontradoException exception, WebRequest request){
        HttpStatus status = HttpStatus.NOT_FOUND;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, exception.getMessage(),request.getDescription(false));
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
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, exception.getMessage(),request.getDescription(false));
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
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, "Credenciais inválidas", request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }

    // idem para AccessDeniedException disparada por @PreAuthorize de método, depois de já
    // autenticado - a que nasce no filtro (rota nem chega no controller) é tratada pelo
    // AccessDeniedHandler de SecurityConfig
    @ExceptionHandler(AccessDeniedException.class)
    private ResponseEntity<RestErrorMenssage> accessDeniedError(AccessDeniedException exception, WebRequest request){
        HttpStatus status = HttpStatus.FORBIDDEN;
        RestErrorMenssage response = new RestErrorMenssage(new Date(), status, "Acesso negado", request.getDescription(false));
        return ResponseEntity.status(status).body(response);
    }
}
