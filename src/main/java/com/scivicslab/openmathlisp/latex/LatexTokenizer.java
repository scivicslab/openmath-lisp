package com.scivicslab.openmathlisp.latex;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Cuts LaTeX math text into tokens (LatexReaderDecisions_261002_oo01, decision 3). Spacing and sizing
 * commands are dropped here; {@code \left} and {@code \right} are dropped leaving their bracket.
 */
public final class LatexTokenizer {

    private static final Set<String> DROPPED = Set.of(",", ";", "!", ":", "quad", "qquad", "big", "Big", "bigg", "Bigg",
            "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr", "Biggl", "Biggr", "displaystyle", "textstyle", "nonumber",
            "limits", "nolimits", "mathrm", "mathit", "rm", "it", "thinspace", "negthinspace", "mkern", "mskip", "allowbreak");

    private LatexTokenizer() {
    }

    /**
     * Tokenizes the text.
     * @param text LaTeX math text without the {@code $$} delimiters
     * @return the tokens followed by one END token
     * @throws LatexReadException with reason {@code non-ascii} for characters outside ASCII
     */
    public static List<LatexToken> tokenize(String text) {
        List<LatexToken> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '~') {
                i++;
                continue;
            }
            if (c > 127) {
                throw new LatexReadException("non-ascii", "'" + c + "'", i);
            }
            if (c == '\\') {
                int start = i;
                i++;
                if (i >= text.length()) {
                    throw new LatexReadException("syntax", "dangling backslash", start);
                }
                char next = text.charAt(i);
                String name;
                if (Character.isLetter(next)) {
                    int j = i;
                    while (j < text.length() && Character.isLetter(text.charAt(j))) {
                        j++;
                    }
                    name = text.substring(i, j);
                    i = j;
                } else {
                    name = String.valueOf(next);
                    i++;
                }
                if (name.equals("left") || name.equals("right")) {
                    // keep the bracket that follows; "\left." and "\right." leave nothing
                    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                        i++;
                    }
                    if (i < text.length() && text.charAt(i) == '.') {
                        i++;
                    }
                    continue;
                }
                if (DROPPED.contains(name)) {
                    continue;
                }
                tokens.add(new LatexToken(LatexToken.Kind.COMMAND, name, start));
                continue;
            }
            if (Character.isLetter(c)) {
                tokens.add(new LatexToken(LatexToken.Kind.LETTER, String.valueOf(c), i));
                i++;
                continue;
            }
            if (Character.isDigit(c)) {
                int j = i;
                while (j < text.length() && Character.isDigit(text.charAt(j))) {
                    j++;
                }
                if (j < text.length() && text.charAt(j) == '.' && j + 1 < text.length() && Character.isDigit(text.charAt(j + 1))) {
                    j++;
                    while (j < text.length() && Character.isDigit(text.charAt(j))) {
                        j++;
                    }
                }
                tokens.add(new LatexToken(LatexToken.Kind.NUMBER, text.substring(i, j), i));
                i = j;
                continue;
            }
            tokens.add(new LatexToken(LatexToken.Kind.PUNCT, String.valueOf(c), i));
            i++;
        }
        tokens.add(new LatexToken(LatexToken.Kind.END, "", text.length()));
        return tokens;
    }
}
