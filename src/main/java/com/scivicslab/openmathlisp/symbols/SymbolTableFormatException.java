package com.scivicslab.openmathlisp.symbols;

/** Thrown when a symbol table row is malformed. */
public final class SymbolTableFormatException extends RuntimeException {
    /**
     * Creates the exception.
     * @param message what is wrong with the row
     */
    public SymbolTableFormatException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     * @param message what is wrong with the row
     * @param cause the underlying failure
     */
    public SymbolTableFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
