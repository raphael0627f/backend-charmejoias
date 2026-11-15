package com.manager.app.financeiro;

import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// metadados do anexo (documento, boleto ou comprovante de pagamento); o conteúdo fica em arquivo no diretório de anexos (campo arquivo)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "conta_pagar_anexo")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class ContaPagarAnexo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_pagar_id")
    private ContaPagar contaPagar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pagamento_id")
    private ContaPagarPagamento pagamento;

    @Enumerated(EnumType.STRING)
    private TipoAnexo tipo;

    private String nomeOriginal;
    private String tipoConteudo;
    private long tamanho;
    private String arquivo;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private Usuario createdBy;
}
