package com.zynyhero.zpizzatpa;

import java.util.UUID;

public record TpaRequest(UUID sender, UUID target, Type type, long expiresAt) {

    public enum Type {
        /** El que envía quiere ir hacia el receptor. */
        TPA,
        /** El que envía quiere que el receptor vaya hacia él. */
        TPAHERE,
        /** Igual que TPAHERE, pero lanzada a todos (/tpaall). */
        TPAALL
    }
}
