package com.scivicslab.openmathlisp.symbols;

/** A projection target. Each symbol table row carries one rule (or nil) per target. */
public enum Target {
    /** LaTeX for humans and markdown. */
    LATEX(":latex"),
    /** Maxima input for the numeric check. */
    MAXIMA(":maxima"),
    /** SMT-LIB 2 input for Z3. */
    SMT(":smt");

    private final String keyword;

    Target(String keyword) {
        this.keyword = keyword;
    }

    /** @return the keyword naming this target in a symbol table row */
    public String keyword() {
        return keyword;
    }

    /**
     * Parses a target name.
     * @param name {@code latex}, {@code maxima} or {@code smt}
     * @return the target
     */
    public static Target fromName(String name) {
        return switch (name) {
            case "latex" -> LATEX;
            case "maxima" -> MAXIMA;
            case "smt" -> SMT;
            default -> throw new IllegalArgumentException("unknown target: " + name);
        };
    }
}
