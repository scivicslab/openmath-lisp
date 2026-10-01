package com.scivicslab.openmathlisp.sexp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The s-expression reader and writer: lists, symbols, strings, numbers and comments round-trip. */
@Tag("TermGrammarAndSymbolTable_261002_oo01")
class SexpReaderTest {

    @Test
    void readAll_nestedListWithComment_buildsTree() {
        List<SExp> all = SexpReader.readAll("(relation1:eq x 2) ; comment\n(a (b \"s\\\"q\" 1.5e-3))");
        assertEquals(2, all.size());
        SExp.SList first = (SExp.SList) all.get(0);
        assertEquals(new SExp.SSymbol("relation1:eq"), first.items().get(0));
        assertEquals(new SExp.SInteger(BigInteger.TWO), first.items().get(2));
        SExp.SList inner = (SExp.SList) ((SExp.SList) all.get(1)).items().get(1);
        assertEquals(new SExp.SString("s\"q"), inner.items().get(1));
        assertEquals("1.5e-3", ((SExp.SDecimal) inner.items().get(2)).literal());
    }

    @Test
    void readAll_unbalancedParenthesis_throwsWithOffset() {
        SexpSyntaxException e = assertThrows(SexpSyntaxException.class, () -> SexpReader.readAll("(a (b)"));
        assertEquals(0, e.getPosition());
    }

    @Test
    void writeFlat_afterRead_reproducesText() {
        String text = "(equation :id \"p1-eq2\" :term (arith1:plus a -3 0.5))";
        assertEquals(text, SexpWriter.writeFlat(SexpReader.readOne(text)));
    }

    @Test
    void writePretty_longList_breaksKeywordPairsOnOwnLines() {
        String text = "(equation :id \"x\" :source \"" + "a".repeat(120) + "\" :status :ok)";
        String pretty = SexpWriter.writePretty(SexpReader.readOne(text));
        assertEquals(SexpReader.readOne(text), SexpReader.readOne(pretty));
        assertEquals(4, pretty.split("\n").length);
    }
}
