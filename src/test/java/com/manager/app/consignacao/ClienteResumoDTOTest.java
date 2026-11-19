package com.manager.app.consignacao;

import com.manager.app.cliente.Cliente;
import com.manager.app.consignacao.dto.ClienteResumoDTO;
import com.manager.app.endereco.Endereco;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClienteResumoDTOTest {

    private static Endereco endereco(String municipio, String uf, boolean principal) {
        Endereco e = new Endereco();
        e.setMunicipio(municipio);
        e.setUf(uf);
        e.setPrincipal(principal);
        return e;
    }

    private static Cliente cliente() {
        Cliente c = new Cliente();
        c.setCodigo("10815RF");
        c.setNome("Maria");
        return c;
    }

    @Test
    void usaOEnderecoPrincipal() {
        Cliente c = cliente();
        c.adicionarEndereco(endereco("Anápolis", "GO", false));
        c.adicionarEndereco(endereco("Goiânia", "GO", true));
        ClienteResumoDTO dto = ClienteResumoDTO.de(c);
        assertThat(dto.getCodigo()).isEqualTo("10815RF");
        assertThat(dto.getNome()).isEqualTo("Maria");
        assertThat(dto.getCidade()).isEqualTo("Goiânia");
        assertThat(dto.getUf()).isEqualTo("GO");
    }

    @Test
    void semPrincipalUsaOPrimeiroEndereco() {
        Cliente c = cliente();
        c.adicionarEndereco(endereco("Anápolis", "GO", false));
        assertThat(ClienteResumoDTO.de(c).getCidade()).isEqualTo("Anápolis");
    }

    @Test
    void semEnderecoCidadeEUfNulas() {
        ClienteResumoDTO dto = ClienteResumoDTO.de(cliente());
        assertThat(dto.getCodigo()).isEqualTo("10815RF");
        assertThat(dto.getCidade()).isNull();
        assertThat(dto.getUf()).isNull();
    }

    @Test
    void clienteNuloDevolveNulo() {
        assertThat(ClienteResumoDTO.de(null)).isNull();
    }

    @Test
    void construtorDeDoisArgumentosMantemOComportamentoAnterior() {
        ClienteResumoDTO dto = new ClienteResumoDTO("A1", "Ana");
        assertThat(dto.getCidade()).isNull();
        assertThat(dto.getUf()).isNull();
    }
}
