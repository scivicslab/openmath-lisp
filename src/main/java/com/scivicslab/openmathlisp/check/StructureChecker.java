package com.scivicslab.openmathlisp.check;

import com.scivicslab.openmathlisp.symbols.SymbolEntry;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.term.Term;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The structural check: argument counts match the symbol table, and every bound variable occurs in its
 * body (TermFileAndCheckRecord_261002_oo01, decision 1).
 *
 * <p>A bound variable missing from the body means the term says something else than the page does:
 * {@code (calculus1:diff (fns1:lambda (t) x))} differentiates a quantity that does not depend on {@code t},
 * so its value is zero. Declaring {@code x} a function of {@code t} is what the page meant.</p>
 */
public final class StructureChecker {

    private final SymbolTable symbols;

    /**
     * Creates a checker.
     * @param symbols the symbol table giving the arities
     */
    public StructureChecker(SymbolTable symbols) {
        this.symbols = symbols;
    }

    /** What kind of problem: a wrong argument count, or a variable whose dependence is not declared. */
    public enum Kind { ARITY, DEPENDENCE }

    /** One problem found in a term. */
    public record Problem(Kind kind, String message) {
    }

    /**
     * Checks a term.
     * @param term the term
     * @param knownFunctions kept for the caller's signature; no longer consulted
     * @return the problems found, empty when the term passes
     */
    public List<Problem> check(Term term, Set<String> knownFunctions) {
        List<Problem> problems = new ArrayList<>();
        checkArities(term, problems);
        checkBindings(term, problems);
        return problems;
    }

    private void checkArities(Term term, List<Problem> problems) {
        switch (term) {
            case Term.ApplicationTerm application -> {
                if (application.head() instanceof Term.SymbolTerm symbol) {
                    SymbolEntry entry = symbols.get(symbol.qualifiedName());
                    int expected = entry.arity();
                    int given = application.args().size();
                    if (expected == SymbolEntry.NARY) {
                        if (given < 1) {
                            problems.add(new Problem(Kind.ARITY, symbol.qualifiedName() + " applied to no argument"));
                        }
                    } else if (expected != given) {
                        problems.add(new Problem(Kind.ARITY, symbol.qualifiedName() + " expects " + expected + " argument(s), given " + given));
                    }
                }
                for (Term arg : application.args()) {
                    checkArities(arg, problems);
                }
            }
            case Term.BindingTerm binding -> checkArities(binding.body(), problems);
            default -> {
            }
        }
    }

    private void checkBindings(Term term, List<Problem> problems) {
        switch (term) {
            case Term.BindingTerm binding -> {
                Set<String> inBody = new LinkedHashSet<>();
                collectAllVariables(binding.body(), inBody);
                for (Term.VariableTerm bound : binding.variables()) {
                    if (!inBody.contains(bound.name())) {
                        problems.add(new Problem(Kind.DEPENDENCE, "bound variable " + bound.name()
                                + " does not occur in the body of " + binding.binder().qualifiedName()));
                    }
                }
                checkBindings(binding.body(), problems);
            }
            case Term.ApplicationTerm application -> {
                for (Term arg : application.args()) {
                    checkBindings(arg, problems);
                }
            }
            default -> {
            }
        }
    }

    private void collectAllVariables(Term term, Set<String> names) {
        switch (term) {
            case Term.VariableTerm variable -> names.add(variable.name());
            case Term.ApplicationTerm application -> {
                collectAllVariables(application.head(), names);
                for (Term arg : application.args()) {
                    collectAllVariables(arg, names);
                }
            }
            case Term.BindingTerm binding -> collectAllVariables(binding.body(), names);
            default -> {
            }
        }
    }
}
