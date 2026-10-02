package com.scivicslab.openmathlisp.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The file that keeps what the OCR produced, which conversion would otherwise destroy. */
@Tag("FormulaSourceInMarkdown_261002_oo01")
class SourceRecordFileTest {

    @Test
    void pathFor_markdown_sameDirectoryOcrName() {
        assertEquals(Path.of("/a/b/X.ocr.lisp"), SourceRecordFile.pathFor(Path.of("/a/b/X.md")));
    }

    @Test
    void write_thenParse_reproducesTheLatexIncludingBackslashes() {
        Map<String, String> records = new LinkedHashMap<>();
        records.put("d-eq1", "\\frac{E''}{E} = e^{i\\delta_i}");
        String text = new SourceRecordFile(records).write();
        assertEquals("\\frac{E''}{E} = e^{i\\delta_i}", SourceRecordFile.parse(text).ocrOf("d-eq1"));
    }

    @Test
    void merge_identifierAlreadyRecorded_keepsTheFirstReading() {
        SourceRecordFile first = new SourceRecordFile(Map.of("d-eq1", "a = b"));
        SourceRecordFile merged = first.merge(Map.of("d-eq1", "a = c", "d-eq2", "x = y"));
        assertEquals("a = b", merged.ocrOf("d-eq1"));
        assertEquals("x = y", merged.ocrOf("d-eq2"));
    }

    @Test
    void writeTo_nothingRecorded_leavesNoFile(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("X.ocr.lisp");
        Files.writeString(path, "");
        new SourceRecordFile(Map.of()).writeTo(path);
        assertTrue(!Files.exists(path));
    }
}
