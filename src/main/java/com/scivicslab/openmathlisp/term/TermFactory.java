package com.scivicslab.openmathlisp.term;

import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.symbols.SymbolEntry;
import com.scivicslab.openmathlisp.symbols.SymbolTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between s-expressions and terms. The symbol table decides which lists are bindings:
 * a list whose head symbol has role binder is a binding, every other list is an application.
 */
public final class TermFactory {

    private final SymbolTable symbols;

    /**
     * Creates a factory bound to a symbol table.
     * @param symbols the symbol table used to classify heads
     */
    public TermFactory(SymbolTable symbols) {
        this.symbols = symbols;
    }

    /**
     * Builds a term from an s-expression.
     * @param expression the s-expression
     * @return the term
     * @throws TermFormatException when the expression is not a well-formed term
     */
    public Term fromSExp(SExp expression) {
        switch (expression) {
            case SExp.SInteger integer -> {
                return new Term.IntegerTerm(integer.value());
            }
            case SExp.SDecimal decimal -> {
                return new Term.DecimalTerm(decimal.literal());
            }
            case SExp.SString string -> throw new TermFormatException("strings are not terms: " + string.value());
            case SExp.SSymbol symbol -> {
                return atomFromSymbol(symbol);
            }
            case SExp.SList list -> {
                return compoundFromList(list);
            }
        }
    }

    private Term atomFromSymbol(SExp.SSymbol symbol) {
        String name = symbol.name();
        if (symbol.isKeyword() || symbol.isNil()) {
            throw new TermFormatException("not a term element: " + name);
        }
        int colon = name.indexOf(':');
        if (colon < 0) {
            if (!name.matches("[A-Za-z][A-Za-z0-9_^]*'*")) {
                throw new TermFormatException("variable name not allowed: " + name);
            }
            return new Term.VariableTerm(name);
        }
        Term.SymbolTerm symbolTerm = new Term.SymbolTerm(name.substring(0, colon), name.substring(colon + 1));
        if (!symbols.contains(symbolTerm.qualifiedName())) {
            throw new TermFormatException("symbol not in the symbol table: " + name);
        }
        return symbolTerm;
    }

    private Term compoundFromList(SExp.SList list) {
        if (list.items().isEmpty()) {
            throw new TermFormatException("empty list is not a term");
        }
        Term head = fromSExp(list.items().get(0));
        if (head instanceof Term.SymbolTerm symbolHead) {
            SymbolEntry entry = symbols.get(symbolHead.qualifiedName());
            switch (entry.role()) {
                case BINDER -> {
                    return bindingFromList(symbolHead, list);
                }
                case CONSTANT -> throw new TermFormatException("constant cannot be applied: " + symbolHead.qualifiedName());
                case APPLICATION -> {
                    return new Term.ApplicationTerm(head, argumentsFromList(list));
                }
            }
        }
        if (head instanceof Term.VariableTerm) {
            return new Term.ApplicationTerm(head, argumentsFromList(list));
        }
        throw new TermFormatException("head must be a symbol or a variable: " + list.items().get(0));
    }

    private List<Term> argumentsFromList(SExp.SList list) {
        List<Term> args = new ArrayList<>();
        for (int i = 1; i < list.items().size(); i++) {
            args.add(fromSExp(list.items().get(i)));
        }
        return args;
    }

    private Term.BindingTerm bindingFromList(Term.SymbolTerm binder, SExp.SList list) {
        if (list.items().size() != 3 || !(list.items().get(1) instanceof SExp.SList variableList)) {
            throw new TermFormatException("binding must be (binder (vars...) body): " + binder.qualifiedName());
        }
        List<Term.VariableTerm> variables = new ArrayList<>();
        for (SExp item : variableList.items()) {
            Term variable = fromSExp(item);
            if (!(variable instanceof Term.VariableTerm variableTerm)) {
                throw new TermFormatException("bound element is not a variable: " + item);
            }
            variables.add(variableTerm);
        }
        if (variables.isEmpty()) {
            throw new TermFormatException("binding binds no variable: " + binder.qualifiedName());
        }
        return new Term.BindingTerm(binder, variables, fromSExp(list.items().get(2)));
    }

    /**
     * Converts a term back to an s-expression.
     * @param term the term
     * @return the s-expression
     */
    public static SExp toSExp(Term term) {
        switch (term) {
            case Term.SymbolTerm symbol -> {
                return new SExp.SSymbol(symbol.qualifiedName());
            }
            case Term.VariableTerm variable -> {
                return new SExp.SSymbol(variable.name());
            }
            case Term.IntegerTerm integer -> {
                return new SExp.SInteger(integer.value());
            }
            case Term.DecimalTerm decimal -> {
                return new SExp.SDecimal(decimal.literal(), Double.parseDouble(decimal.literal()));
            }
            case Term.ApplicationTerm application -> {
                List<SExp> items = new ArrayList<>();
                items.add(toSExp(application.head()));
                for (Term arg : application.args()) {
                    items.add(toSExp(arg));
                }
                return new SExp.SList(items);
            }
            case Term.BindingTerm binding -> {
                List<SExp> variables = new ArrayList<>();
                for (Term.VariableTerm variable : binding.variables()) {
                    variables.add(new SExp.SSymbol(variable.name()));
                }
                return new SExp.SList(List.of(toSExp(binding.binder()), new SExp.SList(variables), toSExp(binding.body())));
            }
        }
    }
}
