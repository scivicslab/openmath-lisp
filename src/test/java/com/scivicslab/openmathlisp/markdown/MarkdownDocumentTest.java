package com.scivicslab.openmathlisp.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scivicslab.openmathlisp.project.Projector;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The markdown document: parsing into pieces, converting the LaTeX a scanner left behind, rendering back to
 * LaTeX for display, and handing the formulas to the checks.
 */
@Tag("FormulaSourceInMarkdown_261002_oo01")
class MarkdownDocumentTest {

    private static final SymbolTable SYMBOLS = SymbolTable.loadBundled();
    private static final TermFactory FACTORY = new TermFactory(SYMBOLS);
    private static final Projector PROJECTOR = new Projector(SYMBOLS);

    private static final String PHYSICS_DECLARATION = "```om\n(declare :time t :euler e :imaginary i :functions ((x t) (f t)))\n```\n";

    @Test
    void prefixFor_bookPageDirectoryLayout_bookAndPageRange() {
        assertEquals("SlaterVol1-p051-060",
                DocumentIdentifier.prefixFor(Path.of("/x/Books/SlaterVol1/理論物理学入門 上_p051-060/理論物理学入門 上_p051-060.md")));
        assertEquals("notes-chapter2", DocumentIdentifier.prefixFor(Path.of("/x/notes/chapter2.md")));
    }

    @Test
    void parse_omBlockAndSpanAndLeftoverLatex_pieceKindsInOrder() {
        String text = PHYSICS_DECLARATION + "\n角振動数 `om:omega_n` について\n\n```om id=d-eq1\n(relation1:eq a b)\n```\n\n"
                + "読めない $$x + \\cdots = y$$ と行内の $\\alpha$ が残る。\n";
        MarkdownDocument document = MarkdownDocument.parse(text, FACTORY);
        List<String> kinds = document.pieces().stream().map((MarkdownPiece p) -> p.getClass().getSimpleName()).toList();
        assertEquals(List.of("OmBlock", "Text", "OmSpan", "Text", "OmBlock", "Text", "LatexBlock", "Text", "LatexSpan", "Text"), kinds);
        assertEquals(Optional.of("t"), document.declaration().time());
        assertEquals(text, document.write());
    }

    @Test
    void convert_readableDisplayFormula_becomesOmBlockWithIdentifier() {
        String text = PHYSICS_DECLARATION + "\n$$m\\ddot{x} + kx = 0$$\n";
        MarkdownDocument.ConversionResult result = MarkdownDocument.parse(text, FACTORY).convert("SlaterVol1-p051-060", FACTORY);
        assertEquals(1, result.blocks());
        assertEquals(0, result.spans());
        assertEquals(0, result.unreadable());
        String written = result.document().write();
        assertTrue(written.contains("```om id=SlaterVol1-p051-060-eq1"), written);
        assertTrue(written.contains("(calculus1:nthdiff 2 (fns1:lambda (t) (x t)))"), written);
        assertTrue(!written.contains("$$"), written);
    }

    @Test
    void convert_readableFormula_reportsTheLatexItReplaced() {
        MarkdownDocument.ConversionResult result =
                MarkdownDocument.parse("$$a = b + c$$\n", FACTORY).convert("d", FACTORY);
        assertEquals("a = b + c", result.replacedLatex().get("d-eq1"));
    }

    @Test
    void convert_taggedFormula_keepsTheBookEquationNumber() {
        String written = MarkdownDocument.parse("$$a = b \\tag{17}$$\n", FACTORY).convert("d", FACTORY).document().write();
        assertTrue(written.contains("```om id=d-eq1 tag=17"), written);
    }

    @Test
    void convert_chainOfRelations_becomesOneOmBlockPerPair() {
        MarkdownDocument.ConversionResult result =
                MarkdownDocument.parse("$$a = b + c = 2d$$\n", FACTORY).convert("d", FACTORY);
        assertEquals(2, result.blocks());
        String written = result.document().write();
        assertTrue(written.contains("```om id=d-eq1-1"), written);
        assertTrue(written.contains("```om id=d-eq1-2"), written);
    }

    @Test
    void convert_unreadableFormula_keepsLatexAndWritesMarker() {
        MarkdownDocument.ConversionResult result =
                MarkdownDocument.parse("$$1 + \\cdots = 2$$\n", FACTORY).convert("d", FACTORY);
        assertEquals(0, result.converted());
        assertEquals(1, result.unreadable());
        String written = result.document().write();
        assertTrue(written.startsWith("<!-- om:unreadable id=d-eq1 reason=ellipsis -->"), written);
        assertTrue(written.contains("$$1 + \\cdots = 2$$"), written);
    }

