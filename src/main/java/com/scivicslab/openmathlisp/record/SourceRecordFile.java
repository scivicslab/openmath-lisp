package com.scivicslab.openmathlisp.record;

import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.sexp.SexpReader;
import com.scivicslab.openmathlisp.sexp.SexpWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The file {@code X.ocr.lisp} beside the markdown file {@code X.md}: the LaTeX each formula had before
 * conversion replaced it, one record per formula identifier.
 *
 * <p>When a check reports a formula as suspect, the fault is in one of four places: the page itself, the
 * OCR reading of it, the LaTeX reader that built the term, or the projection of the term back to LaTeX.
 * The page is in the PDF, the term is in the markdown, the projection is in the check record. This file
 * holds the one remaining piece, which conversion would otherwise destroy: what the OCR produced.</p>
 *
 * <p>The records are therefore not derivable from anything still on disk and belong in version control,
 * unlike the check record file beside them.</p>
 */
public final class SourceRecordFile {

    private final Map<String, String> ocrByIdentifier;

    /**
     * Creates a file in memory.
     * @param ocrByIdentifier formula identifier to the LaTeX the OCR produced, in document order
     */
    public SourceRecordFile(Map<String, String> ocrByIdentifier) {
        this.ocrByIdentifier = new LinkedHashMap<>(ocrByIdentifier);
    }

    /**
     * Gives the source record file path for a markdown file.
     * @param markdown {@code X.md}
     * @return {@code X.ocr.lisp} in the same directory
     */
    public static Path pathFor(Path markdown) {
        String name = markdown.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        return markdown.resolveSibling(stem + ".ocr.lisp");
    }

    /**
     * Parses a source record file.
     * @param text the file text
     * @return the file
     */
    public static SourceRecordFile parse(String text) {
        Map<String, String> records = new LinkedHashMap<>();
        for (SExp form : SexpReader.readAll(text)) {
            if (!(form instanceof SExp.SList list) || list.items().isEmpty()
                    || !(list.items().get(0) instanceof SExp.SSymbol head) || !head.name().equals("formula")) {
                throw new IllegalArgumentException("not a (formula ...) form: " + SexpWriter.writeFlat(form));
            }
            Map<String, SExp> fields = new LinkedHashMap<>();
            for (int i = 1; i + 1 < list.items().size(); i += 2) {
                fields.put(((SExp.SSymbol) list.items().get(i)).name(), list.items().get(i + 1));
            }
            records.put(((SExp.SString) fields.get(":id")).value(), ((SExp.SString) fields.get(":ocr")).value());
        }
        return new SourceRecordFile(records);
    }

    /**
     * Reads a source record file, or an empty one when the file does not exist.
     * @param path the file
     * @return the file
     * @throws IOException when the file exists but cannot be read
     */
    public static SourceRecordFile readOrEmpty(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new SourceRecordFile(Map.of());
        }
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** @return the file text */
    public String write() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : ocrByIdentifier.entrySet()) {
            List<SExp> items = List.of(new SExp.SSymbol("formula"),
                    new SExp.SSymbol(":id"), new SExp.SString(entry.getKey()),
                    new SExp.SSymbol(":ocr"), new SExp.SString(entry.getValue()));
            out.append(SexpWriter.writePretty(new SExp.SList(items))).append("\n\n");
        }
        return out.toString();
    }

    /**
     * Writes the file, or deletes it when there is nothing to record.
     * @param path the file
     * @throws IOException when the file cannot be written
     */
    public void writeTo(Path path) throws IOException {
        if (ocrByIdentifier.isEmpty()) {
            Files.deleteIfExists(path);
            return;
        }
        Files.writeString(path, write(), StandardCharsets.UTF_8);
    }

    /**
     * Returns a copy with records added. A record already present keeps its text: the OCR reading of a
     * formula is written once, when conversion replaces it, and never revised afterwards.
     * @param fresh identifier to LaTeX for the formulas just converted
     * @return the merged file
     */
    public SourceRecordFile merge(Map<String, String> fresh) {
        Map<String, String> merged = new LinkedHashMap<>(ocrByIdentifier);
        for (Map.Entry<String, String> entry : fresh.entrySet()) {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return new SourceRecordFile(merged);
    }

    /**
     * Looks up what the OCR produced for one formula.
     * @param identifier the formula identifier
     * @return the LaTeX, or null when this file has no record for it
     */
    public String ocrOf(String identifier) {
        return ocrByIdentifier.get(identifier);
    }

    /** @return the records in document order */
    public Map<String, String> records() {
        return new LinkedHashMap<>(ocrByIdentifier);
    }

    /** @return how many formulas this file records */
    public int size() {
        return ocrByIdentifier.size();
    }

}
