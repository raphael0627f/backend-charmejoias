package com.manager.app.shared;

// controla onde o termo digitado precisa bater dentro do campo buscado (LIKE) -
// usado por todo filtro "busca" de listagem que passa por SpecificationUtils.likeIgnoreCase
public enum ModoBusca {
    INICIO,
    MEIO,
    FIM,
    EXATA
}
