package com.scivicslab.openmathlisp.check;

import com.scivicslab.openmathlisp.write.TermWriterException;
import com.scivicslab.openmathlisp.write.TermWriter;
import com.scivicslab.openmathlisp.write.VariableNames;
import com.scivicslab.openmathlisp.symbols.InputFormat;
import com.scivicslab.openmathlisp.term.Term;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The numeric check (TermFileAndCheckRecord_261002_oo01, decision 3): Maxima evaluates {@code L - R} for
 * three sets of integers from 2 to 9 chosen by a random generator seeded with the equation id; every
 * equation of a file goes into one Maxima script. An equation whose sides each hold a variable the other
 * lacks defines or relates quantities rather than stating an identity, and is not checkable by substitution.
 */
public final class NumericChecker {

    /** Result of the numeric check of one equation. */
    public sealed interface Result permits Ok, Failed, NotCheckable {
    }

    /** All three substitutions gave a difference below the tolerance. */
    public record Ok() implements Result {
    }

    /** A substitution gave a difference at or above the tolerance; carries the simplified difference. */
    public record Failed(String difference) implements Result {
    }

    /** Maxima did not return numbers (unknown functions, unevaluated integrals, errors). */
    public record NotCheckable(String why) implements Result {
    }

    private static final double TOLERANCE = 1e-9;
    private static final int SAMPLES = 3;
    private static final Pattern NUMBER = Pattern.compile("^-?[0-9]+(\\.[0-9]*)?([eE][+-]?[0-9]+)?$");
    private static final String MARKER = "### ";

    private final TermWriter writer;
    private final Function<String, String> maxima;

    /**
     * Creates the checker.
     * @param writer the writer for the Maxima format
     * @param maxima runs a Maxima script and returns its output (null when Maxima is unavailable)
     */
    public NumericChecker(TermWriter writer, Function<String, String> maxima) {
        this.writer = writer;
        this.maxima = maxima;
    }

    /**
     * Runs Maxima once for all equations.
     * @param equations id to term, in order
     * @return id to result, in the same order
     */
    public Map<String, Result> check(Map<String, Term> equations) {
        Map<String, Result> results = new java.util.LinkedHashMap<>();
        StringBuilder script = new StringBuilder("display2d:false$\nratprint:false$\n");
        List<String> scripted = new ArrayList<>();
        for (Map.Entry<String, Term> entry : equations.entrySet()) {
            String id = entry.getKey();
            Term term = entry.getValue();
            if (!(term instanceof Term.ApplicationTerm application) || !(application.head() instanceof Term.SymbolTerm head)
                    || !head.qualifiedName().equals("relation1:eq") || application.args().size() != 2) {
                results.put(id, new NotCheckable("not an equation"));
                continue;
            }
            Set<String> leftVariables = freeVariables(application.args().get(0));
            Set<String> rightVariables = freeVariables(application.args().get(1));
            if (!leftVariables.containsAll(rightVariables) && !rightVariables.containsAll(leftVariables)) {
                results.put(id, new NotCheckable("each side has a variable the other lacks"));
                continue;
            }
            if (isConstantSideRelation(application)) {
                results.put(id, new NotCheckable("one side is a number and the other has variables"));
                continue;
            }
            String difference;
            try {
                difference = "(" + writer.write(application.args().get(0), InputFormat.MAXIMA) + ") - ("
                        + writer.write(application.args().get(1), InputFormat.MAXIMA) + ")";
            } catch (TermWriterException e) {
                results.put(id, new NotCheckable(e.getMessage()));
                continue;
            }
            Set<String> variables = freeVariables(term);
            Random random = new Random(id.hashCode());
            script.append("print(\"").append(MARKER).append(id).append("\")$\n");
            for (int sample = 0; sample < SAMPLES; sample++) {
                StringBuilder substitution = new StringBuilder();
                for (String variable : variables) {
                    substitution.append(", ").append(VariableNames.render(variable, InputFormat.MAXIMA)).append("=").append(2 + random.nextInt(8));
                }
                script.append("print(float(ev(").append(difference).append(substitution).append(")))$\n");
            }
            script.append("print(ratsimp(").append(difference).append("))$\n");
            scripted.add(id);
            results.put(id, null);
        }
        if (scripted.isEmpty()) {
            return results;
        }
        String output = maxima.apply(script.toString());
        if (output == null) {
            for (String id : scripted) {
                results.put(id, new NotCheckable("maxima unavailable or timed out"));
            }
            return results;
        }
        Map<String, List<String>> sections = sections(output);
        for (String id : scripted) {
            results.put(id, interpret(sections.get(id)));
        }
        return results;
    }

    private static Map<String, List<String>> sections(String output) {
        Map<String, List<String>> sections = new java.util.LinkedHashMap<>();
        List<String> current = null;
        for (String rawLine : output.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith(MARKER)) {
                current = new ArrayList<>();
                sections.put(line.substring(MARKER.length()).trim(), current);
            } else if (current != null && !line.isEmpty()) {
                current.add(line);
            }
        }
        return sections;
    }

    private static Result interpret(List<String> lines) {
        if (lines == null) {
            return new NotCheckable("no output from maxima");
        }
        // the first SAMPLES lines after the marker must each be one number; an unevaluated expression
        // (which Maxima may wrap over several lines) makes the equation not checkable
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < SAMPLES && i < lines.size(); i++) {
            String compact = lines.get(i).replace(" ", "");
            if (!NUMBER.matcher(compact).matches()) {
                return new NotCheckable(lines.get(i));
            }
            values.add(Double.parseDouble(compact));
        }
        if (values.size() < SAMPLES) {
            return new NotCheckable(lines.isEmpty() ? "no value" : lines.get(0));
        }
        for (Double value : values) {
            if (Double.isNaN(value) || Math.abs(value) >= TOLERANCE) {
                return new Failed(lines.get(lines.size() - 1));
            }
        }
        return new Ok();
    }

    /**
     * True for {@code expr = 0}-like equations: one side is a bare number and the other has free variables.
     * These state a condition between quantities, not an identity, so substitution cannot judge them.
     * @param equation a relation1:eq application
     * @return true when substitution is meaningless
     */
    public static boolean isConstantSideRelation(Term.ApplicationTerm equation) {
        Term left = equation.args().get(0);
        Term right = equation.args().get(1);
        boolean leftNumber = left instanceof Term.IntegerTerm || left instanceof Term.DecimalTerm;
        boolean rightNumber = right instanceof Term.IntegerTerm || right instanceof Term.DecimalTerm;
        return (leftNumber && !freeVariables(right).isEmpty()) || (rightNumber && !freeVariables(left).isEmpty());
    }

    /** Variables that are not bound and are not applied as functions. */
    static Set<String> freeVariables(Term term) {
        Set<String> free = new LinkedHashSet<>();
        collect(term, new LinkedHashSet<>(), free);
        return free;
    }

    private static void collect(Term term, Set<String> bound, Set<String> free) {
        switch (term) {
            case Term.VariableTerm variable -> {
                if (!bound.contains(variable.name())) {
                    free.add(variable.name());
                }
            }
            case Term.ApplicationTerm application -> {
                if (!(application.head() instanceof Term.VariableTerm)) {
                    collect(application.head(), bound, free);
                }
                for (Term arg : application.args()) {
                    collect(arg, bound, free);
                }
            }
            case Term.BindingTerm binding -> {
                Set<String> inner = new LinkedHashSet<>(bound);
                for (Term.VariableTerm variable : binding.variables()) {
                    inner.add(variable.name());
                }
                collect(binding.body(), inner, free);
            }
            default -> {
            }
        }
    }
}
