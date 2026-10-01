package com.scivicslab.openmathlisp.latex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scivicslab.openmathlisp.record.Declaration;
import com.scivicslab.openmathlisp.sexp.SexpReader;
import com.scivicslab.openmathlisp.sexp.SexpWriter;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The LaTeX reader on the four Slater equations of the grammar document and on the block-splitting rules. */
@Tag("LatexReaderDecisions_261002_oo01")
class LatexBlockReaderTest {

    private static final Declaration PHYSICS = Declaration.fromSExp(SexpReader.readOne(
            "(declare :time t :imaginary i :euler e :functions ((x t) (f t) (u r t)))"));

    private static List<String> terms(String block, Declaration declaration) {
        LatexBlockReader.BlockResult result = LatexBlockReader.read(block, declaration);
        return result.equations().stream().map((LatexBlockReader.EquationResult r) ->
                r.term().map((com.scivicslab.openmathlisp.term.Term t) -> SexpWriter.writeFlat(TermFactory.toSExp(t)))
                        .orElseGet(() -> "FAILED " + r.failure().get().getReason())).toList();
    }

    @Test
    void read_divergenceEquation_veccalcSymbolAndPi() {
        assertEquals(List.of("(relation1:eq (veccalc1:divergence (arith1:plus E (arith1:times 4 nums1:pi P))) (arith1:times 4 nums1:pi rho))"),
                terms("\\operatorname{div} (E + 4\\pi P) = 4\\pi \\rho.", Declaration.empty()));
    }

    @Test
    void read_fourierCoefficient_definiteIntegralStopsAtDifferential() {
        assertEquals(List.of("(relation1:eq B_n (arith1:times (arith1:divide 2 T) (calculus1:defint (interval1:interval (arith1:unary_minus (arith1:divide T 2)) (arith1:divide T 2)) (fns1:lambda (t) (arith1:times (f t) (transc1:sin (arith1:times omega_n t)))))))"),
                terms("B_{n} = \\frac{2}{T} \\int_{-T/2}^{T/2} f(t) \\sin \\omega_{n} t dt,", PHYSICS));
    }

    @Test
    void read_dampedOscillator_dotsAndEulerAndImaginaryFromDeclaration() {
        assertEquals(List.of("(relation1:eq (arith1:plus (arith1:times m (calculus1:nthdiff 2 (fns1:lambda (t) (x t)))) (arith1:times m g (calculus1:diff (fns1:lambda (t) (x t)))) (arith1:times (arith1:power omega_0 2) m (x t))) (arith1:times c (arith1:power E_x 0) (transc1:exp (arith1:times nums1:i omega t))))"),
                terms("m\\ddot{x} + mg\\dot{x} + \\omega_0^2 mx = cE_x^0 e^{i\\omega t}.", PHYSICS));
    }

    @Test
    void read_waveEquation_partialDerivativesWithDegrees() {
        assertEquals(List.of("(relation1:eq (arith1:minus (calculus1:partialdiff (list1:list 1 1) (fns1:lambda (r) (arith1:times r (u r t)))) (arith1:times (arith1:divide 1 (arith1:power r 2)) (calculus1:partialdiff (list1:list 1 1) (fns1:lambda (t) (arith1:times r (u r t)))))) 0)"),
                terms("\\frac{\\partial^2(ru)}{\\partial r^2} - \\frac{1}{r^2} \\frac{\\partial^2(ru)}{\\partial t^2} = 0.", PHYSICS));
    }

    @Test
    void read_relationChain_splitsIntoPairs() {
        assertEquals(List.of("(relation1:eq a (arith1:plus b c))", "(relation1:eq (arith1:plus b c) (arith1:times 2 d))"),
                terms("a = b + c = 2d", Declaration.empty()));
    }

    @Test
    void read_splitEnvironmentWithContinuation_usesPreviousRightSide() {
        assertEquals(List.of("(relation1:eq q (arith1:times A t))", "(relation1:eq (arith1:times A t) (arith1:plus B 1))"),
                terms("\\begin{split} q &= A t \\\\ &= B + 1 \\end{split}", Declaration.empty()));
    }

    @Test
    void read_tagAndTrailingNumber_becomeTheTag() {
        assertEquals("17", LatexBlockReader.read("x = 1 \\tag{17}", Declaration.empty()).tag().get());
        assertEquals("15", LatexBlockReader.read("\\frac{a}{b}.\t(15)", Declaration.empty()).tag().get());
    }

