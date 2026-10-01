package com.scivicslab.openmathlisp.latex;

/**
 * A token of LaTeX math text.
 * @param kind the kind
 * @param text the command name without backslash, the letter, the digits, or the punctuation character
 * @param position the character offset in the source
 */
public record LatexToken(Kind kind, String text, int position) {

    /** Token kinds: command, single letter, number, punctuation. */
    public enum Kind { COMMAND, LETTER, NUMBER, PUNCT, END }

    /**
     * Tells whether this token is the punctuation given.
     * @param punct the character
     * @return true when it matches
     */
    public boolean isPunct(String punct) {
        return kind == Kind.PUNCT && text.equals(punct);
    }

    /**
     * Tells whether this token is the command given.
     * @param name the command name without backslash
     * @return true when it matches
     */
    public boolean isCommand(String name) {
        return kind == Kind.COMMAND && text.equals(name);
    }
}
