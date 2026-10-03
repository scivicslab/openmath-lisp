package com.scivicslab.openmathlisp.record;

import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.sexp.SexpWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One check record: one formula of a markdown file as the term the om block holds, the LaTeX the writer
 * writes from it, the results of the four checks and the status they give it.
 *
 * <p>Both the term and the LaTeX are copies: the term's original is the om block the identifier names, and
 * the LaTeX is produced from it on demand. They are written here so that a suspect formula can be read
 * without opening the markdown or rendering it, and so that the written LaTeX can be compared with what the
 * OCR produced ({@link SourceRecordFile}).</p>
 */
public final class EquationRecord {

    private final String id;
    private final String term;
    private final String latex;
    private final Map<String, SExp> checks;
    private final String status;

    /**
     * Creates a record.
     * @param id the identifier of the om block
     * @param term the s-expression of that block on one line
     * @param latex the LaTeX the writer writes from that term
     * @param checks check name (e.g. {@code :parse}) to result, in check order
     * @param status one of {@code :ok}, {@code :suspect}, {@code :unparseable}, {@code :not-checkable}
     */
    public EquationRecord(String id, String term, String latex, Map<String, SExp> checks, String status) {
        this.id = id;
        this.term = term;
        this.latex = latex;
        this.checks = new LinkedHashMap<>(checks);
        this.status = status;
    }

    /**
     * Reads a record from its s-expression.
     * @param expression the {@code (equation ...)} form
     * @return the record
     */
    public static EquationRecord fromSExp(SExp expression) {
        if (!(expression instanceof SExp.SList list) || list.items().isEmpty()
                || !(list.items().get(0) instanceof SExp.SSymbol head) || !head.name().equals("equation")) {
            throw new IllegalArgumentException("not an (equation ...) form: " + expression);
        }
        Map<String, SExp> fields = new LinkedHashMap<>();
        for (int i = 1; i + 1 < list.items().size(); i += 2) {
            fields.put(((SExp.SSymbol) list.items().get(i)).name(), list.items().get(i + 1));
        }
        String id = ((SExp.SString) fields.get(":id")).value();
        String term = fields.containsKey(":term") ? ((SExp.SString) fields.get(":term")).value() : "";
        String latex = fields.containsKey(":latex") ? ((SExp.SString) fields.get(":latex")).value() : "";
        Map<String, SExp> checks = new LinkedHashMap<>();
        if (fields.get(":checks") instanceof SExp.SList checkList) {
            for (SExp pair : checkList.items()) {
                SExp.SList entry = (SExp.SList) pair;
                checks.put(((SExp.SSymbol) entry.items().get(0)).name(), entry.items().get(1));
            }
        }
        String status = fields.containsKey(":status") ? ((SExp.SSymbol) fields.get(":status")).name() : ":not-checkable";
        return new EquationRecord(id, term, latex, checks, status);
    }

    /** @return the s-expression {@code (equation ...)} */
    public SExp toSExp() {
        List<SExp> items = new ArrayList<>();
        items.add(new SExp.SSymbol("equation"));
        items.add(new SExp.SSymbol(":id"));
        items.add(new SExp.SString(id));
        items.add(new SExp.SSymbol(":term"));
        items.add(new SExp.SString(term));
        items.add(new SExp.SSymbol(":latex"));
        items.add(new SExp.SString(latex));
        items.add(new SExp.SSymbol(":checks"));
        List<SExp> checkItems = new ArrayList<>();
        for (Map.Entry<String, SExp> entry : checks.entrySet()) {
            checkItems.add(new SExp.SList(List.of(new SExp.SSymbol(entry.getKey()), entry.getValue())));
        }
        items.add(new SExp.SList(checkItems));
        items.add(new SExp.SSymbol(":status"));
        items.add(new SExp.SSymbol(status));
        return new SExp.SList(items);
    }

    /** @return the identifier */
    public String id() {
        return id;
    }

    /** @return the s-expression of the formula on one line */
    public String term() {
        return term;
    }

    /** @return the LaTeX the writer writes from that term */
    public String latex() {
        return latex;
    }

    /** @return the check results in order */
    public Map<String, SExp> checks() {
        return checks;
    }

    /** @return the status keyword */
    public String status() {
        return status;
    }

    @Override
    public String toString() {
        return SexpWriter.writeFlat(toSExp());
    }
}
