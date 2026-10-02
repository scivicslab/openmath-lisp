package com.scivicslab.openmathlisp.cli;

import com.scivicslab.openmathlisp.markdown.DocumentIdentifier;
import com.scivicslab.openmathlisp.markdown.MarkdownDocument;
import com.scivicslab.openmathlisp.markdown.MarkdownPiece;
import com.scivicslab.openmathlisp.project.ProjectionException;
import com.scivicslab.openmathlisp.record.CheckRecordFile;
import com.scivicslab.openmathlisp.record.EquationRecord;
import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.sexp.SexpWriter;
import com.scivicslab.openmathlisp.symbols.Target;
import com.scivicslab.openmathlisp.term.Term;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The five subcommands: convert, check, report, render, project. */
public final class Commands {

    private final Toolchain toolchain;
    private final PrintStream out;

    /**
     * Creates the commands.
     * @param toolchain the shared objects
     * @param out where the commands print
     */
    public Commands(Toolchain toolchain, PrintStream out) {
        this.toolchain = toolchain;
        this.out = out;
    }

    /**
     * {@code convert}: replaces the LaTeX of markdown files with om blocks and om spans where the reader can
     * read it, and marks the rest with its reason. The previous content is left in {@code X.md.bak}.
     * @param markdownFiles the markdown files
     * @throws IOException when a file cannot be read or written
     */
    public void convert(List<Path> markdownFiles) throws IOException {
        for (Path markdown : markdownFiles) {
            MarkdownDocument document = MarkdownDocument.read(markdown, toolchain.factory());
            MarkdownDocument.ConversionResult result =
                    document.convert(DocumentIdentifier.prefixFor(markdown), toolchain.factory());
            result.document().writeTo(markdown);
            out.println(markdown + ": " + result.converted() + " converted, " + result.unreadable() + " unreadable");
        }
    }

    /**
     * {@code check}: runs the four checks on the om blocks of markdown files and writes the check record
     * file beside each one.
     * @param markdownFiles the markdown files
     * @throws IOException when a file cannot be read or written
     */
    public void check(List<Path> markdownFiles) throws IOException {
        for (Path markdown : markdownFiles) {
            MarkdownDocument document = MarkdownDocument.read(markdown, toolchain.factory());
            Map<String, Term> equations = document.equations(DocumentIdentifier.prefixFor(markdown));
            CheckRecordFile checked = toolchain.checker().check(equations, document.declaration().functions().keySet());
            Path recordPath = CheckRecordFile.pathFor(markdown);
            checked.writeTo(recordPath);
            out.println(recordPath + ": " + summary(checked));
        }
    }

    /**
     * {@code render}: prints each markdown file with its om blocks and om spans replaced by LaTeX, so that
     * KaTeX can draw them. With an output directory, writes the files there under the same names instead.
     * @param markdownFiles the markdown files
     * @param outputDirectory where to write, or null to print
     * @throws IOException when a file cannot be read or written
     */
    public void render(List<Path> markdownFiles, Path outputDirectory) throws IOException {
        for (Path markdown : markdownFiles) {
            MarkdownDocument document = MarkdownDocument.read(markdown, toolchain.factory());
            String rendered = document.render(toolchain.projector());
            if (outputDirectory == null) {
                out.print(rendered);
            } else {
                Files.createDirectories(outputDirectory);
                Path target = outputDirectory.resolve(markdown.getFileName());
                Files.writeString(target, rendered, StandardCharsets.UTF_8);
                out.println(target.toString());
            }
        }
    }

    /**
     * {@code project}: prints every formula of markdown files for one target.
     * @param target the target
     * @param markdownFiles the markdown files
     * @throws IOException when a file cannot be read
     */
    public void project(Target target, List<Path> markdownFiles) throws IOException {
        for (Path markdown : markdownFiles) {
            MarkdownDocument document = MarkdownDocument.read(markdown, toolchain.factory());
            for (Map.Entry<String, Term> entry : document.equations(DocumentIdentifier.prefixFor(markdown)).entrySet()) {
                try {
                    out.println(entry.getKey() + "\t" + toolchain.projector().project(entry.getValue(), target));
                } catch (ProjectionException e) {
                    out.println(entry.getKey() + "\t(" + e.getMessage() + ")");
                }
            }
        }
    }

    /**
     * {@code report}: prints the count per status, the markers conversion left with their reasons, and the
     * suspect formulas with the difference the numeric check reported.
     * @param markdownFiles the markdown files
     * @throws IOException when a file cannot be read
     */
    public void report(List<Path> markdownFiles) throws IOException {
        Map<String, Integer> totals = new LinkedHashMap<>();
        Map<String, Integer> reasons = new LinkedHashMap<>();
        StringBuilder suspects = new StringBuilder();
        int all = 0;
        int unreadable = 0;
        for (Path markdown : markdownFiles) {
            MarkdownDocument document = MarkdownDocument.read(markdown, toolchain.factory());
            for (MarkdownPiece.UnreadableMarker marker : document.markers()) {
                unreadable++;
                reasons.merge(marker.reason(), 1, Integer::sum);
            }
            for (MarkdownPiece.OmError error : document.errors()) {
                out.println("malformed om source in " + markdown + ": " + error.source() + " (" + error.message() + ")");
            }
            Path recordPath = CheckRecordFile.pathFor(markdown);
            if (!Files.exists(recordPath)) {
                continue;
            }
            for (EquationRecord record : CheckRecordFile.read(recordPath).records()) {
                all++;
                totals.merge(record.status(), 1, Integer::sum);
                if (record.status().equals(":suspect")) {
                    suspects.append(record.id()).append('\n').append("  source: ").append(record.source()).append('\n');
                    for (Map.Entry<String, SExp> check : record.checks().entrySet()) {
                        if (check.getValue() instanceof SExp.SList detail) {
                            suspects.append("  ").append(check.getKey()).append(": ")
                                    .append(SexpWriter.writeFlat(detail)).append('\n');
                        } else if (check.getKey().equals(":smt") && check.getValue() instanceof SExp.SSymbol smt
                                && smt.name().equals(":sat")) {
                            suspects.append("  :smt: :sat\n");
                        }
                    }
                }
            }
        }
        out.println("formulas checked: " + all);
        for (String status : List.of(":ok", ":suspect", ":not-checkable")) {
            out.println("  " + status + ": " + totals.getOrDefault(status, 0));
        }
        out.println("still LaTeX (unreadable): " + unreadable);
        for (Map.Entry<String, Integer> entry : reasons.entrySet()) {
            out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
        if (!suspects.isEmpty()) {
            out.println("suspect formulas:");
            out.print(suspects);
        }
    }

    private static String summary(CheckRecordFile file) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (EquationRecord record : file.records()) {
            totals.merge(record.status(), 1, Integer::sum);
        }
        return totals.toString();
    }
}
