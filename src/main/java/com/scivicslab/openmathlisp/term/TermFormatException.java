package com.scivicslab.openmathlisp.term;

/** Thrown when an s-expression is not a well-formed term. */
public final class TermFormatException extends RuntimeException {
    /**
     * Creates the exception.
     * @param message what is wrong with the expression
     */
    public TermFormatException(String message) {
        super(message);
    }
}
