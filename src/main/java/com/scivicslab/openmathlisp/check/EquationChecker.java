package com.scivicslab.openmathlisp.check;

import com.scivicslab.openmathlisp.record.CheckRecordFile;
import com.scivicslab.openmathlisp.record.EquationRecord;
import com.scivicslab.openmathlisp.write.TermWriterException;
import com.scivicslab.openmathlisp.write.TermWriter;
import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.sexp.SexpWriter;
import com.scivicslab.openmathlisp.symbols.InputFormat;
import com.scivicslab.openmathlisp.term.Term;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the four checks on the formulas of one markdown file and gives each a status. The structural check
 * runs in this process; the numeric and SMT checks hand one script per file to Maxima and to Z3.
 */
public final class EquationChecker {

    private final StructureChecker structure;
    private final NumericChecker numeric;
    private final SmtChecker smt;
    private final TermWriter writer;

    /**
     * Creates the checker.
     * @param structure the structural check
     * @param numeric the Maxima check
     * @param smt the Z3 check
     * @param writer writes each formula's LaTeX into its record, so that the written LaTeX can be compared
     *                  with what the OCR produced
     */
    public EquationChecker(StructureChecker structure, NumericChecker numeric, SmtChecker smt, TermWriter writer) {
        this.structure = structure;
        this.numeric = numeric;
        this.smt = smt;
        this.writer = writer;
    }

    /**
     * Checks every formula.
     * @param equations identifier to term, in file order
     * @param knownFunctions the names the file's declaration lists as functions
     * @return the check records in the same order
     */
    public CheckRecordFile check(Map<String, Term> equations, Set<String> knownFunctions) {
        Map<String, List<StructureChecker.Problem>> structureProblems = new LinkedHashMap<>();
        Map<String, Term> wellFormed = new LinkedHashMap<>();
        for (Map.Entry<String, Term> entry : equations.entrySet()) {
            List<StructureChecker.Problem> problems = structure.check(entry.getValue(), knownFunctions);
            structureProblems.put(entry.getKey(), problems);
            if (problems.isEmpty()) {
                wellFormed.put(entry.getKey(), entry.getValue());
            }
        }
        Map<String, NumericChecker.Result> numericResults = numeric.check(wellFormed);
        Map<String, String> smtResults = smt.check(wellFormed);
        List<EquationRecord> records = new ArrayList<>();
        for (Map.Entry<String, Term> entry : equations.entrySet()) {
            String id = entry.getKey();
            String term = SexpWriter.writeFlat(TermFactory.toSExp(entry.getValue()));
            String latex = writeOrReason(entry.getValue());
            Map<String, SExp> checks = new LinkedHashMap<>();
            checks.put(":parse", keyword(":ok"));
            List<StructureChecker.Problem> problems = structureProblems.get(id);
            if (!problems.isEmpty()) {
                boolean arity = problems.stream().anyMatch((StructureChecker.Problem p) -> p.kind() == StructureChecker.Kind.ARITY);
                List<String> messages = problems.stream().map(StructureChecker.Problem::message).toList();
                checks.put(":binders", new SExp.SList(List.of(keyword(arity ? ":failed" : ":not-checkable"),
                        new SExp.SString(String.join("; ", messages)))));
                checks.put(":numeric", keyword(":skipped"));
                checks.put(":smt", keyword(":skipped"));
                records.add(new EquationRecord(id, term, latex, checks, arity ? ":suspect" : ":not-checkable"));
                continue;
            }
            checks.put(":binders", keyword(":ok"));
            NumericChecker.Result numericResult = numericResults.get(id);
            String smtResult = smtResults.get(id);
            checks.put(":numeric", switch (numericResult) {
                case NumericChecker.Ok ok -> keyword(":ok");
                case NumericChecker.Failed failed -> new SExp.SList(List.of(keyword(":failed"), new SExp.SString(failed.difference())));
                case NumericChecker.NotCheckable notCheckable -> new SExp.SList(List.of(keyword(":not-checkable"), new SExp.SString(notCheckable.why())));
            });
            checks.put(":smt", keyword(smtResult));
            records.add(new EquationRecord(id, term, latex, checks, status(numericResult, smtResult)));
        }
        return new CheckRecordFile(records);
    }

    /**
     * Gives the status of one formula from its numeric and SMT results.
     * @param numericResult what Maxima said
     * @param smtResult what Z3 said
     * @return the status keyword
     */
    public static String status(NumericChecker.Result numericResult, String smtResult) {
        if (numericResult instanceof NumericChecker.Failed || smtResult.equals(":sat")) {
            return ":suspect";
        }
        if (numericResult instanceof NumericChecker.Ok || smtResult.equals(":unsat")) {
            return ":ok";
        }
        return ":not-checkable";
    }

    /** The formula's LaTeX, or the reason the writer could not write it. */
    private String writeOrReason(Term term) {
        try {
            return writer.write(term, InputFormat.LATEX);
        } catch (TermWriterException e) {
            return "(" + e.getMessage() + ")";
        }
    }

    private static SExp.SSymbol keyword(String name) {
        return new SExp.SSymbol(name);
    }
}
