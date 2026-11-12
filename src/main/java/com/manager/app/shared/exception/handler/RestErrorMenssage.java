package com.manager.app.shared.exception.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.util.Date;
import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public class RestErrorMenssage {
    private Date timestamp;
    private HttpStatus status;
    private String menssage;
    private String path;
    // só nas falhas de validação do corpo/parâmetros: mesmo formato que o frontend consome (errors[].field/defaultMessage)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<CampoInvalido> errors;

    public RestErrorMenssage(Date timestamp, HttpStatus status, String menssage, String path) {
        this(timestamp, status, menssage, path, null);
    }

    public record CampoInvalido(String field, String defaultMessage) {}
}
