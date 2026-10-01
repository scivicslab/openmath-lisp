package com.scivicslab.openmathlisp.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scivicslab.openmathlisp.project.Projector;
import com.scivicslab.openmathlisp.record.TermFile;
import com.scivicslab.openmathlisp.sexp.SexpWriter;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The checker with stand-ins for Maxima and Z3: what script is sent, how the output is read, and how the
 * four results become a status (TermFileAndCheckRecord_261002_oo01, decisions 3 to 5).
 */
@Tag("TermFileAndCheckRecord_261002_oo01")
class EquationCheckerTest {

    private static final SymbolTable SYMBOLS = SymbolTable.loadBundled();
    private static final TermFactory FACTORY = new TermFactory(SYMBOLS);
    private static final Projector PROJECTOR = new Projector(SYMBOLS);

    private static final String FILE = "(declare :time t :functions ((x t)))\n"
            + "(equation :id \"d-eq1\" :source \"\" :term (relation1:eq (arith1:power (arith1:plus a b) 2) (arith1:plus (arith1:power a 2) (arith1:times a b) (arith1:power b 2))) :checks () :status :not-checkable)\n"
            + "(equation :id \"d-eq2\" :source \"\" :term (relation1:eq (transc1:sin (arith1:times 2 x)) (arith1:times 2 (transc1:sin x) (transc1:cos x))) :checks () :status :not-checkable)\n"
            + "(equation :id \"d-eq3\" :source \"\" :term (relation1:eq y (calculus1:diff (fns1:lambda (t) (x t)))) :checks () :status :not-checkable)\n"
            + "(equation :id \"d-eq4\" :source \"\" :term nil :checks ((:parse (:failed \"ellipsis\" 3))) :status :unparseable)\n";

    @Test
    void check_scriptsAndStatuses_fromStandInOutputs() {
        List<String> maximaScripts = new ArrayList<>();
        List<String> z3Scripts = new ArrayList<>();
        NumericChecker numeric = new NumericChecker(PROJECTOR, (String script) -> {
            maximaScripts.add(script);
            return "### d-eq1\n21.0\n6.0\n12.0\na*b\n### d-eq2\n0.0\n0.0\n0.0\n0\n### d-eq3\ny-'diff(x(t),t,1)\n";
        });
        SmtChecker smt = new SmtChecker(PROJECTOR, (String script) -> {
            z3Scripts.add(script);
            return "sat\n";
        });
        EquationChecker checker = new EquationChecker(new StructureChecker(SYMBOLS), numeric, smt);
        TermFile checked = checker.check(TermFile.parse(FILE, FACTORY));

        assertEquals(1, maximaScripts.size());
        assertTrue(maximaScripts.get(0).contains("print(\"### d-eq1\")$"));
        assertTrue(maximaScripts.get(0).contains("print(float(ev(((a+b)^2) - (a^2+a*b+b^2), a="), maximaScripts.get(0));
        assertTrue(maximaScripts.get(0).contains("print(ratsimp(((a+b)^2) - (a^2+a*b+b^2)))$"));
        assertEquals(1, z3Scripts.size());
        assertTrue(z3Scripts.get(0).contains("(declare-const a Real)"));
        assertTrue(z3Scripts.get(0).contains("(assert (not (= (^ (+ a b) 2) (+ (^ a 2) (* a b) (^ b 2)))))"));
        assertTrue(!z3Scripts.get(0).contains("sin"), "trigonometric equation must not reach Z3");

        assertEquals(":suspect", checked.records().get(0).status());
        assertEquals("(:failed \"a*b\")", SexpWriter.writeFlat(checked.records().get(0).checks().get(":numeric")));
        assertEquals(":sat", SexpWriter.writeFlat(checked.records().get(0).checks().get(":smt")));
        assertEquals(":ok", checked.records().get(1).status());
        assertEquals(":not-checkable", SexpWriter.writeFlat(checked.records().get(1).checks().get(":smt")));
        assertEquals(":not-checkable", checked.records().get(2).status());
        assertEquals(":unparseable", checked.records().get(3).status());
        assertEquals(":skipped", SexpWriter.writeFlat(checked.records().get(3).checks().get(":numeric")));
    }

