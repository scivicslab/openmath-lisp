package com.scivicslab.openmathlisp.write;

import com.scivicslab.openmathlisp.symbols.InputFormat;

import java.util.Set;

/**
 * Renders variable names per format. In a name, {@code _} and {@code ^} introduce subscript and
 * superscript labels and a trailing {@code '} is a prime (TermGrammarAndSymbolTable_261002_oo01, decision 2).
 * Greek letters are spelled by their LaTeX command name without the backslash.
 */
public final class VariableNames {

    private static final Set<String> GREEK = Set.of(
            "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta", "theta", "vartheta",
            "iota", "kappa", "lambda", "mu", "nu", "xi", "pi", "varpi", "rho", "varrho", "sigma", "varsigma",
            "tau", "upsilon", "phi", "varphi", "chi", "psi", "omega",
            "Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma", "Upsilon", "Phi", "Psi", "Omega",
            "hbar", "ell", "partial", "nabla");

    private VariableNames() {
    }

    /**
     * Renders a variable name.
     * @param name the name as written in the term
     * @param format the format
     * @return the text
     */
    public static String render(String name, InputFormat format) {
        return switch (format) {
            case LATEX -> renderLatex(name);
            case MAXIMA, SMT -> renderIdentifier(name);
        };
    }

    /**
     * Tells whether a base name is a Greek letter command.
     * @param base the name without labels
     * @return true when LaTeX writes it with a backslash
     */
    public static boolean isGreek(String base) {
        return GREEK.contains(base);
    }

    static String renderLatex(String name) {
        int primes = 0;
        while (name.endsWith("'")) {
            primes++;
            name = name.substring(0, name.length() - 1);
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < name.length() && name.charAt(i) != '_' && name.charAt(i) != '^') {
            i++;
        }
        String base = name.substring(0, i);
        out.append(isGreek(base) ? "\\" + base : base);
        while (i < name.length()) {
            char marker = name.charAt(i);
            int j = i + 1;
            while (j < name.length() && name.charAt(j) != '_' && name.charAt(j) != '^') {
                j++;
            }
            String label = name.substring(i + 1, j);
            out.append(marker).append('{').append(isGreek(label) ? "\\" + label : label).append('}');
            i = j;
        }
        out.append("'".repeat(primes));
        return out.toString();
    }

    static String renderIdentifier(String name) {
        return name.replace("^", "_sup_").replace("'", "_prime");
    }
}
