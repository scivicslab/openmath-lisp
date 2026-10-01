package com.scivicslab.openmathlisp.e2e;

import com.scivicslab.openmathlisp.check.ExternalProcess;
import com.scivicslab.openmathlisp.check.NumericChecker;
import com.scivicslab.openmathlisp.check.SmtChecker;
import com.scivicslab.openmathlisp.project.Projector;
import com.scivicslab.openmathlisp.sexp.SexpReader;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.term.Term;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * End-to-end check against the installed Maxima and Z3 (ToolchainInstalled_261002_oo01). Not a JUnit test:
 * it needs the programs on the PATH. Run with
 * {@code java -cp target/classes:target/test-classes com.scivicslab.openmathlisp.e2e.ToolchainE2E}.
 * Exit code 0 when the Concept's broken identity is reported suspect by both tools and the correct one is ok.
 */
public final class ToolchainE2E {

    private ToolchainE2E() {
    }

    /**
     * Runs the check.
     * @param args unused
     */
    public static void main(String[] args) {
        SymbolTable symbols = SymbolTable.loadBundled();
        TermFactory factory = new TermFactory(symbols);
        Projector projector = new Projector(symbols);
        NumericChecker numeric = new NumericChecker(projector, (String script) ->
                ExternalProcess.run(List.of("maxima", "--very-quiet"), script, 120));
        SmtChecker smt = new SmtChecker(projector, (String script) ->
                ExternalProcess.run(List.of("z3", "-in", "-t:5000"), script, 60));
        Map<String, Term> equations = new LinkedHashMap<>();
        equations.put("broken", factory.fromSExp(SexpReader.readOne(
                "(relation1:eq (arith1:power (arith1:plus a b) 2) (arith1:plus (arith1:power a 2) (arith1:times a b) (arith1:power b 2)))")));
        equations.put("correct", factory.fromSExp(SexpReader.readOne(
                "(relation1:eq (arith1:power (arith1:plus a b) 2) (arith1:plus (arith1:power a 2) (arith1:times 2 a b) (arith1:power b 2)))")));
        equations.put("trig", factory.fromSExp(SexpReader.readOne(
                "(relation1:eq (transc1:sin (arith1:times 2 x)) (arith1:times 2 (transc1:sin x) (transc1:cos x)))")));
        Map<String, NumericChecker.Result> numericResults = numeric.check(equations);
        Map<String, String> smtResults = smt.check(equations);
        boolean ok = true;
        for (String id : equations.keySet()) {
            System.out.println(id + ": numeric=" + numericResults.get(id) + " smt=" + smtResults.get(id));
        }
        ok &= numericResults.get("broken") instanceof NumericChecker.Failed failed && failed.difference().contains("a*b");
        ok &= smtResults.get("broken").equals(":sat");
        ok &= numericResults.get("correct") instanceof NumericChecker.Ok;
        ok &= smtResults.get("correct").equals(":unsat");
        ok &= numericResults.get("trig") instanceof NumericChecker.Ok;
        ok &= smtResults.get("trig").equals(":not-checkable");
        System.out.println(ok ? "E2E OK" : "E2E FAILED");
        System.exit(ok ? 0 : 1);
    }
}
