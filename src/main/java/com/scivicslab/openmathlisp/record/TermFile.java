package com.scivicslab.openmathlisp.record;

import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.sexp.SexpReader;
import com.scivicslab.openmathlisp.sexp.SexpWriter;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A term file {@code X.lisp} beside {@code X.md}: one declaration followed by the check records in
 * document order (TermFileAndCheckRecord_261002_oo01, decision 1).
 */
public final class TermFile {

    private final Declaration declaration;
    private final List<EquationRecord> records;

    /**
     * Creates a term file in memory.
     * @param declaration the declaration
     * @param records the records in order
     */
    public TermFile(Declaration declaration, List<EquationRecord> records) {
        this.declaration = declaration;
        this.records = List.copyOf(records);
    }

    /**
     * Gives the term file path for a markdown file.
     * @param markdown {@code X.md}
     * @return {@code X.lisp} in the same directory
     */
    public static Path pathFor(Path markdown) {
        String name = markdown.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        return markdown.resolveSibling(stem + ".lisp");
    }

    /**
     * Parses a term file.
     * @param text the file text
     * @param factory the factory for the terms
     * @return the term file
     */
    public static TermFile parse(String text, TermFactory factory) {
        Declaration declaration = Declaration.empty();
        List<EquationRecord> records = new ArrayList<>();
        for (SExp form : SexpReader.readAll(text)) {
            if (form instanceof SExp.SList list && !list.items().isEmpty() && list.items().get(0) instanceof SExp.SSymbol head) {
                if (head.name().equals("declare")) {
                    declaration = Declaration.fromSExp(form);
                    continue;
                }
                if (head.name().equals("equation")) {
                    records.add(EquationRecord.fromSExp(form, factory));
                    continue;
                }
            }
            throw new IllegalArgumentException("unexpected form in term file: " + SexpWriter.writeFlat(form));
        }
        return new TermFile(declaration, records);
    }

    /**
     * Reads a term file from disk.
     * @param path the file
     * @param factory the factory for the terms
     * @return the term file
     * @throws IOException when the file cannot be read
     */
    public static TermFile read(Path path, TermFactory factory) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8), factory);
    }

    /** @return the file text */
    public String write() {
        StringBuilder out = new StringBuilder();
        out.append(SexpWriter.writeFlat(declaration.toSExp())).append("\n\n");
        for (EquationRecord record : records) {
            out.append(SexpWriter.writePretty(record.toSExp())).append("\n\n");
        }
        return out.toString();
    }

    /**
     * Writes the file, leaving the previous content in {@code X.lisp.bak} when the file existed.
     * @param path the file
     * @throws IOException when the file cannot be written
     */
    public void writeTo(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(path, write(), StandardCharsets.UTF_8);
    }

    /**
     * Merges freshly read records into this file: records whose term a person edited keep their term,
     * all others are replaced; the declaration is kept.
     * @param fresh the records the reader produced
     * @return the merged file
     */
    public TermFile mergeFresh(List<EquationRecord> fresh) {
        Map<String, EquationRecord> existing = new LinkedHashMap<>();
        for (EquationRecord record : records) {
            existing.put(record.id(), record);
        }
        List<EquationRecord> merged = new ArrayList<>();
        for (EquationRecord record : fresh) {
            EquationRecord previous = existing.get(record.id());
            if (previous != null && previous.edited()) {
                merged.add(previous.keepingTermOver(record));
            } else {
                merged.add(record);
            }
        }
        return new TermFile(declaration, merged);
    }

    /**
     * Returns a copy with the records replaced.
     * @param newRecords the records
     * @return the copy
     */
    public TermFile withRecords(List<EquationRecord> newRecords) {
        return new TermFile(declaration, newRecords);
    }

    /** @return the declaration */
    public Declaration declaration() {
        return declaration;
    }

    /** @return the records in order */
    public List<EquationRecord> records() {
        return records;
    }
}
