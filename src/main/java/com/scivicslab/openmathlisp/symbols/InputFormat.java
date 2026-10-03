package com.scivicslab.openmathlisp.symbols;

/** An input format a term is written as: LaTeX, Maxima input or SMT-LIB input. Each symbol table row carries one rule (or nil) per format. */
public enum InputFormat {
    /** LaTeX for humans and markdown. */
    LATEX(":latex"),
    /** Maxima input for the numeric check. */
    MAXIMA(":maxima"),
    /** SMT-LIB 2 input for Z3. */
    SMT(":smt");

    private final String keyword;

    InputFormat(String keyword) {
        this.keyword = keyword;
    }

    /** @return the keyword naming this format in a symbol table row */
    public String keyword() {
        return keyword;
    }

    /**
     * Parses a format name.
     * @param name {@code latex}, {@code maxima} or {@code smt}
     * @return the format
     */
    public static InputFormat fromName(String name) {
        return switch (name) {
            case "latex" -> LATEX;
            case "maxima" -> MAXIMA;
            case "smt" -> SMT;
            default -> throw new IllegalArgumentException("unknown format: " + name);
        };
    }
}
