package com.scivicslab.openmathlisp.record;

import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.sexp.SexpReader;
import com.scivicslab.openmathlisp.sexp.SexpWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The check record file {@code X.lisp} beside the markdown file {@code X.md}: the check results of that
 * file's formulas, in file order. It holds no terms, so it can be deleted and produced again by checking.
 */
public final class CheckRecordFile {

    private final List<EquationRecord> records;

    /**
     * Creates a file in memory.
     * @param records the records in order
     */
    public CheckRecordFile(List<EquationRecord> records) {
        this.records = List.copyOf(records);
    }

    /**
     * Gives the check record file path for a markdown file.
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
     * Parses a check record file.
     * @param text the file text
     * @return the file
     */
    public static CheckRecordFile parse(String text) {
        List<EquationRecord> records = new ArrayList<>();
        for (SExp form : SexpReader.readAll(text)) {
            records.add(EquationRecord.fromSExp(form));
        }
        return new CheckRecordFile(records);
    }

    /**
     * Reads a check record file from disk.
     * @param path the file
     * @return the file
     * @throws IOException when the file cannot be read
     */
    public static CheckRecordFile read(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** @return the file text */
    public String write() {
        StringBuilder out = new StringBuilder();
        for (EquationRecord record : records) {
            out.append(SexpWriter.writePretty(record.toSExp())).append("\n\n");
        }
        return out.toString();
    }

    /**
     * Writes the file.
     * @param path the file
     * @throws IOException when the file cannot be written
     */
    public void writeTo(Path path) throws IOException {
        Files.writeString(path, write(), StandardCharsets.UTF_8);
    }

    /** @return the records in order */
    public List<EquationRecord> records() {
        return records;
    }
}
