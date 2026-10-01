package com.scivicslab.openmathlisp.project;

/** Thrown when a term cannot be written for a target. */
public final class ProjectionException extends RuntimeException {
    /**
     * Creates the exception.
     * @param message which symbol or form the target cannot express
     */
    public ProjectionException(String message) {
        super(message);
    }
}
