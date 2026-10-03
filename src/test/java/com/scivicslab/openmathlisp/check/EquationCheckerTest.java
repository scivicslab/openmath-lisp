package com.scivicslab.openmathlisp.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scivicslab.openmathlisp.write.TermWriter;
import com.scivicslab.openmathlisp.record.CheckRecordFile;
import com.scivicslab.openmathlisp.record.EquationRecord;
import com.scivicslab.openmathlisp.sexp.SexpReader;
import com.scivicslab.openmathlisp.sexp.SexpWriter;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.term.Term;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The checker with stand-ins for Maxima and Z3: what script is sent, how the output is read, and how the
 * four results become a status.
 */
@Tag("TermFileAndCheckRecord_261002_oo01")
class EquationCheckerTest {

    private static final SymbolTable SYMBOLS = SymbolTable.loadBundled();
    private static final TermFactory FACTORY = new TermFactory(SYMBOLS);
    private static final TermWriter WRITER = new TermWriter(SYMBOLS);

    private static Term term(String text) {
        return FACTORY.fromSExp(SexpReader.readOne(text));
    }

    private static Map<String, Term> fixture() {
        Map<String, Term> equations = new LinkedHashMap<>();
        equations.put("d-eq1", term("(relation1:eq (arith1:power (arith1:plus a b) 2) (arith1:plus (arith1:power a 2) (arith1:times a b) (arith1:power b 2)))"));
        equations.put("d-eq2", term("(relation1:eq (transc1:sin (arith1:times 2 x)) (arith1:times 2 (transc1:sin x) (transc1:cos x)))"));
        equations.put("d-eq3", term("(relation1:eq y (calculus1:diff (fns1:lambda (t) (x t))))"));
        return equations;
    }

    @Test
    void check_scriptsAndStatuses_fromStandInOutputs() {
        List<String> maximaScripts = new ArrayList<>();
        List<String> z3Scripts = new ArrayList<>();
        NumericChecker numeric = new NumericChecker(WRITER, (String script) -> {
            maximaScripts.add(script);
            return "### d-eq1\n21.0\n6.0\n12.0\na*b\n### d-eq2\n0.0\n0.0\n0.0\n0\n### d-eq3\ny-'diff(x(t),t,1)\n";
        });
        SmtChecker smt = new SmtChecker(WRITER, (String script) -> {
            z3Scripts.add(script);
            return "sat\n";
        });
        EquationChecker checker = new EquationChecker(new StructureChecker(SYMBOLS), numeric, smt, WRITER);
        CheckRecordFile checked = checker.check(fixture(), Set.of("x"));

        assertEquals(1, maximaScripts.size());
        assertTrue(maximaScripts.get(0).contains("print(\"### d-eq1\")$"));
        assertTrue(maximaScripts.get(0).contains("print(float(ev(((a+b)^2) - (a^2+a*b+b^2), a="), maximaScripts.get(0));
        assertTrue(maximaScripts.get(0).contains("print(ratsimp(((a+b)^2) - (a^2+a*b+b^2)))$"));
        assertEquals(1, z3Scripts.size());
        assertTrue(z3Scripts.get(0).contains("(declare-const a Real)"));
        assertTrue(z3Scripts.get(0).contains("(assert (not (= (^ (+ a b) 2) (+ (^ a 2) (* a b) (^ b 2)))))"));
        assertTrue(!z3Scripts.get(0).contains("sin"), "a trigonometric equation must not reach Z3");

        List<EquationRecord> records = checked.records();
        assertEquals(":suspect", records.get(0).status());
        assertEquals("(:failed \"a*b\")", SexpWriter.writeFlat(records.get(0).checks().get(":numeric")));
        assertEquals(":sat", SexpWriter.writeFlat(records.get(0).checks().get(":smt")));
        assertEquals(":ok", records.get(1).status());
        assertEquals(":not-checkable", SexpWriter.writeFlat(records.get(1).checks().get(":smt")));
        assertEquals(":not-checkable", records.get(2).status());
        assertTrue(records.get(0).term().startsWith("(relation1:eq"));
        assertTrue(records.get(0).latex().contains("^{2}"), records.get(0).latex());
    }

