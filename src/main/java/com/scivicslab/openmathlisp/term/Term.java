package com.scivicslab.openmathlisp.term;

import java.math.BigInteger;
import java.util.List;

/**
 * A formula as a tree. Heads are OpenMath symbols; leaves are variables and numbers.
 * The four elements and three forms follow TermGrammarAndSymbolTable_261002_oo01.
 */
public sealed interface Term permits Term.SymbolTerm, Term.VariableTerm, Term.IntegerTerm,
        Term.DecimalTerm, Term.ApplicationTerm, Term.BindingTerm, Term.AttributionTerm {

    /**
     * The term an attribution is about, with every attribution removed.
     *
     * <p>The OpenMath standard says of a symbol whose role is attribution that the attribution "may be
     * ignored by an application, so should be used for information which does not change the meaning of
     * the attributed OpenMath object". The reading keys of {@code private_reading1} are such symbols:
     * they record how a formula's LaTeX was read, which the term itself already shows. Writing and the
     * checks therefore work on the term this returns.</p>
     *
     * @param term any term
     * @return the term without its attributions
     */
    static Term withoutAttributions(Term term) {
        Term bare = term;
        while (bare instanceof AttributionTerm attribution) {
            bare = attribution.attributed();
        }
        return switch (bare) {
            case ApplicationTerm application -> {
                java.util.List<Term> args = new java.util.ArrayList<>();
                for (Term arg : application.args()) {
                    args.add(withoutAttributions(arg));
                }
                yield new ApplicationTerm(withoutAttributions(application.head()), args);
            }
            case BindingTerm binding -> new BindingTerm(binding.binder(), binding.variables(),
                    withoutAttributions(binding.body()));
            default -> bare;
        };
    }

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

    /**
     * An attribution {@code (key value attributed)}: the key is a symbol whose role is attribution, the
     * value says what the key asserts, and the attributed term is what it is asserted about.
     *
     * <p>The OpenMath standard builds this as {@code attribution(A, S1 A1, ..., Sn An)}, where {@code A} is
     * any object. A single formula, a subexpression or one variable can therefore carry an attribution.
     * Here one attribution holds one pair; several pairs nest.</p>
     *
     * @param key the attribution symbol
     * @param value what the key asserts
     * @param attributed the term the assertion is about
     */
    record AttributionTerm(SymbolTerm key, Term value, Term attributed) implements Term {
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
