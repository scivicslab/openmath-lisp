package com.scivicslab.openmathlisp.sexp;

import java.math.BigInteger;
import java.util.List;

/**
 * An s-expression as read from text, before any interpretation as a term.
 * The same reader serves the symbol table, the term files and the terms themselves.
 */
public sealed interface SExp permits SExp.SList, SExp.SSymbol, SExp.SString, SExp.SInteger, SExp.SDecimal {

    /** A parenthesized list. */
    record SList(List<SExp> items) implements SExp {
        /**
         * Creates a list.
         * @param items the elements in order
         */
        public SList {
            items = List.copyOf(items);
        }
    }

    /** A bare name such as {@code arith1:plus}, {@code x}, {@code :role} or {@code nil}. */
    record SSymbol(String name) implements SExp {
        /** @return true when the name starts with a colon, i.e. it is a keyword */
        public boolean isKeyword() {
            return name.startsWith(":");
        }
        /** @return true when the name is {@code nil} */
        public boolean isNil() {
            return name.equals("nil");
        }
    }

    /** A double-quoted string with Lisp escapes already resolved. */
    record SString(String value) implements SExp {
    }

    /** A decimal integer literal. */
    record SInteger(BigInteger value) implements SExp {
    }

    /** A decimal number literal with a point or an exponent; the literal text is kept. */
    record SDecimal(String literal, double value) implements SExp {
    }
}
