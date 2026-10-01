package com.scivicslab.openmathlisp.sexp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads s-expressions from text. Whitespace separates tokens, {@code ;} starts a comment that
 * runs to the end of the line, strings are double-quoted with backslash escapes.
 */
public final class SexpReader {

    private final String text;
    private int position;

    private SexpReader(String text) {
        this.text = text;
        this.position = 0;
    }

    /**
     * Reads every top-level s-expression in the text.
     * @param text the source text
     * @return the expressions in order
     * @throws SexpSyntaxException when parentheses or strings are unbalanced
     */
    public static List<SExp> readAll(String text) {
        SexpReader reader = new SexpReader(text);
        List<SExp> result = new ArrayList<>();
        while (true) {
            reader.skipWhitespaceAndComments();
            if (reader.position >= reader.text.length()) {
                return result;
            }
            result.add(reader.readOne());
        }
    }

    /**
     * Reads exactly one s-expression from the text.
     * @param text the source text
     * @return the expression
     * @throws SexpSyntaxException when the text holds zero or more than one expression
     */
    public static SExp readOne(String text) {
        List<SExp> all = readAll(text);
        if (all.size() != 1) {
            throw new SexpSyntaxException("expected exactly one s-expression, found " + all.size(), 0);
        }
        return all.get(0);
    }

    private SExp readOne() {
        skipWhitespaceAndComments();
        if (position >= text.length()) {
            throw new SexpSyntaxException("unexpected end of input", position);
        }
        char c = text.charAt(position);
        if (c == '(') {
            return readList();
        }
        if (c == ')') {
            throw new SexpSyntaxException("unexpected ')'", position);
        }
        if (c == '"') {
            return readString();
        }
        return readAtom();
    }

    private SExp.SList readList() {
        int start = position;
        position++;
        List<SExp> items = new ArrayList<>();
        while (true) {
            skipWhitespaceAndComments();
            if (position >= text.length()) {
                throw new SexpSyntaxException("unclosed '(' opened here", start);
            }
            if (text.charAt(position) == ')') {
                position++;
                return new SExp.SList(items);
            }
            items.add(readOne());
        }
    }

    private SExp.SString readString() {
        int start = position;
        position++;
        StringBuilder value = new StringBuilder();
        while (true) {
            if (position >= text.length()) {
                throw new SexpSyntaxException("unclosed string opened here", start);
            }
            char c = text.charAt(position);
            if (c == '"') {
                position++;
                return new SExp.SString(value.toString());
            }
            if (c == '\\') {
                position++;
                if (position >= text.length()) {
                    throw new SexpSyntaxException("dangling backslash in string", position);
                }
                char escaped = text.charAt(position);
                switch (escaped) {
                    case 'n' -> value.append('\n');
                    case 't' -> value.append('\t');
                    default -> value.append(escaped);
                }
                position++;
                continue;
            }
            value.append(c);
            position++;
        }
    }

    private SExp readAtom() {
        int start = position;
        while (position < text.length()) {
            char c = text.charAt(position);
            if (Character.isWhitespace(c) || c == '(' || c == ')' || c == '"' || c == ';') {
                break;
            }
            position++;
        }
        String token = text.substring(start, position);
        if (token.matches("[+-]?[0-9]+")) {
            return new SExp.SInteger(new BigInteger(token));
        }
        if (token.matches("[+-]?[0-9]+\\.[0-9]*([eE][+-]?[0-9]+)?") || token.matches("[+-]?[0-9]+[eE][+-]?[0-9]+")) {
            return new SExp.SDecimal(token, Double.parseDouble(token));
        }
        return new SExp.SSymbol(token);
    }

    private void skipWhitespaceAndComments() {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == ';') {
                while (position < text.length() && text.charAt(position) != '\n') {
                    position++;
                }
            } else if (Character.isWhitespace(c)) {
                position++;
            } else {
                return;
            }
        }
    }
}
