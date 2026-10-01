package com.scivicslab.openmathlisp.record;

import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.term.Term;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One check record: an equation read from a markdown block, its term, the results of the four checks and
 * the status (TermFileAndCheckRecord_261002_oo01, decision 2). Written as
 * {@code (equation :id ... :source ... :term ... :checks ((:parse :ok) ...) :status :ok)}.
 */
public final class EquationRecord {

    private final String id;
    private final Optional<String> tag;
    private final String source;
    private final Optional<Term> term;
    private final boolean edited;
    private final Map<String, SExp> checks;
    private final String status;

    /**
     * Creates a record.
     * @param id the equation identifier
     * @param tag the equation number from the book, if any
     * @param source the LaTeX text the equation came from
     * @param term the term, empty when the reader failed
     * @param edited true when a person edited the term and the reader must keep it
     * @param checks check name (e.g. {@code :parse}) to result, in check order
     * @param status one of {@code :ok}, {@code :suspect}, {@code :unparseable}, {@code :not-checkable}
     */
    public EquationRecord(String id, Optional<String> tag, String source, Optional<Term> term, boolean edited,
                          Map<String, SExp> checks, String status) {
        this.id = id;
        this.tag = tag;
        this.source = source;
        this.term = term;
        this.edited = edited;
        this.checks = new LinkedHashMap<>(checks);
        this.status = status;
    }

    /**
     * Reads a record from its s-expression.
     * @param expression the {@code (equation ...)} form
     * @param factory the factory that turns the {@code :term} value into a term
     * @return the record
     */
    public static EquationRecord fromSExp(SExp expression, TermFactory factory) {
        if (!(expression instanceof SExp.SList list) || list.items().isEmpty()
                || !(list.items().get(0) instanceof SExp.SSymbol head) || !head.name().equals("equation")) {
            throw new IllegalArgumentException("not an (equation ...) form: " + expression);
        }
        Map<String, SExp> fields = new LinkedHashMap<>();
        for (int i = 1; i + 1 < list.items().size(); i += 2) {
            fields.put(((SExp.SSymbol) list.items().get(i)).name(), list.items().get(i + 1));
        }
        String id = ((SExp.SString) fields.get(":id")).value();
        Optional<String> tag = fields.containsKey(":tag") ? Optional.of(((SExp.SString) fields.get(":tag")).value()) : Optional.empty();
        String source = fields.containsKey(":source") ? ((SExp.SString) fields.get(":source")).value() : "";
        Optional<Term> term = Optional.empty();
        SExp termValue = fields.get(":term");
        if (termValue != null && !(termValue instanceof SExp.SSymbol nil && nil.isNil())) {
            term = Optional.of(factory.fromSExp(termValue));
        }
        boolean edited = fields.containsKey(":edited") && fields.get(":edited") instanceof SExp.SSymbol flag && flag.name().equals("t");
        Map<String, SExp> checks = new LinkedHashMap<>();
        if (fields.get(":checks") instanceof SExp.SList checkList) {
            for (SExp pair : checkList.items()) {
                SExp.SList entry = (SExp.SList) pair;
                checks.put(((SExp.SSymbol) entry.items().get(0)).name(), entry.items().get(1));
            }
        }
        String status = fields.containsKey(":status") ? ((SExp.SSymbol) fields.get(":status")).name() : ":not-checkable";
        return new EquationRecord(id, tag, source, term, edited, checks, status);
    }

    /** @return the s-expression {@code (equation ...)} */
    public SExp toSExp() {
        List<SExp> items = new ArrayList<>();
        items.add(new SExp.SSymbol("equation"));
        items.add(new SExp.SSymbol(":id"));
        items.add(new SExp.SString(id));
        if (tag.isPresent()) {
            items.add(new SExp.SSymbol(":tag"));
            items.add(new SExp.SString(tag.get()));
        }
        items.add(new SExp.SSymbol(":source"));
        items.add(new SExp.SString(source));
        items.add(new SExp.SSymbol(":term"));
        items.add(term.map(TermFactory::toSExp).orElse(new SExp.SSymbol("nil")));
        if (edited) {
            items.add(new SExp.SSymbol(":edited"));
            items.add(new SExp.SSymbol("t"));
        }
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

    /**
     * Returns a copy with new checks and status.
     * @param newChecks the check results
     * @param newStatus the status
     * @return the copy
     */
    public EquationRecord withChecks(Map<String, SExp> newChecks, String newStatus) {
        return new EquationRecord(id, tag, source, term, edited, newChecks, newStatus);
    }

    /**
     * Returns a copy that keeps this record's term and edited flag but takes the source and tag of a fresh reading.
     * @param fresh the record the reader produced for the same id
     * @return the copy
     */
    public EquationRecord keepingTermOver(EquationRecord fresh) {
        return new EquationRecord(id, fresh.tag, fresh.source, term, edited, checks, status);
    }

    /** @return the identifier */
    public String id() {
        return id;
    }

    /** @return the equation number from the book */
    public Optional<String> tag() {
        return tag;
    }

    /** @return the LaTeX source */
    public String source() {
        return source;
    }

    /** @return the term, empty when unreadable */
    public Optional<Term> term() {
        return term;
    }

    /** @return true when a person edited the term */
    public boolean edited() {
        return edited;
    }

    /** @return the check results in order */
    public Map<String, SExp> checks() {
        return checks;
    }

    /** @return the status keyword */
    public String status() {
        return status;
    }
}
