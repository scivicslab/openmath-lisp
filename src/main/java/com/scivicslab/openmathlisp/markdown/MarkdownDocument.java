package com.scivicslab.openmathlisp.markdown;

import com.scivicslab.openmathlisp.latex.LatexBlockReader;
import com.scivicslab.openmathlisp.latex.LatexParser;
import com.scivicslab.openmathlisp.latex.LatexReadException;
import com.scivicslab.openmathlisp.project.ProjectionException;
import com.scivicslab.openmathlisp.project.Projector;
import com.scivicslab.openmathlisp.record.Declaration;
import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.sexp.SexpReader;
import com.scivicslab.openmathlisp.sexp.SexpSyntaxException;
import com.scivicslab.openmathlisp.sexp.SexpWriter;
import com.scivicslab.openmathlisp.symbols.Target;
import com.scivicslab.openmathlisp.term.Term;
import com.scivicslab.openmathlisp.term.TermFactory;
import com.scivicslab.openmathlisp.term.TermFormatException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A markdown file as a list of pieces, with the three operations the formula source placement defines:
 * convert the LaTeX a scanner left behind into om blocks and om spans, render the om pieces back to LaTeX
 * for display, and hand the terms to the checks.
 */
public final class MarkdownDocument {

    private static final Pattern OM_BLOCK = Pattern.compile(
            "(?m)^```om([^\\n]*)\\n(.*?)^```[ \\t]*$\\n?", Pattern.DOTALL);
    private static final Pattern OM_SPAN = Pattern.compile("`om:([^`\\n]+)`");
    private static final Pattern LATEX_BLOCK = Pattern.compile("\\$\\$(.+?)\\$\\$", Pattern.DOTALL);
    private static final Pattern LATEX_SPAN = Pattern.compile("(?<!\\$)\\$(?!\\$)([^$\\n]+?)\\$(?!\\$)");
    private static final Pattern MARKER = Pattern.compile(
            "(?m)^<!--[ \\t]*om:unreadable[ \\t]+id=(\\S+)[ \\t]+reason=(\\S+)[ \\t]*-->[ \\t]*\\n?");
    private static final Pattern ID_IN_INFO = Pattern.compile("\\bid=(\\S+)");
    private static final Pattern TAG_IN_INFO = Pattern.compile("\\btag=(\\S+)");
    private static final Pattern INLINE_VARIABLE = Pattern.compile("[A-Za-z][A-Za-z0-9_^]*'*");

    private final List<MarkdownPiece> pieces;
    private final Declaration declaration;

    private MarkdownDocument(List<MarkdownPiece> pieces, Declaration declaration) {
        this.pieces = List.copyOf(pieces);
        this.declaration = declaration;
    }

    /**
     * Parses a markdown file's text into pieces. An om block or om span whose s-expression is not a term
     * is kept with its text so that writing reproduces the file; the error surfaces when the checks run.
     * @param text the file text
     * @param factory the factory that turns an s-expression into a term
     * @return the document
     */
    public static MarkdownDocument parse(String text, TermFactory factory) {
        List<MarkdownPiece> pieces = new ArrayList<>();
        Declaration declaration = Declaration.empty();
        int cursor = 0;
        Matcher blocks = OM_BLOCK.matcher(text);
        Matcher markers = MARKER.matcher(text);
        List<int[]> starts = new ArrayList<>();
        Map<Integer, Matcher> owner = new LinkedHashMap<>();
        while (blocks.find()) {
            starts.add(new int[] {blocks.start(), blocks.end(), 0});
        }
        while (markers.find()) {
            starts.add(new int[] {markers.start(), markers.end(), 1});
        }
        starts.sort((int[] a, int[] b) -> Integer.compare(a[0], b[0]));
        for (int[] span : starts) {
            if (span[0] < cursor) {
                continue;
            }
            addText(pieces, text.substring(cursor, span[0]), factory);
            String raw = text.substring(span[0], span[1]);
            if (span[2] == 1) {
                Matcher m = MARKER.matcher(raw);
                m.find();
                pieces.add(new MarkdownPiece.UnreadableMarker(raw, m.group(1), m.group(2)));
            } else {
                MarkdownPiece block = parseOmBlock(raw, factory);
                if (block instanceof MarkdownPiece.OmBlock readable && readable.isDeclaration()) {
                    declaration = Declaration.fromSExp(SexpReader.readOne(readable.source()));
                }
                pieces.add(block);
            }
            cursor = span[1];
        }
        addText(pieces, text.substring(cursor), factory);
        return new MarkdownDocument(pieces, declaration);
    }

