package com.scivicslab.openmathlisp.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.scivicslab.openmathlisp.sexp.SexpReader;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.symbols.InputFormat;
import com.scivicslab.openmathlisp.term.Term;
import com.scivicslab.openmathlisp.term.TermFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The writer on the Concept's example and on the four Slater equations of the grammar document. */
@Tag("TermGrammarAndSymbolTable_261002_oo01")
class TermWriterTest {

    private static final SymbolTable SYMBOLS = SymbolTable.loadBundled();
    private static final TermFactory FACTORY = new TermFactory(SYMBOLS);
    private static final TermWriter WRITER = new TermWriter(SYMBOLS);

    private static Term term(String text) {
        return FACTORY.fromSExp(SexpReader.readOne(text));
    }

    @Test
    void write_conceptExample_threeTargets() {
        Term t = term("(relation1:eq (arith1:power (arith1:plus a b) 2) (arith1:plus (arith1:power a 2) (arith1:times a b) (arith1:power b 2)))");
        assertEquals("{\\left(a + b\\right)}^{2} = {a}^{2} + a b + {b}^{2}", WRITER.write(t, InputFormat.LATEX));
        assertEquals("(a+b)^2 = a^2+a*b+b^2", WRITER.write(t, InputFormat.MAXIMA));
        assertEquals("(= (^ (+ a b) 2) (+ (^ a 2) (* a b) (^ b 2)))", WRITER.write(t, InputFormat.SMT));
    }

    @Test
    void write_divergenceEquation_latexAndMaximaFailure() {
        Term t = term("(relation1:eq (veccalc1:divergence (arith1:plus E (arith1:times 4 nums1:pi P))) (arith1:times 4 nums1:pi rho))");
        assertEquals("\\operatorname{div} \\left(E + 4 \\pi P\\right) = 4 \\pi \\rho", WRITER.write(t, InputFormat.LATEX));
        assertThrows(TermWriterException.class, () -> WRITER.write(t, InputFormat.MAXIMA));
    }

    @Test
    void write_fourierCoefficient_definiteIntegralWithLambda() {
        Term t = term("(relation1:eq B_n (arith1:times (arith1:divide 2 T) (calculus1:defint (interval1:interval (arith1:unary_minus (arith1:divide T 2)) (arith1:divide T 2)) (fns1:lambda (t) (arith1:times (f t) (transc1:sin (arith1:times omega_n t)))))))");
        assertEquals("B_{n} = \\frac{2}{T} \\int_{-\\frac{T}{2}}^{\\frac{T}{2}} f\\left(t\\right) \\sin \\left(\\omega_{n} t\\right) \\, dt",
                WRITER.write(t, InputFormat.LATEX));
        assertEquals("B_n = 2/T*'integrate(f(t)*sin(omega_n*t), t, -(T/2), T/2)", WRITER.write(t, InputFormat.MAXIMA));
    }

    @Test
    void write_dampedOscillator_derivativesOfDependentVariable() {
        Term t = term("(relation1:eq (arith1:plus (arith1:times m (calculus1:nthdiff 2 (fns1:lambda (t) (x t)))) (arith1:times m g (calculus1:diff (fns1:lambda (t) (x t)))) (arith1:times (arith1:power omega_0 2) m (x t))) (arith1:times c E_x^0 (transc1:exp (arith1:times nums1:i omega t))))");
        assertEquals("m \\frac{d^{2}}{dt^{2}} x\\left(t\\right) + m g \\frac{d}{dt} x\\left(t\\right) + {\\omega_{0}}^{2} m x\\left(t\\right) = c E_{x}^{0} e^{i \\omega t}",
                WRITER.write(t, InputFormat.LATEX));
        assertEquals("m*diff(x(t), t, 2)+m*g*diff(x(t), t)+omega_0^2*m*x(t) = c*E_x_sup_0*exp(%i*omega*t)",
                WRITER.write(t, InputFormat.MAXIMA));
    }

    @Test
    void write_waveEquation_partialDerivativesGroupDegrees() {
        Term t = term("(relation1:eq (arith1:minus (calculus1:partialdiff (list1:list 1 1) (fns1:lambda (r) (arith1:times r (u r t)))) (arith1:times (arith1:divide 1 (arith1:power r 2)) (calculus1:partialdiff (list1:list 1 1) (fns1:lambda (t) (arith1:times r (u r t)))))) 0)");
        assertEquals("\\frac{\\partial^{2} \\left(r u\\left(r, t\\right)\\right)}{\\partial r^{2}} - \\frac{1}{{r}^{2}} \\frac{\\partial^{2} \\left(r u\\left(r, t\\right)\\right)}{\\partial t^{2}} = 0",
                WRITER.write(t, InputFormat.LATEX));
        assertEquals("diff(r*u(r, t), r, 2)-1/r^2*diff(r*u(r, t), t, 2) = 0", WRITER.write(t, InputFormat.MAXIMA));
    }

    @Test
    void write_power_exponentNeedsNoBracketsBaseDoes() {
        assertEquals("{e}^{i x}", WRITER.write(term("(arith1:power e (arith1:times i x))"), InputFormat.LATEX));
        assertEquals("{a}^{2}", WRITER.write(term("(arith1:power a 2)"), InputFormat.LATEX));
        assertEquals("{\\left(a + b\\right)}^{2}", WRITER.write(term("(arith1:power (arith1:plus a b) 2)"), InputFormat.LATEX));
    }

    @Test
    void write_root_degreeTwoWithoutIndex() {
        assertEquals("\\sqrt{x}", WRITER.write(term("(arith1:root x 2)"), InputFormat.LATEX));
        assertEquals("\\sqrt[3]{x}", WRITER.write(term("(arith1:root x 3)"), InputFormat.LATEX));
    }

    @Test
    void write_variableNames_labelsAndGreek() {
        assertEquals("\\omega'", WRITER.write(term("omega'"), InputFormat.LATEX));
        assertEquals("v_{x0}", WRITER.write(term("v_x0"), InputFormat.LATEX));
        assertEquals("E_x_sup_0", WRITER.write(term("E_x^0"), InputFormat.MAXIMA));
    }

    @Test
    void write_negativeExponentOfPower_parenthesizedInLatexAndSmt() {
        Term t = term("(arith1:power (arith1:unary_minus a) 2)");
        assertEquals("{\\left(-a\\right)}^{2}", WRITER.write(t, InputFormat.LATEX));
        assertEquals("(-a)^2", WRITER.write(t, InputFormat.MAXIMA));
        assertEquals("(^ (- a) 2)", WRITER.write(t, InputFormat.SMT));
    }
}
