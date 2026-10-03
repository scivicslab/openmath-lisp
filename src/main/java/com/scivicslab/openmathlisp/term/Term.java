package com.scivicslab.openmathlisp.term;

import java.math.BigInteger;
import java.util.List;

/**
 * A formula as a tree. Heads are OpenMath symbols; leaves are variables and numbers.
 * The four elements and three forms follow TermGrammarAndSymbolTable_261002_oo01.
 */
public sealed interface Term permits Term.SymbolTerm, Term.VariableTerm, Term.IntegerTerm,
        Term.DecimalTerm, Term.ApplicationTerm, Term.BindingTerm {

    /** An OpenMath symbol {@code cd:name}; standing alone it is a constant. */
    record SymbolTerm(String cd, String name) implements Term {
        /** @return the qualified name {@code cd:name} */
        public String qualifiedName() {
            return cd + ":" + name;
        }
    }

    /** A variable; the name may carry subscript and superscript labels with {@code _} and {@code ^}. */
    record VariableTerm(String name) implements Term {
    }

    /** An integer. */
    record IntegerTerm(BigInteger value) implements Term {
    }

    /** A decimal number; the literal text is kept so that writing it out reproduces it. */
    record DecimalTerm(String literal) implements Term {
    }

    /** An application {@code (head args...)}; the head is a symbol with role application or a variable. */
    record ApplicationTerm(Term head, List<Term> args) implements Term {
        /**
         * Creates an application.
         * @param head the symbol or variable applied
         * @param args the arguments in order
         */
        public ApplicationTerm {
            args = List.copyOf(args);
        }
    }

    /** A binding {@code (binder (vars...) body)}; the binder is a symbol with role binder. */
    record BindingTerm(SymbolTerm binder, List<VariableTerm> variables, Term body) implements Term {
        /**
         * Creates a binding.
         * @param binder the binder symbol, e.g. {@code fns1:lambda}
         * @param variables the bound variables in order
         * @param body the body
         */
        public BindingTerm {
            variables = List.copyOf(variables);
        }
    }
}
