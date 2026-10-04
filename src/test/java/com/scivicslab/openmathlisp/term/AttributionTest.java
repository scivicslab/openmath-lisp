package com.scivicslab.openmathlisp.term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.scivicslab.openmathlisp.sexp.SexpReader;
import com.scivicslab.openmathlisp.sexp.SexpWriter;
import com.scivicslab.openmathlisp.symbols.InputFormat;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.write.TermWriter;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A reading key of private_reading1 attached to one formula: how it is read, what it leaves when it is
 * removed, and what the writer does with it.
 */
@Tag("OwnDictionary_261003_oo01")
class AttributionTest {

    private static final SymbolTable SYMBOLS = SymbolTable.loadBundled();
    private static final TermFactory FACTORY = new TermFactory(SYMBOLS);
    private static final TermWriter WRITER = new TermWriter(SYMBOLS);

    private static Term term(String text) {
        return FACTORY.fromSExp(SexpReader.readOne(text));
    }

    private static String sexp(Term term) {
        return SexpWriter.writeFlat(TermFactory.toSExp(term));
    }

    private static final String EULER_WAVE =
            "(private_reading1:euler e (relation1:eq psi (transc1:exp (arith1:times nums1:i omega t))))";

    @Test
    void fromSExp_readingKeyOnAWholeEquation_isAnAttributionOverTheEquation() {
        Term read = term(EULER_WAVE);
        Term.AttributionTerm attribution = assertInstanceOf(Term.AttributionTerm.class, read);
        assertEquals("private_reading1:euler", attribution.key().qualifiedName());
        assertEquals(new Term.VariableTerm("e"), attribution.value());
        assertInstanceOf(Term.ApplicationTerm.class, attribution.attributed());
        assertEquals(EULER_WAVE, sexp(read));
    }

    @Test
    void fromSExp_wrongNumberOfItems_isRejected() {
        assertThrows(TermFormatException.class, () -> term("(private_reading1:euler e)"));
        assertThrows(TermFormatException.class, () -> term("(private_reading1:euler e x y)"));
    }

    @Test
    void withoutAttributions_keysAtTheTopAndInsideAnArgument_allRemoved() {
        Term read = term("(arith1:plus (private_reading1:imaginary i (arith1:times nums1:i b))"
                + " (private_reading1:euler e (transc1:exp c)))");
        assertEquals("(arith1:plus (arith1:times nums1:i b) (transc1:exp c))",
                sexp(Term.withoutAttributions(read)));
    }

    @Test
    void withoutAttributions_keyOverABinder_leavesTheBinding() {
        Term read = term("(private_reading1:time t (fns1:lambda (x) (arith1:times x x)))");
        assertEquals("(fns1:lambda (x) (arith1:times x x))", sexp(Term.withoutAttributions(read)));
    }

    @Test
    void write_attributedEquation_onlyWhatTheKeyIsAbout() {
        Term read = term(EULER_WAVE);
        assertEquals("\\psi = e^{i \\omega t}", WRITER.write(read, InputFormat.LATEX));
        assertEquals(WRITER.write(Term.withoutAttributions(read), InputFormat.LATEX),
                WRITER.write(read, InputFormat.LATEX));
    }
}
