package com.scivicslab.openmathlisp.latex;

/**
 * Thrown when LaTeX text cannot be read as a term. The reason is one of the codes of
 * LatexReaderDecisions_261002_oo01, decision 7; {@code syntax} means the text itself is broken.
 */
public final class LatexReadException extends RuntimeException {

    private final String reason;
    private final int position;

    /**
     * Creates the exception.
     * @param reason the reason code
     * @param detail what was found
     * @param position the character offset in the source
     */
    public LatexReadException(String reason, String detail, int position) {
        super(reason + ": " + detail + " (at offset " + position + ")");
        this.reason = reason;
        this.position = position;
    }

    /** @return the reason code */
    public String getReason() {
        return reason;
    }

    /** @return the character offset in the source */
    public int getPosition() {
        return position;
    }
}
