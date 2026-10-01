package com.scivicslab.openmathlisp.sexp;

/** Thrown when text cannot be read as s-expressions. */
public final class SexpSyntaxException extends RuntimeException {

    private final int position;

    /**
     * Creates the exception.
     * @param message what went wrong
     * @param position the character offset in the input where it went wrong
     */
    public SexpSyntaxException(String message, int position) {
        super(message + " (at offset " + position + ")");
        this.position = position;
    }

    /** @return the character offset in the input */
    public int getPosition() {
        return position;
    }
}
