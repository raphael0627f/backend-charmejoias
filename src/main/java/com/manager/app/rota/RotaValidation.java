package com.manager.app.rota;

import com.manager.app.cliente.Cliente;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.endereco.Endereco;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RotaValidation {

    public void validarEndereco(Cliente cliente, Endereco endereco){
        boolean pertenceAoCliente = cliente.getEnderecos().stream()
                .anyMatch(e -> e.getId().equals(endereco.getId()));
        if (!pertenceAoCliente) {
            throw new ItemInvalidoException("Endereço informado não pertence ao cliente selecionado. clienteId: " + cliente.getId());
        }
    }

    public void validarConsignacoesDoCliente(Cliente cliente, List<Consignacao> consignacoes){
        for (Consignacao consignacao : consignacoes) {
            if (consignacao.getCliente() == null || !consignacao.getCliente().getId().equals(cliente.getId())) {
                throw new ItemInvalidoException("Consignação " + consignacao.getId() + " não pertence ao cliente selecionado. clienteId: " + cliente.getId());
            }
        }
    }
}
