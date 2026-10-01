package com.scivicslab.openmathlisp.check;

import com.scivicslab.openmathlisp.project.ProjectionException;
import com.scivicslab.openmathlisp.project.Projector;
import com.scivicslab.openmathlisp.project.VariableNames;
import com.scivicslab.openmathlisp.symbols.Target;
import com.scivicslab.openmathlisp.term.Term;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The SMT check (TermFileAndCheckRecord_261002_oo01, decision 4): only polynomial equations are sent to Z3,
 * as the negation of the equation with every variable declared {@code Real} and denominators asserted non-zero.
 */
public final class SmtChecker {

    private static final Set<String> POLYNOMIAL_SYMBOLS = Set.of("relation1:eq", "arith1:plus", "arith1:minus",
            "arith1:unary_minus", "arith1:times", "arith1:divide", "arith1:power");

    private final Projector projector;
    private final Function<String, String> z3;

    /**
     * Creates the checker.
     * @param projector the projector for the SMT target
     * @param z3 runs Z3 on SMT-LIB text and returns its output (null when unavailable)
     */
    public SmtChecker(Projector projector, Function<String, String> z3) {
        this.projector = projector;
        this.z3 = z3;
    }

    /**
     * Runs Z3 once for all equations; non-polynomial equations get {@code :not-checkable} without running.
     * @param equations id to term
     * @return id to {@code :sat}, {@code :unsat} or {@code :not-checkable}
     */
    public Map<String, String> check(Map<String, Term> equations) {
        Map<String, String> results = new LinkedHashMap<>();
        StringBuilder script = new StringBuilder();
        List<String> scripted = new ArrayList<>();
        for (Map.Entry<String, Term> entry : equations.entrySet()) {
            Term term = entry.getValue();
            if (!isPolynomialEquation(term)) {
                results.put(entry.getKey(), ":not-checkable");
                continue;
            }
            Term.ApplicationTerm equation = (Term.ApplicationTerm) term;
            Set<String> leftVariables = NumericChecker.freeVariables(equation.args().get(0));
            Set<String> rightVariables = NumericChecker.freeVariables(equation.args().get(1));
            if ((!leftVariables.containsAll(rightVariables) && !rightVariables.containsAll(leftVariables))
                    || NumericChecker.isConstantSideRelation(equation)) {
                results.put(entry.getKey(), ":not-checkable");
                continue;
            }
            try {
                script.append("(push)\n");
                for (String variable : NumericChecker.freeVariables(term)) {
                    script.append("(declare-const ").append(VariableNames.render(variable, Target.SMT)).append(" Real)\n");
                }
                List<Term> denominators = new ArrayList<>();
                collectDenominators(term, denominators);
                for (Term denominator : denominators) {
                    script.append("(assert (not (= ").append(projector.project(denominator, Target.SMT)).append(" 0)))\n");
                }
                script.append("(assert (not (= ").append(projector.project(equation.args().get(0), Target.SMT)).append(' ')
                        .append(projector.project(equation.args().get(1), Target.SMT)).append(")))\n(check-sat)\n(pop)\n");
                scripted.add(entry.getKey());
                results.put(entry.getKey(), null);
            } catch (ProjectionException e) {
                results.put(entry.getKey(), ":not-checkable");
            }
        }
        if (scripted.isEmpty()) {
            return results;
        }
        String output = z3.apply(script.toString());
        List<String> answers = new ArrayList<>();
        if (output != null) {
            for (String line : output.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.equals("sat") || trimmed.equals("unsat") || trimmed.equals("unknown")) {
                    answers.add(trimmed);
                }
            }
        }
        for (int i = 0; i < scripted.size(); i++) {
            String answer = i < answers.size() ? answers.get(i) : "unknown";
            results.put(scripted.get(i), answer.equals("unknown") ? ":not-checkable" : ":" + answer);
        }
        return results;
    }

    static boolean isPolynomialEquation(Term term) {
        if (!(term instanceof Term.ApplicationTerm application) || !(application.head() instanceof Term.SymbolTerm head)
                || !head.qualifiedName().equals("relation1:eq")) {
            return false;
        }
        return isPolynomial(term);
    }

    private static boolean isPolynomial(Term term) {
        switch (term) {
            case Term.IntegerTerm integer -> {
                return true;
            }
            case Term.DecimalTerm decimal -> {
                return true;
            }
            case Term.VariableTerm variable -> {
                return true;
            }
            case Term.SymbolTerm symbol -> {
                return false;
            }
            case Term.BindingTerm binding -> {
                return false;
            }
            case Term.ApplicationTerm application -> {
                if (!(application.head() instanceof Term.SymbolTerm head) || !POLYNOMIAL_SYMBOLS.contains(head.qualifiedName())) {
                    return false;
                }
                if (head.qualifiedName().equals("arith1:power")) {
                    if (!(application.args().get(1) instanceof Term.IntegerTerm exponent) || exponent.value().signum() < 0) {
                        return false;
                    }
                    return isPolynomial(application.args().get(0));
                }
                for (Term arg : application.args()) {
                    if (!isPolynomial(arg)) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

    private static void collectDenominators(Term term, List<Term> denominators) {
        if (term instanceof Term.ApplicationTerm application) {
            if (application.head() instanceof Term.SymbolTerm head && head.qualifiedName().equals("arith1:divide")) {
                denominators.add(application.args().get(1));
            }
            for (Term arg : application.args()) {
                collectDenominators(arg, denominators);
            }
        }
    }
}