    @Test
    void read_unsupportedConstructs_reportReasonCodes() {
        assertEquals(List.of("FAILED ellipsis"), terms("1 + \\frac{1}{2} + \\cdots = 2", Declaration.empty()));
        assertEquals(List.of("FAILED sum-without-bounds"), terms("F = \\sum_{n} F_n", Declaration.empty()));
        assertEquals(List.of("FAILED dot-without-time"), terms("v = \\dot{x}", Declaration.empty()));
        assertEquals(List.of("FAILED text"), terms("E = \\text{const}", Declaration.empty()));
        assertEquals(List.of("FAILED non-ascii"), terms("E = 定数", Declaration.empty()));
        assertEquals(List.of("FAILED syntax"), terms("a + = b", Declaration.empty()));
    }

    @Test
    void read_primesAndTopLevelComma_twoEquationsWithPrimedNames() {
        assertEquals(List.of("(relation1:eq (arith1:divide E'' E) (arith1:divide (arith1:minus n' 1) (arith1:plus n' 1)))",
                        "(relation1:eq (veccalc1:divergence H) 0)"),
                terms("\\frac{E''}{E} = \\frac{n'-1}{n'+1}, \\quad \\operatorname{div} H = 0", Declaration.empty()));
    }

    @Test
    void read_adjacentFunctions_eachTakesOnlySimpleFactors() {
        assertEquals(List.of("(arith1:times (transc1:sin x) (transc1:cos y))"), terms("\\sin x \\cos y", Declaration.empty()));
        assertEquals(List.of("(arith1:times (transc1:cos i) (arith1:root (arith1:minus (arith1:power (transc1:sin i) 2) 1) 2))"),
                terms("\\cos i\\sqrt{\\sin^2 i - 1}", Declaration.empty()));
        assertEquals(List.of("(transc1:sin (arith1:divide nums1:pi 2))"), terms("\\sin \\frac{\\pi}{2}", Declaration.empty()));
    }

    @Test
    void read_integralForms_fractionDifferentialAndEmptyIntegrand() {
        assertEquals(List.of("(relation1:eq (arith1:minus t t_0) (calculus1:int (fns1:lambda (x) (arith1:divide 1 (arith1:root (arith1:times 2 E) 2)))))"),
                terms("t - t_0 = \\int \\frac{dx}{\\sqrt{2E}}", Declaration.empty()));
        assertEquals(List.of("(calculus1:defint (interval1:interval 0 (arith1:times 2 nums1:pi)) (fns1:lambda (theta) 1))"),
                terms("\\int_0^{2\\pi} d\\theta", Declaration.empty()));
    }

    @Test
    void read_partialWithRespectToDottedVariable_reasonDotAsVariable() {
        Declaration timed = Declaration.fromSExp(SexpReader.readOne("(declare :time t)"));
        assertEquals(List.of("FAILED dot-as-variable"), terms("\\frac{\\partial L}{\\partial \\dot{q}} = p", timed));
    }

    @Test
    void read_withoutEulerDeclaration_eIsAVariable() {
        assertEquals(List.of("(arith1:divide (arith1:times (arith1:power e 2) x) (arith1:power r 3))"),
                terms("\\frac{e^2 x}{r^3}", Declaration.empty()));
    }

    @Test
    void read_coordinateDot_isAVariableNamedWithDot() {
        Declaration lagrange = Declaration.fromSExp(SexpReader.readOne("(declare :time t :coordinates (q))"));
        assertEquals(List.of("(relation1:eq (calculus1:partialdiff (list1:list 1) (fns1:lambda (qdot_i) L)) (arith1:times m qdot_i))"),
                terms("\\frac{\\partial L}{\\partial \\dot{q}_i} = m\\dot{q}_i", lagrange));
    }

    @Test
    void read_sumWithBounds_integerIntervalAndLambda() {
        assertEquals(List.of("(relation1:eq S (arith1:sum (interval1:integer_interval 1 nums1:infinity) (fns1:lambda (z) (arith1:divide 1 (arith1:power z n)))))"),
                terms("S = \\sum_{z=1}^{\\infty} \\frac{1}{z^n}", Declaration.empty()));
    }

    @Test
    void read_functionPowerAndTrigArgument_productUpToPlus() {
        assertEquals(List.of("(arith1:plus (arith1:power (transc1:sin (arith1:times omega t)) 2) (arith1:power (transc1:cos (arith1:times omega t)) 2))"),
                terms("\\sin^2 \\omega t + \\cos^2 \\omega t", Declaration.empty()));
        assertTrue(terms("\\sin^{-1} x", Declaration.empty()).get(0).startsWith("(transc1:arcsin"));
    }
}