    @Test
    void check_structuralProblem_suspectWithoutRunningMaxima() {
        String file = "(declare)\n(equation :id \"d-eq1\" :source \"\" :term (relation1:eq a (arith1:divide b)) :checks () :status :not-checkable)\n";
        NumericChecker numeric = new NumericChecker(PROJECTOR, (String script) -> {
            throw new AssertionError("maxima must not run");
        });
        SmtChecker smt = new SmtChecker(PROJECTOR, (String script) -> {
            throw new AssertionError("z3 must not run");
        });
        TermFile checked = new EquationChecker(new StructureChecker(SYMBOLS), numeric, smt).check(TermFile.parse(file, FACTORY));
        assertEquals(":suspect", checked.records().get(0).status());
        assertTrue(SexpWriter.writeFlat(checked.records().get(0).checks().get(":binders")).contains("arith1:divide expects 2"));
    }

    @Test
    void check_undeclaredDependence_notCheckableNotSuspect() {
        String file = "(declare)\n(equation :id \"d-eq1\" :source \"\" :term (relation1:eq (arith1:times m (calculus1:nthdiff 2 (fns1:lambda (t) x))) 0) :checks () :status :not-checkable)\n";
        NumericChecker numeric = new NumericChecker(PROJECTOR, (String script) -> {
            throw new AssertionError("maxima must not run");
        });
        SmtChecker smt = new SmtChecker(PROJECTOR, (String script) -> {
            throw new AssertionError("z3 must not run");
        });
        TermFile checked = new EquationChecker(new StructureChecker(SYMBOLS), numeric, smt).check(TermFile.parse(file, FACTORY));
        assertEquals(":not-checkable", checked.records().get(0).status());
        assertTrue(SexpWriter.writeFlat(checked.records().get(0).checks().get(":binders")).startsWith("(:not-checkable"));
    }

    @Test
    void numericCheck_constantSide_notCheckable() {
        NumericChecker numeric = new NumericChecker(PROJECTOR, (String script) -> {
            throw new AssertionError("maxima must not run for a condition");
        });
        java.util.Map<String, com.scivicslab.openmathlisp.term.Term> equations = new java.util.LinkedHashMap<>();
        equations.put("cond", FACTORY.fromSExp(com.scivicslab.openmathlisp.sexp.SexpReader.readOne("(relation1:eq (arith1:plus A_1 (arith1:times k A_0)) 0)")));
        assertTrue(numeric.check(equations).get("cond") instanceof NumericChecker.NotCheckable);
    }

    @Test
    void status_combinations_followDecisionFive() {
        assertEquals(":ok", EquationChecker.status(new NumericChecker.Ok(), ":unsat"));
        assertEquals(":ok", EquationChecker.status(new NumericChecker.Ok(), ":not-checkable"));
        assertEquals(":suspect", EquationChecker.status(new NumericChecker.Ok(), ":sat"));
        assertEquals(":suspect", EquationChecker.status(new NumericChecker.Failed("a*b"), ":not-checkable"));
        assertEquals(":not-checkable", EquationChecker.status(new NumericChecker.NotCheckable("x(t)"), ":not-checkable"));
        assertEquals(":ok", EquationChecker.status(new NumericChecker.NotCheckable("maxima unavailable"), ":unsat"));
    }

    @Test
    void numericCheck_variableOnOneSideOnly_notCheckableWithoutMaxima() {
        NumericChecker numeric = new NumericChecker(PROJECTOR, (String script) -> {
            throw new AssertionError("maxima must not run for a definition");
        });
        java.util.Map<String, com.scivicslab.openmathlisp.term.Term> equations = new java.util.LinkedHashMap<>();
        equations.put("def", FACTORY.fromSExp(com.scivicslab.openmathlisp.sexp.SexpReader.readOne("(relation1:eq R (arith1:divide 1 n))")));
        equations.put("rel", FACTORY.fromSExp(com.scivicslab.openmathlisp.sexp.SexpReader.readOne("(relation1:eq n k)")));
        java.util.Map<String, NumericChecker.Result> results = numeric.check(equations);
        assertTrue(results.get("def") instanceof NumericChecker.NotCheckable);
        assertTrue(results.get("rel") instanceof NumericChecker.NotCheckable);
    }

    @Test
    void numericCheck_sameId_sameSubstitutionsEveryRun() {
        List<String> scripts = new ArrayList<>();
        NumericChecker numeric = new NumericChecker(PROJECTOR, (String script) -> {
            scripts.add(script);
            return "";
        });
        TermFile file = TermFile.parse(FILE, FACTORY);
        numeric.check(java.util.Map.of("d-eq1", file.records().get(0).term().get()));
        numeric.check(java.util.Map.of("d-eq1", file.records().get(0).term().get()));
        assertEquals(scripts.get(0), scripts.get(1));
    }
}
