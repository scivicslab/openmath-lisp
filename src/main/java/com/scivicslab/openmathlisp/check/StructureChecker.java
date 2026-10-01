package com.scivicslab.openmathlisp.check;

import com.scivicslab.openmathlisp.symbols.SymbolEntry;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.term.Term;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The structural check: argument counts match the symbol table, every bound variable occurs in its body,
 * and every variable free inside a binder body also occurs outside it or is a declared function
 * (TermFileAndCheckRecord_261002_oo01, decision 2).
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
     * @param knownFunctions names declared as functions in the term file, allowed free in binder bodies
     * @return the problems found, empty when the term passes
     */
    public List<Problem> check(Term term, Set<String> knownFunctions) {
        List<Problem> problems = new ArrayList<>();
        checkArities(term, problems);
        Set<String> outside = new LinkedHashSet<>();
        collectVariablesOutsideBindings(term, outside);
        checkBindings(term, outside, knownFunctions, problems);
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

    private void collectVariablesOutsideBindings(Term term, Set<String> names) {
        switch (term) {
            case Term.VariableTerm variable -> names.add(variable.name());
            case Term.ApplicationTerm application -> {
                collectVariablesOutsideBindings(application.head(), names);
                for (Term arg : application.args()) {
                    collectVariablesOutsideBindings(arg, names);
                }
            }
            default -> {
            }
        }
    }

    private void checkBindings(Term term, Set<String> outside, Set<String> knownFunctions, List<Problem> problems) {
        switch (term) {
            case Term.BindingTerm binding -> {
                Set<String> inBody = new LinkedHashSet<>();
                collectAllVariables(binding.body(), inBody);
                for (Term.VariableTerm bound : binding.variables()) {
                    if (!inBody.contains(bound.name())) {
                        problems.add(new Problem(Kind.DEPENDENCE, "bound variable " + bound.name() + " does not occur in the body of " + binding.binder().qualifiedName()));
                    }
                }
                Set<String> boundNames = new LinkedHashSet<>();
                for (Term.VariableTerm bound : binding.variables()) {
                    boundNames.add(bound.name());
                }
                for (String name : inBody) {
                    if (!boundNames.contains(name) && !outside.contains(name) && !knownFunctions.contains(name)) {
                        problems.add(new Problem(Kind.DEPENDENCE, "variable " + name + " is free in a binder body and occurs nowhere else"));
                    }
                }
                checkBindings(binding.body(), outside, knownFunctions, problems);
            }
            case Term.ApplicationTerm application -> {
                for (Term arg : application.args()) {
                    checkBindings(arg, outside, knownFunctions, problems);
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
