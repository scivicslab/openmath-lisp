package com.scivicslab.openmathlisp.symbols;

/** The role of a symbol as declared in its OpenMath content dictionary. */
public enum Role {
    /** Heads an application {@code (symbol args...)}. */
    APPLICATION,
    /** Heads a binding {@code (symbol (vars...) body)}. */
    BINDER,
    /** Heads an attribution {@code (symbol value attributed)}. */
    ATTRIBUTION,
    /** Stands alone. */
    CONSTANT;

    /**
     * Parses the role name used in the dictionaries and in the symbol table.
     * @param name {@code application}, {@code binder} or {@code constant}
     * @return the role
     */
    public static Role fromName(String name) {
        return switch (name) {
            case "application" -> APPLICATION;
            case "binder" -> BINDER;
            case "attribution" -> ATTRIBUTION;
            case "constant" -> CONSTANT;
            default -> throw new IllegalArgumentException("unknown role: " + name);
        };
    }
}
