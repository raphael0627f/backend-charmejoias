package com.manager.app.consignacao.dto;

import com.manager.app.cliente.Cliente;
import com.manager.app.endereco.Endereco;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// resumo do cliente: código e nome; cidade e UF (do endereço principal ou, na falta dele, do primeiro endereço) quando preenchidos pelo serviço
@Getter
@Setter
@NoArgsConstructor
public class ClienteResumoDTO {
    private String codigo;
    private String nome;
    private String cidade;
    private String uf;

    public ClienteResumoDTO(String codigo, String nome) {
        this.codigo = codigo;
        this.nome = nome;
    }

    public ClienteResumoDTO(String codigo, String nome, String cidade, String uf) {
        this(codigo, nome);
        this.cidade = cidade;
        this.uf = uf;
    }

    // código, nome, cidade e UF do cliente (endereço principal; sem principal, o primeiro endereço; sem endereço, cidade e UF nulas)
    public static ClienteResumoDTO de(Cliente cliente) {
        if (cliente == null) return null;
        Endereco endereco = cliente.getEnderecoPrincipal();
        if (endereco == null && cliente.getEnderecos() != null && !cliente.getEnderecos().isEmpty()) endereco = cliente.getEnderecos().get(0);
        return new ClienteResumoDTO(cliente.getCodigo(), cliente.getNome(), endereco == null ? null : endereco.getMunicipio(), endereco == null ? null : endereco.getUf());
    }
}