    @Test
    void convert_aSplitBlockThenAgain_doesNotRenumberTheBlocksAfterIt() {
        String text = "$$a = b = c$$\n\n$$x = y$$\n";
        MarkdownDocument once = MarkdownDocument.parse(text, FACTORY).convert("d", FACTORY).document();
        assertTrue(once.write().contains("```om id=d-eq2"), once.write());
        MarkdownDocument twice = MarkdownDocument.parse(once.write(), FACTORY).convert("d", FACTORY).document();
        assertEquals(once.write(), twice.write());
    }

    @Test
    void convert_runTwice_isIdempotentAndKeepsConvertedBlocks() {
        String text = PHYSICS_DECLARATION + "\n$$a = b$$\n\n$$1 + \\cdots = 2$$\n";
        MarkdownDocument once = MarkdownDocument.parse(text, FACTORY).convert("d", FACTORY).document();
        MarkdownDocument.ConversionResult twice = MarkdownDocument.parse(once.write(), FACTORY).convert("d", FACTORY);
        assertEquals(0, twice.converted());
        assertEquals(1, twice.unreadable());
        assertEquals(once.write(), twice.document().write());
    }

    @Test
    void convert_afterAddingDeclaration_convertsWhatWasUnreadable() {
        String text = "$$v = \\dot{x}$$\n";
        MarkdownDocument.ConversionResult without = MarkdownDocument.parse(text, FACTORY).convert("d", FACTORY);
        assertEquals(1, without.unreadable());
        assertEquals("dot-without-time", without.document().markers().get(0).reason());
        String withDeclaration = PHYSICS_DECLARATION + without.document().write();
        MarkdownDocument.ConversionResult again = MarkdownDocument.parse(withDeclaration, FACTORY).convert("d", FACTORY);
        assertEquals(1, again.converted());
        assertEquals(0, again.unreadable());
    }

    @Test
    void convert_inlineVariableAndInlineFormula_bothBecomeOmSpans() {
        MarkdownDocument.ConversionResult result =
                MarkdownDocument.parse("変数 $\\omega_n$ と式 $a + b$ がある。\n", FACTORY).convert("d", FACTORY);
        assertEquals(0, result.blocks());
        assertEquals(2, result.spans(), "a variable and a formula both become om spans");
        String written = result.document().write();
        assertTrue(written.contains("`om:omega_n`"), written);
        assertTrue(!written.contains("$a + b$"), "the formula must no longer be LaTeX: " + written);
        assertTrue(written.contains("arith1:plus"), "the formula must be a term: " + written);
    }

    @Test
    void render_omBlockAndSpan_becomeLatexAndDeclarationDisappears() {
        String text = PHYSICS_DECLARATION + "\n角振動数 `om:omega_n` は\n\n```om id=d-eq1\n(relation1:eq a (arith1:divide b c))\n```\n";
        String rendered = MarkdownDocument.parse(text, FACTORY).render(PROJECTOR);
        assertTrue(!rendered.contains("declare"), rendered);
        assertTrue(rendered.contains("$\\omega_{n}$"), rendered);
        assertTrue(rendered.contains("$$\na = \\frac{b}{c}\n$$"), rendered);
    }

    @Test
    void equations_omBlocksOnly_identifiersInFileOrder() {
        String text = PHYSICS_DECLARATION + "```om id=given\n(relation1:eq a b)\n```\n```om\n(relation1:eq c d)\n```\n";
        assertEquals(List.of("given", "d-eq2"), List.copyOf(MarkdownDocument.parse(text, FACTORY).equations("d").keySet()));
    }

    @Test
    void applyDeclaration_blockConvertedBeforeTheDeclaration_isBroughtIntoLine() {
        String text = "```om\n(declare :time t :functions ((v t)))\n```\n"
                + "```om id=d-eq1\n(relation1:eq (calculus1:diff (fns1:lambda (t) v)) a)\n```\n";
        MarkdownDocument.ConversionResult result = MarkdownDocument.parse(text, FACTORY).applyDeclaration();
        assertEquals(1, result.blocks());
        assertTrue(result.document().write().contains("(fns1:lambda (t) (v t))"), result.document().write());
        assertEquals(0, MarkdownDocument.parse(result.document().write(), FACTORY).applyDeclaration().blocks(),
                "a document already in line with its declaration does not change again");
    }

