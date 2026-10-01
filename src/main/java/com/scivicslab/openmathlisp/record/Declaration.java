package com.scivicslab.openmathlisp.record;

import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.sexp.SexpWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * The per-file declaration {@code (declare ...)} that fixes what the text alone cannot
 * (LatexReaderDecisions_261002_oo01, decision 2): the time variable, the imaginary unit, Euler's number,
 * vector variables, coordinates whose dot is an independent variable, and dependent variables with their arguments.
 */
public final class Declaration {

    private final String time;
    private final String imaginary;
    private final String euler;
    private final Set<String> vectors;
    private final Set<String> coordinates;
    private final Map<String, List<String>> functions;

    /**
     * Creates a declaration.
     * @param time the variable {@code \dot{}} differentiates by, or null
     * @param imaginary the letter read as {@code nums1:i}, or null
     * @param euler the letter whose power is read as {@code transc1:exp}, or null
     * @param vectors variables treated as vectors
     * @param coordinates variables whose dotted form is an independent variable
     * @param functions dependent variables mapped to the variables they depend on
     */
    public Declaration(String time, String imaginary, String euler, Set<String> vectors, Set<String> coordinates,
                       Map<String, List<String>> functions) {
        this.time = time;
        this.imaginary = imaginary;
        this.euler = euler;
        this.vectors = Set.copyOf(vectors);
        this.coordinates = Set.copyOf(coordinates);
        this.functions = Map.copyOf(functions);
    }

    /** @return the declaration with nothing declared */
    public static Declaration empty() {
        return new Declaration(null, null, null, Set.of(), Set.of(), Map.of());
    }

    /**
     * Reads a declaration from its s-expression {@code (declare :key value ...)}.
     * @param expression the s-expression
     * @return the declaration
     * @throws IllegalArgumentException when the expression is not a declaration
     */
    public static Declaration fromSExp(SExp expression) {
        if (!(expression instanceof SExp.SList list) || list.items().isEmpty()
                || !(list.items().get(0) instanceof SExp.SSymbol head) || !head.name().equals("declare")) {
            throw new IllegalArgumentException("not a (declare ...) form: " + expression);
        }
        String time = null;
        String imaginary = null;
        String euler = null;
        Set<String> vectors = new LinkedHashSet<>();
        Set<String> coordinates = new LinkedHashSet<>();
        Map<String, List<String>> functions = new LinkedHashMap<>();
        for (int i = 1; i + 1 < list.items().size(); i += 2) {
            String key = ((SExp.SSymbol) list.items().get(i)).name();
            SExp value = list.items().get(i + 1);
            switch (key) {
                case ":time" -> time = symbolName(value);
                case ":imaginary" -> imaginary = symbolName(value);
                case ":euler" -> euler = symbolName(value);
                case ":vectors" -> vectors.addAll(symbolNames(value));
                case ":coordinates" -> coordinates.addAll(symbolNames(value));
                case ":functions" -> {
                    for (SExp entry : ((SExp.SList) value).items()) {
                        List<String> names = symbolNames(entry);
                        functions.put(names.get(0), names.subList(1, names.size()));
                    }
                }
                default -> throw new IllegalArgumentException("unknown declaration key " + key);
            }
        }
        return new Declaration(time, imaginary, euler, vectors, coordinates, functions);
    }

    private static String symbolName(SExp value) {
        return ((SExp.SSymbol) value).name();
    }

    private static List<String> symbolNames(SExp value) {
        List<String> names = new ArrayList<>();
        for (SExp item : ((SExp.SList) value).items()) {
            names.add(symbolName(item));
        }
        return names;
    }

    /** @return the s-expression {@code (declare ...)} */
    public SExp toSExp() {
        List<SExp> items = new ArrayList<>();
        items.add(new SExp.SSymbol("declare"));
        if (time != null) {
            items.add(new SExp.SSymbol(":time"));
            items.add(new SExp.SSymbol(time));
        }
        if (imaginary != null) {
            items.add(new SExp.SSymbol(":imaginary"));
            items.add(new SExp.SSymbol(imaginary));
        }
        if (euler != null) {
            items.add(new SExp.SSymbol(":euler"));
            items.add(new SExp.SSymbol(euler));
        }
        if (!vectors.isEmpty()) {
            items.add(new SExp.SSymbol(":vectors"));
            items.add(symbolList(vectors));
        }
        if (!coordinates.isEmpty()) {
            items.add(new SExp.SSymbol(":coordinates"));
            items.add(symbolList(coordinates));
        }
        if (!functions.isEmpty()) {
            items.add(new SExp.SSymbol(":functions"));
            List<SExp> entries = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : functions.entrySet()) {
                List<String> names = new ArrayList<>();
                names.add(entry.getKey());
                names.addAll(entry.getValue());
                entries.add(symbolList(names));
            }
            items.add(new SExp.SList(entries));
        }
        return new SExp.SList(items);
    }

    private static SExp.SList symbolList(Iterable<String> names) {
        List<SExp> items = new ArrayList<>();
        for (String name : names) {
            items.add(new SExp.SSymbol(name));
        }
        return new SExp.SList(items);
    }

    /** @return the time variable, if declared */
    public Optional<String> time() {
        return Optional.ofNullable(time);
    }

    /** @return the imaginary unit letter, if declared */
    public Optional<String> imaginary() {
        return Optional.ofNullable(imaginary);
    }

    /** @return Euler's letter, if declared */
    public Optional<String> euler() {
        return Optional.ofNullable(euler);
    }

    /** @return the vector variables */
    public Set<String> vectors() {
        return vectors;
    }

    /** @return the coordinates whose dotted form is a variable */
    public Set<String> coordinates() {
        return coordinates;
    }

    /** @return dependent variables and the variables they depend on */
    public Map<String, List<String>> functions() {
        return functions;
    }

    @Override
    public String toString() {
        return SexpWriter.writeFlat(toSExp());
    }
}
