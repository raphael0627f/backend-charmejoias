package com.manager.app.shared.exception;

import java.sql.SQLException;

public final class SqlErros {

    public static final String UNIQUE = "23505";
    public static final String FOREIGN_KEY = "23503";
    public static final String VALOR_LONGO = "22001";

    private SqlErros(){}

    // SQLState do primeiro SQLException da cadeia de causas (null se não houver)
    public static String sqlState(Throwable erro){
        for (Throwable t = erro; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
