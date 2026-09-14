package com.manager.app.auth;

import com.manager.app.auth.dto.LoginRequestDTO;
import com.manager.app.auth.dto.LoginResponseDTO;
import com.manager.app.auth.dto.MeResponseDTO;
import com.manager.app.auth.dto.RefreshRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/auth")
public class AuthController {

    private final AuthService service;

    @PostMapping(value = "/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO request){
        return ResponseEntity.ok(service.login(request.getUsuario(), request.getSenha()));
    }

    @PostMapping(value = "/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(@RequestBody @Valid RefreshRequestDTO request){
        return ResponseEntity.ok(service.refresh(request.getRefreshToken()));
    }

    @PostMapping(value = "/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid RefreshRequestDTO request){
        service.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/me")
    public ResponseEntity<MeResponseDTO> me(){
        String usuario = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(service.me(usuario));
    }
}