    private static MarkdownPiece parseOmBlock(String raw, TermFactory factory) {
        Matcher m = OM_BLOCK.matcher(raw);
        m.find();
        String info = m.group(1);
        String source = m.group(2).strip();
        Matcher id = ID_IN_INFO.matcher(info);
        Optional<String> identifier = id.find() ? Optional.of(id.group(1)) : Optional.empty();
        Matcher tagMatcher = TAG_IN_INFO.matcher(info);
        Optional<String> tag = tagMatcher.find() ? Optional.of(tagMatcher.group(1)) : Optional.empty();
        if (source.startsWith("(declare")) {
            try {
                Declaration.fromSExp(SexpReader.readOne(source));
                return new MarkdownPiece.OmBlock(raw, identifier, tag, source, Optional.empty());
            } catch (SexpSyntaxException | IllegalArgumentException | ClassCastException e) {
                return new MarkdownPiece.OmError(raw, identifier, source, e.getMessage());
            }
        }
        try {
            return new MarkdownPiece.OmBlock(raw, identifier, tag, source,
                    Optional.of(factory.fromSExp(SexpReader.readOne(source))));
        } catch (SexpSyntaxException | TermFormatException | IllegalArgumentException e) {
            return new MarkdownPiece.OmError(raw, identifier, source, e.getMessage());
        }
    }

    /** Splits a run of ordinary markdown into om spans, LaTeX blocks, LaTeX spans and plain text. */
    private static void addText(List<MarkdownPiece> pieces, String text, TermFactory factory) {
        if (text.isEmpty()) {
            return;
        }
        List<int[]> spans = new ArrayList<>();
        collect(spans, OM_SPAN.matcher(text), 0);
        collect(spans, LATEX_BLOCK.matcher(text), 1);
        collect(spans, LATEX_SPAN.matcher(text), 2);
        spans.sort((int[] a, int[] b) -> Integer.compare(a[0], b[0]));
        int cursor = 0;
        for (int[] span : spans) {
            if (span[0] < cursor) {
                continue;
            }
            if (span[0] > cursor) {
                pieces.add(new MarkdownPiece.Text(text.substring(cursor, span[0])));
            }
            String raw = text.substring(span[0], span[1]);
            switch (span[2]) {
                case 0 -> {
                    String source = OM_SPAN.matcher(raw).results().findFirst().orElseThrow().group(1).strip();
                    try {
                        pieces.add(new MarkdownPiece.OmSpan(raw, source, factory.fromSExp(SexpReader.readOne(source))));
                    } catch (SexpSyntaxException | TermFormatException | IllegalArgumentException e) {
                        pieces.add(new MarkdownPiece.OmError(raw, Optional.empty(), source, e.getMessage()));
                    }
                }
                case 1 -> pieces.add(new MarkdownPiece.LatexBlock(raw, raw.substring(2, raw.length() - 2).strip()));
                default -> pieces.add(new MarkdownPiece.LatexSpan(raw, raw.substring(1, raw.length() - 1).strip()));
            }
            cursor = span[1];
        }
        if (cursor < text.length()) {
            pieces.add(new MarkdownPiece.Text(text.substring(cursor)));
        }
    }

    private static void collect(List<int[]> spans, Matcher matcher, int kind) {
        while (matcher.find()) {
            spans.add(new int[] {matcher.start(), matcher.end(), kind});
        }
    }

