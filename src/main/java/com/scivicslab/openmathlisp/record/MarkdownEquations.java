package com.scivicslab.openmathlisp.record;

import com.scivicslab.openmathlisp.latex.LatexBlockReader;
import com.scivicslab.openmathlisp.sexp.SExp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the {@code $$ ... $$} blocks of a markdown file and reads them into records. The identifier is
 * {@code <book>-p<pages>-eq<n>}: the book is the directory above the page directory, the pages come from the
 * file name ({@code ..._p051-060.md}), and {@code n} counts the blocks in the file.
 */
public final class MarkdownEquations {

    private static final Pattern BLOCK = Pattern.compile("\\$\\$(.+?)\\$\\$", Pattern.DOTALL);
    private static final Pattern PAGES = Pattern.compile("_(p\\d+-\\d+)$");

    private MarkdownEquations() {
    }

    /**
     * Derives the identifier prefix of a markdown file.
     * @param markdown the file
     * @return e.g. {@code SlaterVol1-p051-060}
     */
    public static String idPrefix(Path markdown) {
        Path absolute = markdown.toAbsolutePath().normalize();
        String name = absolute.getFileName().toString();
        String stem = name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
        Path parent = absolute.getParent();
        String book;
        if (parent != null && parent.getFileName().toString().equals(stem) && parent.getParent() != null) {
            book = parent.getParent().getFileName().toString();
        } else if (parent != null) {
            book = parent.getFileName().toString();
        } else {
            book = "doc";
        }
        Matcher pages = PAGES.matcher(stem);
        String pagePart = pages.find() ? pages.group(1) : sanitize(stem);
        return sanitize(book) + "-" + pagePart;
    }

    /** Keeps letters and digits only; the page range {@code p051-060} is matched before sanitizing and keeps its hyphen. */
    private static String sanitize(String text) {
        return text.replaceAll("[^A-Za-z0-9]+", "");
    }

    /**
     * Reads every block of a markdown file into records with the parse check filled in.
     * @param markdown the file
     * @param declaration the declaration to read with
     * @return the records in document order
     * @throws IOException when the file cannot be read
     */
    public static List<EquationRecord> read(Path markdown, Declaration declaration) throws IOException {
        String text = Files.readString(markdown, StandardCharsets.UTF_8);
        String prefix = idPrefix(markdown);
        List<EquationRecord> records = new ArrayList<>();
        Matcher matcher = BLOCK.matcher(text);
        int blockNumber = 0;
        while (matcher.find()) {
            blockNumber++;
            String block = matcher.group(1).trim();
            LatexBlockReader.BlockResult result = LatexBlockReader.read(block, declaration);
            int count = result.equations().size();
            for (int i = 0; i < count; i++) {
                LatexBlockReader.EquationResult equation = result.equations().get(i);
                String id = prefix + "-eq" + blockNumber + (count > 1 ? "-" + (i + 1) : "");
                Map<String, SExp> checks = new LinkedHashMap<>();
                String status;
                if (equation.term().isPresent()) {
                    checks.put(":parse", new SExp.SSymbol(":ok"));
                    status = ":not-checkable";
                } else {
                    checks.put(":parse", new SExp.SList(List.of(new SExp.SSymbol(":failed"),
                            new SExp.SString(equation.failure().get().getReason()),
                            new SExp.SInteger(java.math.BigInteger.valueOf(equation.failure().get().getPosition())))));
                    status = ":unparseable";
                }
                records.add(new EquationRecord(id, result.tag(), equation.source(), equation.term(), false, checks, status));
            }
            if (count == 0) {
                Map<String, SExp> checks = new LinkedHashMap<>();
                checks.put(":parse", new SExp.SList(List.of(new SExp.SSymbol(":failed"), new SExp.SString("empty"), new SExp.SInteger(java.math.BigInteger.ZERO))));
                records.add(new EquationRecord(prefix + "-eq" + blockNumber, result.tag(), block, Optional.empty(), false, checks, ":unparseable"));
            }
        }
        return records;
    }
}
