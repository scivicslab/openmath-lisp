package com.scivicslab.openmathlisp.markdown;

import com.scivicslab.openmathlisp.record.Declaration;
import com.scivicslab.openmathlisp.term.Term;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a markdown file and proposes the declaration it is missing.
 *
 * <p>Three of the six declaration keys follow from what the document itself shows, so they are proposed:
 * {@code :time} when a dot stands over a quantity, {@code :coordinates} when a dot stands in the denominator
 * of a partial derivative, and {@code :functions} when a quantity is differentiated with respect to a
 * variable it does not contain. The other two, {@code :euler} and {@code :imaginary}, say which letter means
 * what in this chapter; the text cannot settle that, so the evidence for each is counted and left to a
 * person (see {@link #evidence}).</p>
 */
public final class DeclarationSuggester {

    private static final Pattern DOT_IN_PARTIAL = Pattern.compile("\\\\partial\\s*\\\\d?dot\\{\\\\?([A-Za-z]+)\\}");
    private static final Pattern DOT = Pattern.compile("\\\\d?dot\\{\\\\?([A-Za-z]+)\\}");
    // a letter right before the e is a multiplication (Ae^{kx}), so only a backslash excludes it
    private static final Pattern EULER_BASE = Pattern.compile("(?<!\\\\)e\\s*\\^\\s*(\\{[^}]*\\}|[A-Za-z])");
    // the charge squared: the exponent is exactly 2, so e^{2\\pi i \\nu t} is not one of these
    private static final Pattern CHARGE = Pattern.compile(
            "(?<!\\\\)e\\s*\\^\\s*(?:\\{\\s*2\\s*\\}|2(?![0-9A-Za-z\\\\]))");
    private static final Pattern IMAGINARY_IN_EXPONENT = Pattern.compile("\\^\\s*\\{[^}]*(?<![A-Za-z])i(?![A-Za-z])[^}]*\\}");
    private static final Pattern I_AS_SOMETHING_ELSE = Pattern.compile(
            "(?<![A-Za-z])i\\s*\\\\(times|cdot)|\\\\(times|cdot)\\s*i(?![A-Za-z])|\\\\sin\\s*i(?![A-Za-z])"
            + "|\\\\cos\\s*i(?![A-Za-z])|\\\\tan\\s*i(?![A-Za-z])|_\\{?i\\}?(?![A-Za-z])");

    private DeclarationSuggester() {
    }

    /** How often each letter is used in each sense, for the two keys a person has to settle. */
    public record Evidence(int eulerBase, int chargeOrSquare, int imaginaryInExponent, int iAsSomethingElse) {
        /** @return true when {@code e} is used as the base of a power and never squared */
        public boolean eulerIsClear() {
            return eulerBase > 0 && chargeOrSquare == 0;
        }

        /** @return true when {@code i} appears in exponents and never as a unit vector, angle or index */
        public boolean imaginaryIsClear() {
            return imaginaryInExponent > 0 && iAsSomethingElse == 0;
        }
    }

    /**
     * Counts how the letters {@code e} and {@code i} are used in a markdown text.
     *
     * @param markdown the text of a markdown file
     * @return the counts
     */
    public static Evidence evidence(String markdown) {
        int base = 0;
        Matcher m = EULER_BASE.matcher(markdown);
        while (m.find()) {
            if (!m.group(1).replaceAll("[{}\\s]", "").matches("-?\\d+")) {
                base++;
            }
        }
        return new Evidence(base, count(CHARGE, markdown), count(IMAGINARY_IN_EXPONENT, markdown),
                count(I_AS_SOMETHING_ELSE, markdown));
    }

    private static int count(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /**
     * Proposes the declaration for a markdown text, keeping whatever its current declaration already says.
     *
     * @param markdown the text of a markdown file
     * @param document the same text parsed, for the terms its om blocks hold
     * @return the proposed declaration
     */
    public static Declaration suggest(String markdown, MarkdownDocument document) {
        Declaration current = document.declaration();
        Set<String> coordinates = new LinkedHashSet<>(current.coordinates());
        Matcher partial = DOT_IN_PARTIAL.matcher(markdown);
        while (partial.find()) {
            coordinates.add(partial.group(1));
        }
        String time = current.time().orElse(DOT.matcher(markdown).find() ? "t" : null);

        // What the document already declares has to stay: once a dependence is declared the document no
        // longer shows it, so dropping it would make the same formulas unreadable again.
        Map<String, List<String>> functions = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : current.functions().entrySet()) {
            functions.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        for (Term term : document.equations("suggest").values()) {
            collectDependencies(term, functions);
        }
        Map<String, List<String>> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : functions.entrySet()) {
            String name = entry.getKey();
            if (coordinates.contains(name) || entry.getValue().isEmpty() || !isPlainName(name)) {
                continue;
            }
            List<String> arguments = new ArrayList<>();
            for (String argument : entry.getValue()) {
                // an argument has to be a variable in its own right: not the quantity itself, not something
                // the document calls a function of other things, and not a dotted name
                if (!argument.equals(name) && isPlainName(argument) && !functions.containsKey(argument)) {
                    arguments.add(argument);
                }
            }
            if (!arguments.isEmpty()) {
                ordered.put(name, arguments);
            }
        }
        return new Declaration(time, current.imaginary().orElse(null), current.euler().orElse(null),
                current.vectors(), coordinates, ordered);
    }

    /** A name a declaration can use: letters, digits and underscores, and not one a dot produced. */
    private static boolean isPlainName(String name) {
        return name.matches("[A-Za-z][A-Za-z0-9_]*") && !name.contains("dot");
    }

    /**
     * Finds every quantity that is differentiated with respect to variables it does not contain, and
     * records those variables as the ones it depends on. A quantity differentiated by {@code x}, by
     * {@code y} and by {@code z} in the same document depends on all three.
     */
    private static void collectDependencies(Term term, Map<String, List<String>> functions) {
        switch (term) {
            case Term.BindingTerm binding -> {
                if (binding.body() instanceof Term.VariableTerm dependent) {
                    List<String> arguments = functions.computeIfAbsent(dependent.name(), (String k) -> new ArrayList<>());
                    for (Term.VariableTerm bound : binding.variables()) {
                        if (!arguments.contains(bound.name())) {
                            arguments.add(bound.name());
                        }
                    }
                }
                collectDependencies(binding.body(), functions);
            }
            case Term.ApplicationTerm application -> {
                for (Term argument : application.args()) {
                    collectDependencies(argument, functions);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Writes the proposed declaration as the om block that belongs right after the front matter, with the
     * evidence for {@code :euler} and {@code :imaginary} as comments.
     *
     * @param declaration the proposed declaration
     * @param evidence the counts for the two keys a person has to settle
     * @return the text of an om block, ending in a newline
     */
    public static String asOmBlock(Declaration declaration, Evidence evidence) {
        StringBuilder out = new StringBuilder("```om\n");
        out.append(declaration).append('\n');
        out.append("; e as the base of a power: ").append(evidence.eulerBase())
                .append(", e squared or as a factor: ").append(evidence.chargeOrSquare())
                .append(evidence.eulerIsClear() ? "  -> :euler e" : "  -> leave e undeclared").append('\n');
        out.append("; i in an exponent: ").append(evidence.imaginaryInExponent())
                .append(", i as a unit vector, angle or index: ").append(evidence.iAsSomethingElse())
                .append(evidence.imaginaryIsClear() ? "  -> :imaginary i" : "  -> leave i undeclared").append('\n');
        return out.append("```\n").toString();
    }

    /**
     * Gives the declaration with {@code :euler} and {@code :imaginary} filled in where the evidence is
     * one-sided.
     *
     * @param declaration the proposed declaration
     * @param evidence the counts
     * @return the declaration with the two letters settled where they can be
     */
    public static Declaration withSettledLetters(Declaration declaration, Evidence evidence) {
        return new Declaration(declaration.time().orElse(null),
                evidence.imaginaryIsClear() ? "i" : declaration.imaginary().orElse(null),
                evidence.eulerIsClear() ? "e" : declaration.euler().orElse(null),
                declaration.vectors(), declaration.coordinates(), declaration.functions());
    }

}
