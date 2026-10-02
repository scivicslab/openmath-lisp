package com.scivicslab.openmathlisp.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scivicslab.openmathlisp.record.Declaration;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** What the declaration suggester derives from a document, and what it leaves to a person. */
@Tag("FormulaSourceInMarkdown_261002_oo01")
class DeclarationSuggesterTest {

    private static final TermFactory FACTORY = new TermFactory(SymbolTable.loadBundled());

    private static Declaration suggest(String text) {
        return DeclarationSuggester.suggest(text, MarkdownDocument.parse(text, FACTORY));
    }

    @Test
    void suggest_quantityDifferentiatedByAVariableItLacks_becomesAFunctionOfIt() {
        String text = "```om id=d-eq1\n(relation1:eq (calculus1:diff (fns1:lambda (t) v)) a)\n```\n";
        assertEquals(List.of("t"), suggest(text).functions().get("v"));
    }

    @Test
    void suggest_sameQuantityDifferentiatedByThreeVariables_dependsOnAllThree() {
        String text = "```om id=a\n(calculus1:partialdiff (list1:list 1) (fns1:lambda (x) V))\n```\n"
                + "```om id=b\n(calculus1:partialdiff (list1:list 1) (fns1:lambda (y) V))\n```\n"
                + "```om id=c\n(calculus1:partialdiff (list1:list 1) (fns1:lambda (z) V))\n```\n";
        assertEquals(List.of("x", "y", "z"), suggest(text).functions().get("V"));
    }

    @Test
    void suggest_dotOverAQuantity_asksForTheTimeVariable() {
        assertEquals("t", suggest("$$m\\ddot{x} = F$$\n").time().orElseThrow());
        assertTrue(suggest("$$a = b$$\n").time().isEmpty());
    }

    @Test
    void suggest_dotInThePartialDenominator_namesTheCoordinate() {
        assertTrue(suggest("$$\\frac{\\partial L}{\\partial \\dot{q}} = p$$\n").coordinates().contains("q"));
    }

    @Test
    void suggest_declarationAlreadySatisfied_isKeptNotDropped() {
        String text = "```om\n(declare :time t :functions ((v t)))\n```\n"
                + "```om id=d-eq1\n(relation1:eq (calculus1:diff (fns1:lambda (t) (v t))) a)\n```\n";
        assertEquals(List.of("t"), suggest(text).functions().get("v"));
    }

    @Test
    void suggest_aQuantityThatIsItselfAFunction_isNotUsedAsAnArgument() {
        String text = "```om\n(declare :time t :functions ((x t)))\n```\n"
                + "```om id=d-eq1\n(calculus1:partialdiff (list1:list 1) (fns1:lambda (x) V))\n```\n";
        assertTrue(!suggest(text).functions().containsKey("V"),
                "V cannot be a function of x while x is a function of t");
    }

    @Test
    void evidence_eulerAndImaginaryUsedInTwoSenses_leftToAPerson() {
        DeclarationSuggester.Evidence both = DeclarationSuggester.evidence(
                "$$\\frac{e^2}{r^4}$$ and $$E_0 e^{-\\frac{2\\pi\\nu k}{c}}$$");
        assertTrue(!both.eulerIsClear(), "e is both the charge and the base here");
        DeclarationSuggester.Evidence base = DeclarationSuggester.evidence("$$y = Ae^{kx}$$");
        assertTrue(base.eulerIsClear());
        DeclarationSuggester.Evidence wave = DeclarationSuggester.evidence("$$e^{2\\pi i \\nu t}$$");
        assertTrue(wave.eulerIsClear(), "an exponent beginning with 2 is not the charge squared");
        DeclarationSuggester.Evidence angle = DeclarationSuggester.evidence(
                "$$\\frac{\\sin i}{\\sin r} = n$$ and $$e^{i\\omega t}$$");
        assertTrue(!angle.imaginaryIsClear(), "i is both the angle of incidence and the imaginary unit here");
    }
}
