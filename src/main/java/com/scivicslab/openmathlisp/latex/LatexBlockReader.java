package com.scivicslab.openmathlisp.latex;

import com.scivicslab.openmathlisp.record.Declaration;
import com.scivicslab.openmathlisp.term.Term;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one {@code $$ ... $$} block (LatexReaderDecisions_261002_oo01, decision 1): takes the tag, splits
 * {@code split}/{@code aligned} lines at {@code \\}, drops {@code &}, joins continuation lines to the previous
 * right-hand side, and splits relation chains into pairs.
 */
public final class LatexBlockReader {

    private static final Pattern TAG = Pattern.compile("\\\\tag\\*?\\{([^}]*)\\}");
    private static final Pattern TRAILING_NUMBER = Pattern.compile("[\\s\\t]*\\((\\d+[a-z]?)\\)\\s*$");
    private static final Pattern ENVIRONMENT = Pattern.compile("\\\\(begin|end)\\{(split|aligned|align\\*?|gather\\*?|multline\\*?|gathered)\\}");
    private static final Pattern LABEL = Pattern.compile("\\\\label\\{[^}]*\\}");

    private LatexBlockReader() {
    }

    /** One equation read from a block, or the reason it could not be read. */
    public record EquationResult(String source, Optional<Term> term, Optional<LatexReadException> failure) {
        /**
         * Creates a successful result.
         * @param source the unit text the term came from
         * @param term the term
         * @return the result
         */
        public static EquationResult of(String source, Term term) {
            return new EquationResult(source, Optional.of(term), Optional.empty());
        }

        /**
         * Creates a failed result.
         * @param source the unit text
         * @param failure why it could not be read
         * @return the result
         */
        public static EquationResult failed(String source, LatexReadException failure) {
            return new EquationResult(source, Optional.empty(), Optional.of(failure));
        }
    }

    /** What a block yields: its tag and its equations in order. */
    public record BlockResult(Optional<String> tag, List<EquationResult> equations) {
    }

    /** Splits at {@code \\\\} and at commas outside braces, parentheses and brackets. */
    static List<String> splitUnits(String text) {
        List<String> units = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && text.charAt(i + 1) == '\\') {
                units.add(current.toString());
                current.setLength(0);
                i += 2;
                continue;
            }
            if (c == '\\' && i + 1 < text.length()) {
                // keep escaped braces and other commands out of the depth count
                current.append(c).append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '{' || c == '(' || c == '[') {
                depth++;
            } else if (c == '}' || c == ')' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                units.add(current.toString());
                current.setLength(0);
                i++;
                continue;
            }
            current.append(c);
            i++;
        }
        units.add(current.toString());
        return units;
    }

    /**
     * Reads a block.
     * @param block the text between the {@code $$} delimiters
     * @param declaration the file's declaration
     * @return the equations
     */
    public static BlockResult read(String block, Declaration declaration) {
        String text = block;
        Optional<String> tag = Optional.empty();
        Matcher tagMatcher = TAG.matcher(text);
        if (tagMatcher.find()) {
            tag = Optional.of(tagMatcher.group(1));
            text = tagMatcher.replaceAll("");
        }
        Matcher numberMatcher = TRAILING_NUMBER.matcher(text);
        if (numberMatcher.find()) {
            if (tag.isEmpty()) {
                tag = Optional.of(numberMatcher.group(1));
            }
            text = numberMatcher.replaceAll("");
        }
        text = LABEL.matcher(text).replaceAll("");
        text = ENVIRONMENT.matcher(text).replaceAll("");
        List<EquationResult> equations = new ArrayList<>();
        Term previousRight = null;
        for (String rawUnit : splitUnits(text)) {
            String unit = rawUnit.replace("&", "").trim();
            while (unit.endsWith(",") || unit.endsWith(".") || unit.endsWith(";")) {
                unit = unit.substring(0, unit.length() - 1).trim();
            }
            if (unit.isEmpty()) {
                continue;
            }
            try {
                LatexParser.RelationChain chain;
                if (previousRight != null && LatexParser.startsWithRelation(unit)) {
                    chain = LatexParser.parseContinuation(previousRight, unit, declaration);
                } else {
                    chain = LatexParser.parse(unit, declaration);
                }
                if (chain.relations().isEmpty()) {
                    equations.add(EquationResult.of(unit, chain.operands().get(0)));
                } else {
                    for (int i = 0; i < chain.relations().size(); i++) {
                        Term equation = new Term.ApplicationTerm(chain.relations().get(i),
                                List.of(chain.operands().get(i), chain.operands().get(i + 1)));
                        equations.add(EquationResult.of(unit, equation));
                    }
                }
                previousRight = chain.operands().get(chain.operands().size() - 1);
            } catch (LatexReadException e) {
                if (e.getReason().equals("empty")) {
                    continue;
                }
                equations.add(EquationResult.failed(unit, e));
                previousRight = null;
            }
        }
        return new BlockResult(tag, equations);
    }
}