    /**
     * Reads a markdown file.
     * @param path the file
     * @param factory the factory for the terms
     * @return the document
     * @throws IOException when the file cannot be read
     */
    public static MarkdownDocument read(Path path, TermFactory factory) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8), factory);
    }

    /** @return the file text */
    public String write() {
        StringBuilder out = new StringBuilder();
        for (MarkdownPiece piece : pieces) {
            out.append(piece.raw());
        }
        return out.toString();
    }

    /**
     * Writes the file, leaving the previous content in {@code X.md.bak}.
     * @param path the file
     * @throws IOException when the file cannot be written
     */
    public void writeTo(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(path, write(), StandardCharsets.UTF_8);
    }

    /** @return the declaration from the file's first om block, or the empty declaration */
    public Declaration declaration() {
        return declaration;
    }

    /** @return the pieces in file order */
    public List<MarkdownPiece> pieces() {
        return pieces;
    }

    /**
     * What conversion changed in one file.
     * @param document the converted document
     * @param blocks how many display formulas became om blocks
     * @param spans how many inline formulas became om spans
     * @param unreadable how many display formulas stayed LaTeX with a marker
     */
    public record ConversionResult(MarkdownDocument document, int blocks, int spans, int unreadable) {
        /** @return the formulas written as formula source, display and inline together */
        public int converted() {
            return blocks + spans;
        }
    }

    /**
     * Replaces the LaTeX blocks and spans that the reader can read with om blocks and om spans, and marks
     * the rest with its reason. Pieces that are already om blocks or om spans are left as they are, so
     * running this again after editing the declaration converts only what is left.
     * @param idPrefix the identifier prefix for new om blocks, e.g. {@code SlaterVol1-p051-060}
     * @param factory the factory for the terms
     * @return the converted document with the counts
     */
    public ConversionResult convert(String idPrefix, TermFactory factory) {
        List<MarkdownPiece> result = new ArrayList<>();
        int unitNumber = 0;
        int blocks = 0;
        int spans = 0;
        int unreadable = 0;
        for (int i = 0; i < pieces.size(); i++) {
            MarkdownPiece piece = pieces.get(i);
            if (piece instanceof MarkdownPiece.OmBlock block && !block.isDeclaration()) {
                unitNumber++;
                result.add(piece);
                continue;
            }
            if (piece instanceof MarkdownPiece.UnreadableMarker) {
                continue; // a fresh marker is written below if the block is still unreadable
            }
            if (piece instanceof MarkdownPiece.LatexSpan span) {
                Optional<Term> term = readInline(span.latex(), factory);
                if (term.isPresent()) {
                    String source = SexpWriter.writeFlat(TermFactory.toSExp(term.get()));
                    result.add(new MarkdownPiece.OmSpan("`om:" + source + "`", source, term.get()));
                    spans++;
                } else {
                    result.add(piece);
                }
                continue;
            }
            if (!(piece instanceof MarkdownPiece.LatexBlock block)) {
                result.add(piece);
                continue;
            }
            unitNumber++;
            String id = idPrefix + "-eq" + unitNumber;
            LatexBlockReader.BlockResult read = LatexBlockReader.read(block.latex(), declaration);
            List<LatexBlockReader.EquationResult> equations = read.equations();
            boolean allReadable = !equations.isEmpty()
                    && equations.stream().allMatch((LatexBlockReader.EquationResult e) -> e.term().isPresent());
            if (!allReadable) {
                String reason = equations.stream()
                        .filter((LatexBlockReader.EquationResult e) -> e.failure().isPresent())
                        .findFirst()
                        .map((LatexBlockReader.EquationResult e) -> e.failure().get().getReason())
                        .orElse("empty");
                result.add(new MarkdownPiece.UnreadableMarker(
                        "<!-- om:unreadable id=" + id + " reason=" + reason + " -->\n", id, reason));
                result.add(piece);
                unreadable++;
                continue;
            }
            for (int k = 0; k < equations.size(); k++) {
                String partId = equations.size() > 1 ? id + "-" + (k + 1) : id;
                result.add(blockFor(partId, read.tag(), equations.get(k).term().get()));
                blocks++;
            }
        }
        return new ConversionResult(new MarkdownDocument(result, declaration), blocks, spans, unreadable);
    }

    private static MarkdownPiece.OmBlock blockFor(String id, Optional<String> tag, Term term) {
        String source = SexpWriter.writePretty(TermFactory.toSExp(term));
        String info = "om id=" + id + tag.map((String t) -> " tag=" + t).orElse("");
        String raw = "```" + info + "\n" + source + "\n```\n";
        return new MarkdownPiece.OmBlock(raw, Optional.of(id), tag, source, Optional.of(term));
    }

    /** An inline formula is converted when it is a variable name or a readable s-expression-free formula. */
    private Optional<Term> readInline(String latex, TermFactory factory) {
        String text = latex.strip();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            LatexParser.RelationChain chain = LatexParser.parse(text, declaration);
            if (chain.relations().isEmpty() && chain.operands().size() == 1) {
                Term term = chain.operands().get(0);
                if (term instanceof Term.VariableTerm || term instanceof Term.SymbolTerm) {
                    return Optional.of(term);
                }
            }
            return Optional.empty();
        } catch (LatexReadException | TermFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Writes the file with every om block and om span replaced by the LaTeX the projector produces, so that
     * KaTeX can draw it. The declaration block is removed. Nothing else changes.
     * @param projector the projector
     * @return the markdown text for display
     */
    public String render(Projector projector) {
        StringBuilder out = new StringBuilder();
        for (MarkdownPiece piece : pieces) {
            switch (piece) {
                case MarkdownPiece.OmBlock block -> {
                    if (block.isDeclaration()) {
                        continue;
                    }
                    out.append("$$\n").append(projectOrSource(block.term().get(), block.source(), projector)).append("\n$$\n");
                }
                case MarkdownPiece.OmSpan span ->
                        out.append('$').append(projectOrSource(span.term(), span.source(), projector)).append('$');
                default -> out.append(piece.raw());
            }
        }
        return out.toString();
    }

    private static String projectOrSource(Term term, String source, Projector projector) {
        try {
            return projector.project(term, Target.LATEX);
        } catch (ProjectionException e) {
            return "\\text{" + source.replace("\n", " ") + "}";
        }
    }

    /**
     * The terms of this file with their identifiers, in file order, for the checks. An om block without an
     * {@code id=} gets its position among the display formulas as identifier.
     * @param idPrefix the prefix for positions
     * @return identifier to term, in order
     */
    public Map<String, Term> equations(String idPrefix) {
        Map<String, Term> result = new LinkedHashMap<>();
        int unitNumber = 0;
        for (MarkdownPiece piece : pieces) {
            if (piece instanceof MarkdownPiece.OmBlock block && !block.isDeclaration()) {
                unitNumber++;
                result.put(block.id().orElse(idPrefix + "-eq" + unitNumber), block.term().get());
            }
        }
        return result;
    }

    /** @return the om blocks and om spans whose s-expression is not a term, in file order */
    public List<MarkdownPiece.OmError> errors() {
        List<MarkdownPiece.OmError> result = new ArrayList<>();
        for (MarkdownPiece piece : pieces) {
            if (piece instanceof MarkdownPiece.OmError error) {
                result.add(error);
            }
        }
        return result;
    }

    /** @return the markers conversion left, in file order */
    public List<MarkdownPiece.UnreadableMarker> markers() {
        List<MarkdownPiece.UnreadableMarker> result = new ArrayList<>();
        for (MarkdownPiece piece : pieces) {
            if (piece instanceof MarkdownPiece.UnreadableMarker marker) {
                result.add(marker);
            }
        }
        return result;
    }

    /**
     * Reads the declaration of a markdown file without building its terms, for use before the symbol table
     * is consulted.
     * @param text the file text
     * @return the declaration, or the empty one
     */
    public static Declaration declarationOf(String text) {
        Matcher blocks = OM_BLOCK.matcher(text);
        while (blocks.find()) {
            String source = blocks.group(2).strip();
            if (source.startsWith("(declare")) {
                try {
                    SExp form = SexpReader.readOne(source);
                    return Declaration.fromSExp(form);
                } catch (SexpSyntaxException | IllegalArgumentException e) {
                    return Declaration.empty();
                }
            }
        }
        return Declaration.empty();
    }
}
