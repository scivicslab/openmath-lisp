package com.scivicslab.openmathlisp.symbols;

import java.util.Map;
import java.util.Optional;

/**
 * One row of the symbol table.
 * @param qualifiedName {@code cd:name}
 * @param role the role from the content dictionary
 * @param arity the number of arguments, or {@link #NARY} for a variable number
 * @param rules the writing rule per format; a format absent from the map cannot express the symbol
 */
public record SymbolEntry(String qualifiedName, Role role, int arity, Map<InputFormat, Rule> rules) {

    /** Arity value meaning "any number of arguments". */
    public static final int NARY = -1;

    /**
     * Creates a row.
     * @param qualifiedName {@code cd:name}
     * @param role the role
     * @param arity the arity or {@link #NARY}
     * @param rules the rules per format
     */
    public SymbolEntry {
        rules = Map.copyOf(rules);
    }

    /**
     * Looks up the rule for a format.
     * @param format the format
     * @return the rule, or empty when the format cannot express this symbol
     */
    public Optional<Rule> rule(InputFormat format) {
        return Optional.ofNullable(rules.get(format));
    }

    /** @return the dictionary part of the qualified name */
    public String cd() {
        return qualifiedName.substring(0, qualifiedName.indexOf(':'));
    }

    /** @return the name part of the qualified name */
    public String name() {
        return qualifiedName.substring(qualifiedName.indexOf(':') + 1);
    }
}
