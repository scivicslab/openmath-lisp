package com.scivicslab.openmathlisp.markdown;

import com.scivicslab.openmathlisp.term.Term;

import java.util.Optional;

/**
 * One piece of a markdown file as seen by openmath-lisp. Concatenating the pieces in order reproduces
 * the file. The pieces that hold mathematics are the om block and the om span (the formula source), the
 * LaTeX block and span that conversion could not read, and the marker that records why.
 */
public sealed interface MarkdownPiece
        permits MarkdownPiece.Text, MarkdownPiece.OmBlock, MarkdownPiece.OmSpan, MarkdownPiece.OmError,
                MarkdownPiece.LatexBlock, MarkdownPiece.LatexSpan, MarkdownPiece.UnreadableMarker {

    /** The text of this piece as it stands in the file. */
    String raw();

    /** Markdown that holds no mathematics. */
    record Text(String raw) implements MarkdownPiece {
    }

    /**
     * A fenced code block with the info string {@code om}: the source of one display formula, or the
     * file's declaration.
     * @param raw the block including its fences
     * @param id the value of {@code id=} in the info string, if present
     * @param tag the value of {@code tag=} in the info string: the equation number printed in the book
     * @param source the s-expression text inside the fences
     * @param term the term, empty when the s-expression is the declaration
     */
    record OmBlock(String raw, Optional<String> id, Optional<String> tag, String source, Optional<Term> term) implements MarkdownPiece {
        /** @return true when this block holds {@code (declare ...)} rather than a formula */
        public boolean isDeclaration() {
            return term.isEmpty();
        }
    }

    /**
     * A code span starting with {@code om:}: the source of one inline formula.
     * @param raw the span including its backticks
     * @param source the text after {@code om:}
     * @param term the term
     */
    record OmSpan(String raw, String source, Term term) implements MarkdownPiece {
    }

    /**
     * An om block or om span whose s-expression is not a well-formed term. The text is kept as it stands so
     * that writing reproduces the file; the checks report it as unparseable.
     * @param raw the block or span including its delimiters
     * @param id the value of {@code id=} in the info string, if present
     * @param source the s-expression text
     * @param message what is wrong with it
     */
    record OmError(String raw, Optional<String> id, String source, String message) implements MarkdownPiece {
    }

    /**
     * A {@code $$ ... $$} block that conversion left as LaTeX.
     * @param raw the block including its delimiters
     * @param latex the text between the delimiters
     */
    record LatexBlock(String raw, String latex) implements MarkdownPiece {
    }

    /**
     * A {@code $ ... $} span that conversion left as LaTeX.
     * @param raw the span including its delimiters
     * @param latex the text between the delimiters
     */
    record LatexSpan(String raw, String latex) implements MarkdownPiece {
    }

    /**
     * The comment that conversion writes before a LaTeX block it could not read.
     * @param raw the comment text, with the trailing newline when it has a line of its own
     * @param id the identifier the block would have had
     * @param reason the reason code from the LaTeX reader
     */
    record UnreadableMarker(String raw, String id, String reason) implements MarkdownPiece {

        /**
         * Whether the formula this marker belongs to is an inline one. A display formula's identifier ends
         * with the display unit it came from, an inline one's with the inline formula it came from, and an
         * inline marker sits inside a line while a display marker has a line of its own.
         * @return true when the identifier names an inline formula
         */
        public boolean isInline() {
            return id.matches(".*-in\\d+$");
        }
    }
}
