package com.scivicslab.openmathlisp.project;

import com.scivicslab.openmathlisp.symbols.Rule;
import com.scivicslab.openmathlisp.symbols.SymbolEntry;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.symbols.Target;
import com.scivicslab.openmathlisp.term.Term;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a term as text for one target. One procedure walks the tree; the difference between
 * targets is only the rule fetched from the symbol table at each node (OpenMathLispConcept_260925_oo01).
 */
public final class Projector {

    /** Priority of an atom or of a function-call form: never parenthesized. */
    static final int ATOM_PRIORITY = 100;

    private final SymbolTable symbols;

    /**
     * Creates a projector over a symbol table.
     * @param symbols the table holding the rules
     */
    public Projector(SymbolTable symbols) {
        this.symbols = symbols;
    }

    /**
     * Projects a term.
     * @param term the term
     * @param target the target
     * @return the text
     * @throws ProjectionException when a symbol in the term has no rule for the target
     */
    public String project(Term term, Target target) {
        return projectWithPriority(term, target).text();
    }

    /** Projected text together with the priority of its outermost construct. */
    record Projected(String text, int priority) {
    }

    Projected projectWithPriority(Term term, Target target) {
        switch (term) {
            case Term.IntegerTerm integer -> {
                return number(integer.value().toString(), target);
            }
            case Term.DecimalTerm decimal -> {
                return number(decimal.literal(), target);
            }
            case Term.VariableTerm variable -> {
                return new Projected(VariableNames.render(variable.name(), target), ATOM_PRIORITY);
            }
            case Term.SymbolTerm symbol -> {
                return applyRule(symbol, List.of(), null, target);
            }
            case Term.ApplicationTerm application -> {
                if (application.head() instanceof Term.SymbolTerm symbol) {
                    return applyRule(symbol, application.args(), null, target);
                }
                return variableApplication(application, target);
            }
            case Term.BindingTerm binding -> {
                return applyRule(binding.binder(), List.of(), binding, target);
            }
        }
    }

    private Projected number(String literal, Target target) {
        if (literal.startsWith("-")) {
            return switch (target) {
                case SMT -> new Projected("(- " + literal.substring(1) + ")", ATOM_PRIORITY);
                default -> new Projected(literal, 40);
            };
        }
        return new Projected(literal, ATOM_PRIORITY);
    }

    private Projected variableApplication(Term.ApplicationTerm application, Target target) {
        Term.VariableTerm head = (Term.VariableTerm) application.head();
        if (target == Target.SMT) {
            throw new ProjectionException("SMT target has no uninterpreted functions: " + head.name());
        }
        List<String> args = new ArrayList<>();
        for (Term arg : application.args()) {
            args.add(project(arg, target));
        }
        String joined = String.join(", ", args);
        return switch (target) {
            case LATEX -> new Projected(VariableNames.render(head.name(), target) + "\\left(" + joined + "\\right)", ATOM_PRIORITY);
            default -> new Projected(VariableNames.render(head.name(), target) + "(" + joined + ")", ATOM_PRIORITY);
        };
    }

    private Projected applyRule(Term.SymbolTerm symbol, List<Term> args, Term.BindingTerm binding, Target target) {
        SymbolEntry entry = symbols.get(symbol.qualifiedName());
        Rule rule = entry.rule(target).orElseThrow(() ->
                new ProjectionException(target.name().toLowerCase() + " cannot express " + symbol.qualifiedName()));
        switch (rule) {
            case Rule.Infix infix -> {
                return infix(infix, args, target);
            }
            case Rule.Prefix prefix -> {
                Projected operand = projectWithPriority(args.get(0), target);
                return new Projected(prefix.text() + parenthesize(operand, prefix.priority(), target), prefix.priority());
            }
            case Rule.Function function -> {
                return functionCall(function, args, target);
            }
            case Rule.Template template -> {
                return new Projected(TemplateExpander.expand(template.text(), args, binding, this, target, template.childPriority()),
                        template.priority() == null ? ATOM_PRIORITY : template.priority());
            }
            case Rule.Special special -> {
                return SpecialRules.apply(special.name(), args, this, target);
            }
        }
    }

    private Projected infix(Rule.Infix infix, List<Term> args, Target target) {
        if (target == Target.SMT) {
            throw new ProjectionException("infix rules are not used for SMT");
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            Projected operand = projectWithPriority(args.get(i), target);
            boolean needsParens = operand.priority() < infix.priority()
                    || (operand.priority() == infix.priority()
                        && ((infix.associativity() == Rule.Associativity.LEFT && i > 0)
                            || (infix.associativity() == Rule.Associativity.RIGHT && i < args.size() - 1)));
            if (i > 0) {
                out.append(infix.text());
            }
            out.append(needsParens ? wrap(operand.text(), target) : operand.text());
        }
        return new Projected(out.toString(), infix.priority());
    }

    private Projected functionCall(Rule.Function function, List<Term> args, Target target) {
        List<String> parts = new ArrayList<>();
        for (Term arg : args) {
            parts.add(project(arg, target));
        }
        if (target == Target.SMT) {
            if (args.size() == 1 && function.name().equals("-")) {
                return new Projected("(- " + parts.get(0) + ")", ATOM_PRIORITY);
            }
            return new Projected("(" + function.name() + " " + String.join(" ", parts) + ")", ATOM_PRIORITY);
        }
        if (target == Target.LATEX) {
            return new Projected(function.name() + "\\left(" + String.join(", ", parts) + "\\right)", ATOM_PRIORITY);
        }
        return new Projected(function.name() + "(" + String.join(", ", parts) + ")", ATOM_PRIORITY);
    }

    String parenthesize(Projected operand, int priority, Target target) {
        return operand.priority() < priority ? wrap(operand.text(), target) : operand.text();
    }

    static String wrap(String text, Target target) {
        return target == Target.LATEX ? "\\left(" + text + "\\right)" : "(" + text + ")";
    }

    /** @return the symbol table this projector uses */
    public SymbolTable symbols() {
        return symbols;
    }

    /**
     * Counts, for a term, which symbols lack a rule for the target.
     * @param term the term
     * @param target the target
     * @return the qualified names of symbols without a rule, in order of first appearance
     */
    public List<String> symbolsWithoutRule(Term term, Target target) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        collectMissing(term, target, seen);
        return new ArrayList<>(seen.keySet());
    }

    private void collectMissing(Term term, Target target, Map<String, Boolean> seen) {
        switch (term) {
            case Term.SymbolTerm symbol -> {
                if (symbols.get(symbol.qualifiedName()).rule(target).isEmpty()) {
                    seen.put(symbol.qualifiedName(), Boolean.TRUE);
                }
            }
            case Term.ApplicationTerm application -> {
                collectMissing(application.head(), target, seen);
                for (Term arg : application.args()) {
                    collectMissing(arg, target, seen);
                }
            }
            case Term.BindingTerm binding -> {
                collectMissing(binding.binder(), target, seen);
                collectMissing(binding.body(), target, seen);
            }
            default -> {
            }
        }
    }
}
