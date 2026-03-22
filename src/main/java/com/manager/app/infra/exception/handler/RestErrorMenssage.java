package com.manager.app.infra.exception.handler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.util.Date;

@AllArgsConstructor
@Getter
@Setter
public class RestErrorMenssage {
    private Date timestamp;
    private HttpStatus status;
    private String menssage;
    private String path;

}
