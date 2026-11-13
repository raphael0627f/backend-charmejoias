package com.manager.app.notafiscal.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({ "enviadas", "ignoradasSemXml", "tamanhoZipBytes", "destinatarios" })
public class EnviarXmlEmailResponseDTO {
    // numeros das notas cujos XMLs seguiram no zip
    private List<Long> enviadas;
    // numeros das notas sem nenhum XML disponivel (ficaram fora do zip)
    private List<Long> ignoradasSemXml;
    private long tamanhoZipBytes;
    // Para + Cc
    private List<String> destinatarios;
}