    @Test
    void check_structuralProblem_suspectWithoutRunningMaxima() {
        NumericChecker numeric = new NumericChecker(WRITER, (String script) -> {
            throw new AssertionError("maxima must not run");
        });
        SmtChecker smt = new SmtChecker(WRITER, (String script) -> {
            throw new AssertionError("z3 must not run");
        });
        Map<String, Term> equations = Map.of("d-eq1", term("(relation1:eq a (arith1:divide b))"));
        CheckRecordFile checked = new EquationChecker(new StructureChecker(SYMBOLS), numeric, smt, WRITER).check(equations, Set.of());
        assertEquals(":suspect", checked.records().get(0).status());
        assertTrue(SexpWriter.writeFlat(checked.records().get(0).checks().get(":binders")).contains("arith1:divide expects 2"));
    }

    @Test
    void check_undeclaredDependence_notCheckableNotSuspect() {
        NumericChecker numeric = new NumericChecker(WRITER, (String script) -> {
            throw new AssertionError("maxima must not run");
        });
        SmtChecker smt = new SmtChecker(WRITER, (String script) -> {
            throw new AssertionError("z3 must not run");
        });
        Map<String, Term> equations = Map.of("d-eq1",
                term("(relation1:eq (arith1:times m (calculus1:nthdiff 2 (fns1:lambda (t) x))) 0)"));
        CheckRecordFile checked = new EquationChecker(new StructureChecker(SYMBOLS), numeric, smt, WRITER).check(equations, Set.of());
        assertEquals(":not-checkable", checked.records().get(0).status());
        assertTrue(SexpWriter.writeFlat(checked.records().get(0).checks().get(":binders")).startsWith("(:not-checkable"));
    }

    @Test
    void numericCheck_variableOnOneSideOnly_notCheckableWithoutMaxima() {
        NumericChecker numeric = new NumericChecker(WRITER, (String script) -> {
            throw new AssertionError("maxima must not run for a definition");
        });
        Map<String, Term> equations = new LinkedHashMap<>();
        equations.put("def", term("(relation1:eq R (arith1:divide 1 n))"));
        equations.put("rel", term("(relation1:eq n k)"));
        Map<String, NumericChecker.Result> results = numeric.check(equations);
        assertTrue(results.get("def") instanceof NumericChecker.NotCheckable);
        assertTrue(results.get("rel") instanceof NumericChecker.NotCheckable);
    }

    @Test
    void numericCheck_constantSide_notCheckable() {
        NumericChecker numeric = new NumericChecker(WRITER, (String script) -> {
            throw new AssertionError("maxima must not run for a condition");
        });
        Map<String, Term> equations = Map.of("cond", term("(relation1:eq (arith1:plus A_1 (arith1:times k A_0)) 0)"));
        assertTrue(numeric.check(equations).get("cond") instanceof NumericChecker.NotCheckable);
    }

    @Test
    void status_combinations_followTheStatusTable() {
        assertEquals(":ok", EquationChecker.status(new NumericChecker.Ok(), ":unsat"));
        assertEquals(":ok", EquationChecker.status(new NumericChecker.Ok(), ":not-checkable"));
        assertEquals(":suspect", EquationChecker.status(new NumericChecker.Ok(), ":sat"));
        assertEquals(":suspect", EquationChecker.status(new NumericChecker.Failed("a*b"), ":not-checkable"));
        assertEquals(":not-checkable", EquationChecker.status(new NumericChecker.NotCheckable("x(t)"), ":not-checkable"));
        assertEquals(":ok", EquationChecker.status(new NumericChecker.NotCheckable("maxima unavailable or timed out"), ":unsat"));
    }

    @Test
    void numericCheck_sameId_sameSubstitutionsEveryRun() {
        List<String> scripts = new ArrayList<>();
        NumericChecker numeric = new NumericChecker(WRITER, (String script) -> {
            scripts.add(script);
            return "";
        });
        Map<String, Term> equations = Map.of("d-eq1", fixture().get("d-eq1"));
        numeric.check(equations);
        numeric.check(equations);
        assertEquals(scripts.get(0), scripts.get(1));
    }
}
