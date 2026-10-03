package com.scivicslab.openmathlisp.write;

/** Thrown when a term cannot be written for a format. */
public final class TermWriterException extends RuntimeException {
    /**
     * Creates the exception.
     * @param message which symbol or form the format cannot express
     */
    public TermWriterException(String message) {
        super(message);
    }
}
