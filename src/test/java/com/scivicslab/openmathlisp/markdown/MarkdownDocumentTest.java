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
    void convert_inlineVariable_becomesOmSpanButFormulaStaysLatex() {
        MarkdownDocument.ConversionResult result =
                MarkdownDocument.parse("変数 $\\omega_n$ と式 $a + b$ がある。\n", FACTORY).convert("d", FACTORY);
        assertEquals(0, result.blocks());
        assertEquals(1, result.spans());
        String written = result.document().write();
        assertTrue(written.contains("`om:omega_n`"), written);
        assertTrue(written.contains("$a + b$"), written);
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
    void writeTo_existingFile_leavesBackup(@TempDir Path dir) throws IOException {
        Path markdown = dir.resolve("X.md");
        Files.writeString(markdown, "$$a = b$$\n");
        MarkdownDocument.read(markdown, FACTORY).convert("d", FACTORY).document().writeTo(markdown);
        assertEquals("$$a = b$$\n", Files.readString(dir.resolve("X.md.bak")));
        assertTrue(Files.readString(markdown).contains("```om id=d-eq1"));
    }
}
