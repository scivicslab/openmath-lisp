package com.scivicslab.openmathlisp.record;

import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.sexp.SexpWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One check record: the results of the four checks on one formula of a markdown file, and the status they
 * give it. The term itself is not here; it is in the om block the identifier names.
 */
public final class EquationRecord {

    private final String id;
    private final String source;
    private final Map<String, SExp> checks;
    private final String status;

    /**
     * Creates a record.
     * @param id the identifier of the om block
     * @param source the s-expression of that block on one line
     * @param checks check name (e.g. {@code :parse}) to result, in check order
     * @param status one of {@code :ok}, {@code :suspect}, {@code :unparseable}, {@code :not-checkable}
     */
    public EquationRecord(String id, String source, Map<String, SExp> checks, String status) {
        this.id = id;
        this.source = source;
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
        String source = fields.containsKey(":source") ? ((SExp.SString) fields.get(":source")).value() : "";
        Map<String, SExp> checks = new LinkedHashMap<>();
        if (fields.get(":checks") instanceof SExp.SList checkList) {
            for (SExp pair : checkList.items()) {
                SExp.SList entry = (SExp.SList) pair;
                checks.put(((SExp.SSymbol) entry.items().get(0)).name(), entry.items().get(1));
            }
        }
        String status = fields.containsKey(":status") ? ((SExp.SSymbol) fields.get(":status")).name() : ":not-checkable";
        return new EquationRecord(id, source, checks, status);
    }

    /** @return the s-expression {@code (equation ...)} */
    public SExp toSExp() {
        List<SExp> items = new ArrayList<>();
        items.add(new SExp.SSymbol("equation"));
        items.add(new SExp.SSymbol(":id"));
        items.add(new SExp.SString(id));
        items.add(new SExp.SSymbol(":source"));
        items.add(new SExp.SString(source));
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
    public String source() {
        return source;
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
