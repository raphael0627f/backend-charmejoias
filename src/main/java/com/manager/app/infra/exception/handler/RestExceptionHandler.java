package com.manager.app.infra.exception.handler;

import com.manager.app.infra.exception.ObjetoEmUsoException;
import com.manager.app.infra.exception.ObjetoExistenteException;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
