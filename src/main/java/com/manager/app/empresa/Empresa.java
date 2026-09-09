package com.manager.app.empresa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.manager.app.endereco.Endereco;

import java.time.LocalDateTime;

@Entity
@Table(name = "empresas")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Empresa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String razaoSocial;
    private String nomeFantasia;
    private String cnpj;
    private String inscricaoEstadual;
    private String cnae;
    private String telefone;

    @Enumerated(EnumType.STRING)
    private RegimeTributario regimeTributario;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "endereco_id")
    private Endereco endereco;

    private String contoraCompanyId;
    private boolean certificadoEnviado;
    private LocalDateTime certificadoEnviadoEm;

    private Long proximoNumeroNfe;
    private Integer serieNfe;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
