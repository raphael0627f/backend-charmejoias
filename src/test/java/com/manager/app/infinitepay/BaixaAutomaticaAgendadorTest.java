package com.manager.app.infinitepay;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BaixaAutomaticaAgendadorTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 23, 10, 0, 0);

    @Test
    void primeiraVezSempreExecuta() {
        assertTrue(BaixaAutomaticaAgendador.deveExecutar(T, null, 2));
    }

    @Test
    void respeitaOIntervaloConfigurado() {
        assertFalse(BaixaAutomaticaAgendador.deveExecutar(T.plusSeconds(90), T, 2));
        assertTrue(BaixaAutomaticaAgendador.deveExecutar(T.plusMinutes(2), T, 2));
        assertTrue(BaixaAutomaticaAgendador.deveExecutar(T.plusMinutes(3), T, 2));
    }

    @Test
    void intervaloMenorQueUmVaiParaUmMinuto() {
        assertFalse(BaixaAutomaticaAgendador.deveExecutar(T.plusSeconds(30), T, 0));
        assertTrue(BaixaAutomaticaAgendador.deveExecutar(T.plusMinutes(1), T, 0));
    }
}
