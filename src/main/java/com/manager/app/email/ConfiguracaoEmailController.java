package com.manager.app.email;

import com.manager.app.email.dto.ConfiguracaoEmailRequestDTO;
import com.manager.app.email.dto.ConfiguracaoEmailResponseDTO;
import com.manager.app.email.dto.ConfiguracaoEmailTesteRequestDTO;
import com.manager.app.email.dto.ConfiguracaoEmailTesteResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// só quem tem CONFIGURACAO_EMAIL_GERENCIAR (perfil ADMIN, mesmo padrão dos backups): leitura e escrita - a tela guarda credenciais
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/configuracao-email")
@PreAuthorize("hasAuthority('CONFIGURACAO_EMAIL_GERENCIAR')")
public class ConfiguracaoEmailController {

    private final ConfiguracaoEmailService service;

    @GetMapping
    public ResponseEntity<ConfiguracaoEmailResponseDTO> obter(){
        return ResponseEntity.ok(service.obter());
    }

    @PutMapping
    public ResponseEntity<ConfiguracaoEmailResponseDTO> salvar(@RequestBody @Valid ConfiguracaoEmailRequestDTO request){
        return ResponseEntity.ok(service.salvar(request));
    }

    @PostMapping(value = "/testar")
    public ResponseEntity<ConfiguracaoEmailTesteResponseDTO> testar(@RequestBody @Valid ConfiguracaoEmailTesteRequestDTO request){
        return ResponseEntity.ok(service.testar(request));
    }
}
