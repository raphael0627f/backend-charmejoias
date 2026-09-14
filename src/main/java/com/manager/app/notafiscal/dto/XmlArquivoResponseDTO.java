package com.manager.app.notafiscal.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({ "nomeArquivo", "tamanhoBytes", "ultimaModificacao" })
public class XmlArquivoResponseDTO {
    private String nomeArquivo;
    private long tamanhoBytes;
    private LocalDateTime ultimaModificacao;
}
