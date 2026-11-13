package com.manager.app.email;

import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// configuracao do SMTP de envio: uma unica linha (id = 1). A senha so existe criptografada e nunca sai pela API.
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "configuracao_email")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracaoEmail {

    public static final Long ID_UNICO = 1L;

    @Id
    private Long id = ID_UNICO;

    private String host;
    private Integer porta;
    private String usuario;

    @Column(name = "senha_criptografada")
    private String senhaCriptografada;

    private String remetente;

    @Column(name = "nome_remetente")
    private String nomeRemetente;

    private boolean starttls = true;
    private boolean ssl = false;

    @Column(name = "timeout_ms")
    private Integer timeoutMs = 30000;

    private boolean ativo = false;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por_id")
    private Usuario atualizadoPor;
}
