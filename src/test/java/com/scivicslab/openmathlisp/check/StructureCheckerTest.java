package com.scivicslab.openmathlisp.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scivicslab.openmathlisp.sexp.SexpReader;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The structural check: arity against the table, bound variables in bodies, free variables in binder bodies. */
@Tag("TermFileAndCheckRecord_261002_oo01")
class StructureCheckerTest {

    private static final SymbolTable SYMBOLS = SymbolTable.loadBundled();
    private static final TermFactory FACTORY = new TermFactory(SYMBOLS);
    private static final StructureChecker CHECKER = new StructureChecker(SYMBOLS);

    private static List<String> check(String text) {
        return CHECKER.check(FACTORY.fromSExp(SexpReader.readOne(text)), Set.of()).stream()
                .map(StructureChecker.Problem::message).toList();
    }

    @Test
    void check_wellFormedIntegral_noProblem() {
        assertTrue(check("(relation1:eq B (calculus1:defint (interval1:interval 0 T) (fns1:lambda (t) (arith1:times B t))))").isEmpty());
    }

    @Test
    void check_wrongArity_reportsSymbolAndCounts() {
        assertEquals(List.of("arith1:divide expects 2 argument(s), given 3"), check("(arith1:divide a b c)"));
    }

    @Test
    void check_boundVariableAbsentFromBody_reported() {
        List<String> problems = check("(relation1:eq (arith1:times a b) (calculus1:int (fns1:lambda (x) (arith1:times a b))))");
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).startsWith("bound variable x does not occur"));
    }

    @Test
    void check_variableOnlyInsideBinderBody_reportedUnlessDeclaredFunction() {
        String text = "(relation1:eq y (calculus1:diff (fns1:lambda (t) (arith1:times t q))))";
        assertEquals(List.of("variable q is free in a binder body and occurs nowhere else"), check(text));
        assertEquals(StructureChecker.Kind.DEPENDENCE, CHECKER.check(FACTORY.fromSExp(SexpReader.readOne(text)), Set.of()).get(0).kind());
        assertTrue(CHECKER.check(FACTORY.fromSExp(SexpReader.readOne(text)), Set.of("q")).isEmpty());
    }
}
