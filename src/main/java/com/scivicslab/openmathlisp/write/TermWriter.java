package com.scivicslab.openmathlisp.write;

import com.scivicslab.openmathlisp.symbols.Rule;
import com.scivicslab.openmathlisp.symbols.SymbolEntry;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.symbols.InputFormat;
import com.scivicslab.openmathlisp.term.Term;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a term as text for one format. One procedure walks the tree; the difference between
 * targets is only the rule fetched from the symbol table at each node (OpenMathLispConcept_260925_oo01).
 */
public final class TermWriter {

    /** Priority of an atom or of a function-call form: never parenthesized. */
    static final int ATOM_PRIORITY = 100;

    private final SymbolTable symbols;

    /**
     * Creates a writer over a symbol table.
     * @param symbols the table holding the rules
     */
    public TermWriter(SymbolTable symbols) {
        this.symbols = symbols;
    }

    /**
     * Writes a term as the input string of one format.
     * @param term the term
     * @param format the format
     * @return the text
     * @throws TermWriterException when a symbol in the term has no rule for the format
     */
    public String write(Term term, InputFormat format) {
        return writeWithPriority(term, format).text();
    }

    /** The written text together with the priority of its outermost construct. */
    record Written(String text, int priority) {
    }

    Written writeWithPriority(Term term, InputFormat format) {
        switch (term) {
            case Term.IntegerTerm integer -> {
                return number(integer.value().toString(), format);
            }
            case Term.DecimalTerm decimal -> {
                return number(decimal.literal(), format);
            }
            case Term.VariableTerm variable -> {
                return new Written(VariableNames.render(variable.name(), format), ATOM_PRIORITY);
            }
            case Term.SymbolTerm symbol -> {
                return applyRule(symbol, List.of(), null, format);
            }
            case Term.ApplicationTerm application -> {
                if (application.head() instanceof Term.SymbolTerm symbol) {
                    return applyRule(symbol, application.args(), null, format);
                }
                return variableApplication(application, format);
            }
            case Term.BindingTerm binding -> {
                return applyRule(binding.binder(), List.of(), binding, format);
            }
            case Term.AttributionTerm attribution -> {
                // no format writes a reading key out, so only what it is about is written
                return writeWithPriority(attribution.attributed(), format);
            }
        }
    }

    private Written number(String literal, InputFormat format) {
        if (literal.startsWith("-")) {
            return switch (format) {
                case SMT -> new Written("(- " + literal.substring(1) + ")", ATOM_PRIORITY);
                default -> new Written(literal, 40);
            };
        }
        return new Written(literal, ATOM_PRIORITY);
    }

    private Written variableApplication(Term.ApplicationTerm application, InputFormat format) {
        Term.VariableTerm head = (Term.VariableTerm) application.head();
        if (format == InputFormat.SMT) {
            throw new TermWriterException("SMT format has no uninterpreted functions: " + head.name());
        }
        List<String> args = new ArrayList<>();
        for (Term arg : application.args()) {
            args.add(write(arg, format));
        }
        String joined = String.join(", ", args);
        return switch (format) {
            case LATEX -> new Written(VariableNames.render(head.name(), format) + "\\left(" + joined + "\\right)", ATOM_PRIORITY);
            default -> new Written(VariableNames.render(head.name(), format) + "(" + joined + ")", ATOM_PRIORITY);
        };
    }

    private Written applyRule(Term.SymbolTerm symbol, List<Term> args, Term.BindingTerm binding, InputFormat format) {
        SymbolEntry entry = symbols.get(symbol.qualifiedName());
        Rule rule = entry.rule(format).orElseThrow(() ->
                new TermWriterException(format.name().toLowerCase() + " cannot express " + symbol.qualifiedName()));
        switch (rule) {
            case Rule.Infix infix -> {
                return infix(infix, args, format);
            }
            case Rule.Prefix prefix -> {
                Written operand = writeWithPriority(args.get(0), format);
                return new Written(prefix.text() + parenthesize(operand, prefix.priority(), format), prefix.priority());
            }
            case Rule.Function function -> {
                return functionCall(function, args, format);
            }
            case Rule.Template template -> {
                return new Written(TemplateExpander.expand(template.text(), args, binding, this, format, template.childPriority()),
                        template.priority() == null ? ATOM_PRIORITY : template.priority());
            }
            case Rule.Special special -> {
                return SpecialRules.apply(special.name(), args, this, format);
            }
        }
    }

    private Written infix(Rule.Infix infix, List<Term> args, InputFormat format) {
        if (format == InputFormat.SMT) {
            throw new TermWriterException("infix rules are not used for SMT");
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            Written operand = writeWithPriority(args.get(i), format);
            boolean needsParens = operand.priority() < infix.priority()
                    || (operand.priority() == infix.priority()
                        && ((infix.associativity() == Rule.Associativity.LEFT && i > 0)
                            || (infix.associativity() == Rule.Associativity.RIGHT && i < args.size() - 1)));
            if (i > 0) {
                out.append(infix.text());
            }
            out.append(needsParens ? wrap(operand.text(), format) : operand.text());
        }
        return new Written(out.toString(), infix.priority());
    }

    private Written functionCall(Rule.Function function, List<Term> args, InputFormat format) {
        List<String> parts = new ArrayList<>();
        for (Term arg : args) {
            parts.add(write(arg, format));
        }
        if (format == InputFormat.SMT) {
            if (args.size() == 1 && function.name().equals("-")) {
                return new Written("(- " + parts.get(0) + ")", ATOM_PRIORITY);
            }
            return new Written("(" + function.name() + " " + String.join(" ", parts) + ")", ATOM_PRIORITY);
        }
        if (format == InputFormat.LATEX) {
            return new Written(function.name() + "\\left(" + String.join(", ", parts) + "\\right)", ATOM_PRIORITY);
        }
        return new Written(function.name() + "(" + String.join(", ", parts) + ")", ATOM_PRIORITY);
    }

    String parenthesize(Written operand, int priority, InputFormat format) {
        return operand.priority() < priority ? wrap(operand.text(), format) : operand.text();
    }

    static String wrap(String text, InputFormat format) {
        return format == InputFormat.LATEX ? "\\left(" + text + "\\right)" : "(" + text + ")";
    }

    /** @return the symbol table this writer uses */
    public SymbolTable symbols() {
        return symbols;
    }

    /**
     * Counts, for a term, which symbols lack a rule for the format.
     * @param term the term
     * @param format the format
     * @return the qualified names of symbols without a rule, in order of first appearance
     */
    public List<String> symbolsWithoutRule(Term term, InputFormat format) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        collectMissing(term, format, seen);
        return new ArrayList<>(seen.keySet());
    }

    private void collectMissing(Term term, InputFormat format, Map<String, Boolean> seen) {
        switch (term) {
            case Term.SymbolTerm symbol -> {
                if (symbols.get(symbol.qualifiedName()).rule(format).isEmpty()) {
                    seen.put(symbol.qualifiedName(), Boolean.TRUE);
                }
            }
            case Term.ApplicationTerm application -> {
                collectMissing(application.head(), format, seen);
                for (Term arg : application.args()) {
                    collectMissing(arg, format, seen);
                }
            }
            case Term.BindingTerm binding -> {
                collectMissing(binding.binder(), format, seen);
                collectMissing(binding.body(), format, seen);
            }
            case Term.AttributionTerm attribution -> {
                collectMissing(attribution.attributed(), format, seen);
            }
            default -> {
            }
        }
    }
}
