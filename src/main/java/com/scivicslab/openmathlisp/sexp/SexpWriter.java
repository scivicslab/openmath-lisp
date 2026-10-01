package com.scivicslab.openmathlisp.sexp;

/**
 * Writes s-expressions back to text. Short lists go on one line; a list whose one-line form
 * exceeds the width is broken after its head, one element per line.
 */
public final class SexpWriter {

    private static final int WIDTH = 100;

    private SexpWriter() {
    }

    /**
     * Writes the expression on one line.
     * @param expression the expression
     * @return the text
     */
    public static String writeFlat(SExp expression) {
        StringBuilder out = new StringBuilder();
        appendFlat(out, expression);
        return out.toString();
    }

    /**
     * Writes the expression with indentation.
     * @param expression the expression
     * @return the text, without a trailing newline
     */
    public static String writePretty(SExp expression) {
        StringBuilder out = new StringBuilder();
        appendPretty(out, expression, 0);
        return out.toString();
    }

    /**
     * Quotes a string with Lisp escapes.
     * @param value the raw string
     * @return the quoted literal
     */
    public static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static void appendFlat(StringBuilder out, SExp expression) {
        switch (expression) {
            case SExp.SList list -> {
                out.append('(');
                for (int i = 0; i < list.items().size(); i++) {
                    if (i > 0) {
                        out.append(' ');
                    }
                    appendFlat(out, list.items().get(i));
                }
                out.append(')');
            }
            case SExp.SSymbol symbol -> out.append(symbol.name());
            case SExp.SString string -> out.append(quote(string.value()));
            case SExp.SInteger integer -> out.append(integer.value());
            case SExp.SDecimal decimal -> out.append(decimal.literal());
        }
    }

    private static void appendPretty(StringBuilder out, SExp expression, int indent) {
        String flat = writeFlat(expression);
        if (!(expression instanceof SExp.SList list) || flat.length() + indent <= WIDTH || list.items().isEmpty()) {
            out.append(flat);
            return;
        }
        out.append('(');
        appendPretty(out, list.items().get(0), indent + 1);
        // keyword/value pairs stay on one line each: (:key value)
        int i = 1;
        while (i < list.items().size()) {
            SExp item = list.items().get(i);
            out.append('\n').append(" ".repeat(indent + 2));
            if (item instanceof SExp.SSymbol symbol && symbol.isKeyword() && i + 1 < list.items().size()) {
                out.append(symbol.name()).append(' ');
                appendPretty(out, list.items().get(i + 1), indent + 2 + symbol.name().length() + 1);
                i += 2;
            } else {
                appendPretty(out, item, indent + 2);
                i++;
            }
        }
        out.append(')');
    }
}
