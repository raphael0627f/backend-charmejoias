package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.ContaPagarCriacaoResponseDTO;
import com.manager.app.financeiro.dto.ContaPagarRequestDTO;
import com.manager.app.financeiro.dto.LoteLancamentoDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// lançamento em lote de títulos simples: cada linha em transação própria, uma linha inválida nunca derruba as outras
@Service
@RequiredArgsConstructor
public class ContaPagarLoteService {

    static final int LIMITE = 100;

    private final ContaPagarService contaPagarService;
    private final Validator validator;
    private final PlatformTransactionManager transactionManager;

    public LoteLancamentoDTOs.Resultado lancar(LoteLancamentoDTOs.Request request) {
        if (request == null || request.itens() == null || request.itens().isEmpty()) throw new ItemInvalidoException("Informe ao menos um título.");
        if (request.itens().size() > LIMITE) throw new ItemInvalidoException("O lançamento em lote aceita no máximo " + LIMITE + " títulos por requisição.");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<LoteLancamentoDTOs.Item> resultados = new ArrayList<>();
        int criadas = 0;
        for (int i = 0; i < request.itens().size(); i++) {
            ContaPagarRequestDTO item = request.itens().get(i);
            try {
                if (item == null) throw new ItemInvalidoException("A linha está vazia.");
                if (item.parcelamento() != null) throw new ItemInvalidoException("O lançamento em lote não aceita parcelamento.");
                if (item.recorrencia() != null) throw new ItemInvalidoException("O lançamento em lote não aceita recorrência.");
                Set<ConstraintViolation<ContaPagarRequestDTO>> violacoes = validator.validate(item);
                if (!violacoes.isEmpty()) {
                    ConstraintViolation<ContaPagarRequestDTO> v = violacoes.stream().min(Comparator.comparing(c -> c.getPropertyPath().toString())).get();
                    resultados.add(new LoteLancamentoDTOs.Item(i, false, null, null, v.getMessage(), v.getPropertyPath().toString()));
                    continue;
                }
                ContaPagarCriacaoResponseDTO criado = tx.execute(status -> contaPagarService.criar(item));
                var titulo = criado.titulos().get(0);
                resultados.add(new LoteLancamentoDTOs.Item(i, true, titulo.id(), titulo.numero(), null, null));
                criadas++;
            } catch (ItemInvalidoException | ObjetoNaoEncontradoException e) {
                resultados.add(new LoteLancamentoDTOs.Item(i, false, null, null, e.getMessage(), campo(e.getMessage())));
            }
        }
        return new LoteLancamentoDTOs.Resultado(criadas, resultados.size() - criadas, resultados);
    }

    // identifica o campo pela mensagem formal quando possível
    static String campo(String mensagem) {
        if (mensagem == null) return null;
        String m = mensagem.toLowerCase(java.util.Locale.ROOT);
        String[][] mapa = {{"linha digitável", "linhaDigitavel"}, {"centro de custo", "centroCustoId"}, {"forma de pagamento", "formaPrevistaId"},
                {"categoria", "categoriaId"}, {"competência", "competencia"}, {"vencimento", "vencimento"}, {"descrição", "descricao"},
                {"favorecido", "favorecido"}, {"documento", "documento"}, {"observação", "observacao"}, {"valor", "valor"}, {"emissão", "emissao"}};
        for (String[] par : mapa) if (m.contains(par[0])) return par[1];
        return null;
    }
}