    @Test
    void applyDeclaration_theVariableAnIntegralBinds_staysBare() {
        String text = "```om\n(declare :functions ((x t)))\n```\n"
                + "```om id=d-eq1\n(calculus1:int (fns1:lambda (x) (arith1:times a x)))\n```\n";
        MarkdownDocument.ConversionResult result = MarkdownDocument.parse(text, FACTORY).applyDeclaration();
        assertEquals(0, result.blocks(), "the integration variable is not a function here");
    }

    @Test
    void convert_derivativeByADeclaredFunctionName_readsItAsTheDifferentiationVariable() {
        String text = "```om\n(declare :functions ((x t)))\n```\n$$\\frac{d}{dx} \\arctan x = y$$\n";
        String written = MarkdownDocument.parse(text, FACTORY).convert("d", FACTORY).document().write();
        assertTrue(written.contains("(calculus1:diff (fns1:lambda (x) (transc1:arctan x)))"), written);
    }

    @Test
    void writeTo_existingFile_leavesBackup(@TempDir Path dir) throws IOException {
        Path markdown = dir.resolve("X.md");
        Files.writeString(markdown, "$$a = b$$\n");
        MarkdownDocument.read(markdown, FACTORY).convert("d", FACTORY).document().writeTo(markdown);
        assertEquals("$$a = b$$\n", Files.readString(dir.resolve("X.md.bak")));
        assertTrue(Files.readString(markdown).contains("```om id=d-eq1"));
    }

    @Test
    void convert_inlineEquation_becomesOmSpan() {
        MarkdownDocument document = MarkdownDocument.parse(
                "Hamilton の方程式 $dq/dt = p$ は速度を与える。\n", FACTORY);
        MarkdownDocument.ConversionResult result = document.convert("notes-ch1", FACTORY);
        assertEquals(1, result.spans(), "an inline equation must become an om span");
        assertEquals(0, result.unreadable(), "a readable inline equation leaves no marker");
        assertTrue(result.document().write().contains("`om:"),
                "the written markdown must carry the om span: " + result.document().write());
    }

    @Test
    void convert_inlineRelationChain_staysLatexWithAMarkerBeforeIt() {
        MarkdownDocument document = MarkdownDocument.parse("そこで $a = b = c$ となる。\n", FACTORY);
        MarkdownDocument.ConversionResult result = document.convert("notes-ch1", FACTORY);
        assertEquals(0, result.spans(), "a chain of two relations is not one s-expression");
        assertEquals(1, result.unreadable(), "it must be marked, not dropped silently");
        String written = result.document().write();
        assertTrue(written.contains("<!-- om:unreadable id=notes-ch1-in1 reason=relation-chain -->$a = b = c$"),
                "the marker must sit immediately before the formula, on the same line: " + written);
        assertTrue(written.startsWith("そこで <!--"),
                "the text before the formula must stay where it was: " + written);
    }

    @Test
    void convert_runTwiceOnAnUnreadableInlineFormula_keepsOneMarkerAndItsIdentifier() {
        MarkdownDocument first = MarkdownDocument.parse("そこで $a = b = c$ となる。\n", FACTORY);
        String once = first.convert("notes-ch1", FACTORY).document().write();
        MarkdownDocument.ConversionResult twice =
                MarkdownDocument.parse(once, FACTORY).convert("notes-ch1", FACTORY);
        assertEquals(once, twice.document().write(), "converting again must change nothing");
        assertEquals(1, twice.unreadable(), "the marker must not be duplicated");
        assertEquals(List.of("notes-ch1-in1"),
                twice.document().markers().stream().map(MarkdownPiece.UnreadableMarker::id).toList(),
                "the identifier must survive the second run");
    }

    @Test
    void convert_inlineFormulaMarker_isInlineAndDisplayMarkerIsNot() {
        MarkdownDocument inline = MarkdownDocument.parse("そこで $a = b = c$ となる。\n", FACTORY);
        MarkdownPiece.UnreadableMarker inlineMarker =
                inline.convert("notes-ch1", FACTORY).document().markers().get(0);
        assertTrue(inlineMarker.isInline(), "an id ending in -in<N> names an inline formula");

        MarkdownDocument display = MarkdownDocument.parse("$$a = b = \\dots$$\n", FACTORY);
        List<MarkdownPiece.UnreadableMarker> displayMarkers =
                display.convert("notes-ch1", FACTORY).document().markers();
        assertEquals(1, displayMarkers.size(), "the display block must be marked");
        assertTrue(!displayMarkers.get(0).isInline(), "an id ending in -eq<N> names a display formula");
    }
}
