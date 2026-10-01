package com.scivicslab.openmathlisp.check;

import com.scivicslab.openmathlisp.record.EquationRecord;
import com.scivicslab.openmathlisp.record.TermFile;
import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.term.Term;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the four checks on every record of a term file and sets the status
 * (TermFileAndCheckRecord_261002_oo01, decisions 2 and 5).
 */
public final class EquationChecker {

    private final StructureChecker structure;
    private final NumericChecker numeric;
    private final SmtChecker smt;

    /**
     * Creates the checker.
     * @param structure the structural check
     * @param numeric the Maxima check
     * @param smt the Z3 check
     */
    public EquationChecker(StructureChecker structure, NumericChecker numeric, SmtChecker smt) {
        this.structure = structure;
        this.numeric = numeric;
        this.smt = smt;
    }

    /**
     * Checks every record.
     * @param file the term file
     * @return the file with checks and statuses filled in
     */
    public TermFile check(TermFile file) {
        Set<String> knownFunctions = file.declaration().functions().keySet();
        Map<String, Term> forNumeric = new LinkedHashMap<>();
        Map<String, List<StructureChecker.Problem>> structureProblems = new LinkedHashMap<>();
        for (EquationRecord record : file.records()) {
            if (record.term().isEmpty()) {
                continue;
            }
            List<StructureChecker.Problem> problems = structure.check(record.term().get(), knownFunctions);
            structureProblems.put(record.id(), problems);
            if (problems.isEmpty()) {
                forNumeric.put(record.id(), record.term().get());
            }
        }
        Map<String, NumericChecker.Result> numericResults = numeric.check(forNumeric);
        Map<String, String> smtResults = smt.check(forNumeric);
        List<EquationRecord> checked = new ArrayList<>();
        for (EquationRecord record : file.records()) {
            if (record.term().isEmpty()) {
                Map<String, SExp> checks = new LinkedHashMap<>(record.checks());
                checks.put(":binders", keyword(":skipped"));
                checks.put(":numeric", keyword(":skipped"));
                checks.put(":smt", keyword(":skipped"));
                checked.add(record.withChecks(checks, ":unparseable"));
                continue;
            }
            Map<String, SExp> checks = new LinkedHashMap<>();
            checks.put(":parse", keyword(":ok"));
            List<StructureChecker.Problem> problems = structureProblems.get(record.id());
            if (!problems.isEmpty()) {
                boolean arity = problems.stream().anyMatch((StructureChecker.Problem p) -> p.kind() == StructureChecker.Kind.ARITY);
                List<String> messages = problems.stream().map(StructureChecker.Problem::message).toList();
                checks.put(":binders", new SExp.SList(List.of(keyword(arity ? ":failed" : ":not-checkable"), new SExp.SString(String.join("; ", messages)))));
                checks.put(":numeric", keyword(":skipped"));
                checks.put(":smt", keyword(":skipped"));
                checked.add(record.withChecks(checks, arity ? ":suspect" : ":not-checkable"));
                continue;
            }
            checks.put(":binders", keyword(":ok"));
            NumericChecker.Result numericResult = numericResults.get(record.id());
            String smtResult = smtResults.get(record.id());
            checks.put(":numeric", switch (numericResult) {
                case NumericChecker.Ok ok -> keyword(":ok");
                case NumericChecker.Failed failed -> new SExp.SList(List.of(keyword(":failed"), new SExp.SString(failed.difference())));
                case NumericChecker.NotCheckable notCheckable -> new SExp.SList(List.of(keyword(":not-checkable"), new SExp.SString(notCheckable.why())));
            });
            checks.put(":smt", keyword(smtResult));
            checked.add(record.withChecks(checks, status(numericResult, smtResult)));
        }
        return file.withRecords(checked);
    }

    static String status(NumericChecker.Result numericResult, String smtResult) {
        if (numericResult instanceof NumericChecker.Failed || smtResult.equals(":sat")) {
            return ":suspect";
        }
        if (numericResult instanceof NumericChecker.Ok && (smtResult.equals(":unsat") || smtResult.equals(":not-checkable"))) {
            return ":ok";
        }
        if (smtResult.equals(":unsat")) {
            return ":ok";
        }
        return ":not-checkable";
    }

    private static SExp.SSymbol keyword(String name) {
        return new SExp.SSymbol(name);
    }
}
