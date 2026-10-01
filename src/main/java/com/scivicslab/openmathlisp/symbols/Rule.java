package com.scivicslab.openmathlisp.symbols;

/** A projection rule from a symbol table row. The five kinds follow TermGrammarAndSymbolTable_261002_oo01. */
public sealed interface Rule permits Rule.Infix, Rule.Prefix, Rule.Function, Rule.Template, Rule.Special {

    /** Associativity of an infix operator. */
    enum Associativity { LEFT, RIGHT }

    /** {@code (:infix "text" priority :left|:right)}: arguments joined by the text. */
    record Infix(String text, int priority, Associativity associativity) implements Rule {
    }

    /** {@code (:prefix "text" priority)}: the text before the single argument. */
    record Prefix(String text, int priority) implements Rule {
    }

    /** {@code (:function "name")}: {@code name(arg, ...)} or, for SMT, {@code (name arg ...)}. */
    record Function(String name) implements Rule {
    }

    /**
     * {@code (:template "text" [priority])}: {@code ~1}, {@code ~2}, {@code ~*} are replaced by projected
     * arguments, {@code ~1.2} by the second element of argument 1, {@code ~2.v1} by the first bound variable
     * of binding argument 2, {@code ~2.b} by its body, {@code ~v} / {@code ~b} by the binding's own variables
     * and body. {@code priority} is the binding strength of the result; {@code childPriority} is the strength
     * below which arguments are parenthesized (it defaults to {@code priority}). Without any priority the
     * arguments are never parenthesized.
     */
    record Template(String text, Integer priority, Integer childPriority) implements Rule {
        /**
         * Creates a template rule.
         * @param text the template
         * @param priority binding strength of the result, or null
         * @param childPriority threshold for parenthesizing arguments, or null to use the priority
         */
        public Template {
            if (childPriority == null) {
                childPriority = priority;
            }
        }
    }

    /** {@code (:special "name")}: handled by a named procedure in the projector. */
    record Special(String name) implements Rule {
    }
}
