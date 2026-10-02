package com.scivicslab.openmathlisp.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.sexp.SexpReader;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Term files: identifier derivation, round-trip through text, and merging that keeps edited terms. */
@Tag("TermFileAndCheckRecord_261002_oo01")
class TermFileTest {

    private static final TermFactory FACTORY = new TermFactory(SymbolTable.loadBundled());

    @Test
    void idPrefix_bookPageDirectoryLayout_bookAndPages() {
        Path markdown = Path.of("/x/Books/SlaterVol1/理論物理学入門 上_p051-060/理論物理学入門 上_p051-060.md");
        assertEquals("SlaterVol1-p051-060", MarkdownEquations.idPrefix(markdown));
        assertEquals("notes-chapter2", MarkdownEquations.idPrefix(Path.of("/x/notes/chapter2.md")));
    }

    @Test
    void pathFor_markdown_sameDirectoryLispName() {
        assertEquals(Path.of("/a/b/X.lisp"), TermFile.pathFor(Path.of("/a/b/X.md")));
    }

    @Test
    void write_thenParse_reproducesDeclarationAndRecords() {
        String text = "(declare :time t :functions ((x t)))\n\n(equation :id \"doc-eq1\" :tag \"5\" :source \"a = b\" "
                + ":term (relation1:eq a b) :edited t :checks ((:parse :ok) (:numeric (:failed \"a-b\"))) :status :suspect)\n";
        TermFile file = TermFile.parse(text, FACTORY);
        assertEquals(Optional.of("t"), file.declaration().time());
        assertEquals(List.of("t"), file.declaration().functions().get("x"));
        EquationRecord record = file.records().get(0);
        assertTrue(record.edited());
        assertEquals(":suspect", record.status());
        assertEquals(SexpReader.readOne("(:failed \"a-b\")"), record.checks().get(":numeric"));
        TermFile again = TermFile.parse(file.write(), FACTORY);
        assertEquals(file.write(), again.write());
    }

    @Test
    void mergeFresh_editedRecordKeepsTermAndTakesNewSource() {
        String text = "(declare)\n(equation :id \"d-eq1\" :source \"old\" :term (relation1:eq a b) :edited t :checks () :status :ok)\n"
                + "(equation :id \"d-eq2\" :source \"old2\" :term (relation1:eq c d) :checks () :status :ok)\n";
        TermFile existing = TermFile.parse(text, FACTORY);
        EquationRecord fresh1 = new EquationRecord("d-eq1", Optional.empty(), "new", Optional.of(FACTORY.fromSExp(SexpReader.readOne("(relation1:eq a c)"))), false, Map.of(":parse", new SExp.SSymbol(":ok")), ":not-checkable");
        EquationRecord fresh2 = new EquationRecord("d-eq2", Optional.empty(), "new2", Optional.of(FACTORY.fromSExp(SexpReader.readOne("(relation1:eq c e)"))), false, Map.of(":parse", new SExp.SSymbol(":ok")), ":not-checkable");
        TermFile merged = existing.mergeFresh(List.of(fresh1, fresh2));
        assertEquals("(relation1:eq a b)", com.scivicslab.openmathlisp.sexp.SexpWriter.writeFlat(TermFactory.toSExp(merged.records().get(0).term().get())));
        assertEquals("new", merged.records().get(0).source());
        assertEquals("(relation1:eq c e)", com.scivicslab.openmathlisp.sexp.SexpWriter.writeFlat(TermFactory.toSExp(merged.records().get(1).term().get())));
    }

    @Test
    void writeTo_existingFile_leavesBackup(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("X.lisp");
        TermFile file = TermFile.parse("(declare)\n", FACTORY);
        file.writeTo(path);
        Files.writeString(path, "(declare :time t)\n");
        file.writeTo(path);
        assertEquals("(declare :time t)\n", Files.readString(dir.resolve("X.lisp.bak")));
    }

    @Test
    void read_markdownWithTwoBlocks_numbersBlocksAndChainParts(@TempDir Path dir) throws IOException {
        Path markdown = dir.resolve("notes.md");
        Files.writeString(markdown, "text\n\n$$a = b = c$$\n\nmore\n\n$$x + \\cdots = y$$\n");
        List<EquationRecord> records = MarkdownEquations.read(markdown, Declaration.empty());
        String prefix = MarkdownEquations.idPrefix(markdown);
        assertEquals(List.of(prefix + "-eq1-1", prefix + "-eq1-2", prefix + "-eq2"),
                records.stream().map(EquationRecord::id).toList());
        assertEquals(":unparseable", records.get(2).status());
    }
}
