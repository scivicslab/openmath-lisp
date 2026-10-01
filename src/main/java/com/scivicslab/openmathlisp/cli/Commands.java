package com.scivicslab.openmathlisp.cli;

import com.scivicslab.openmathlisp.project.ProjectionException;
import com.scivicslab.openmathlisp.record.Declaration;
import com.scivicslab.openmathlisp.record.EquationRecord;
import com.scivicslab.openmathlisp.record.MarkdownEquations;
import com.scivicslab.openmathlisp.record.TermFile;
import com.scivicslab.openmathlisp.sexp.SExp;
import com.scivicslab.openmathlisp.sexp.SexpWriter;
import com.scivicslab.openmathlisp.symbols.Target;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The four subcommands of TermFileAndCheckRecord_261002_oo01, decision 6: read, check, project, report. */
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
     * {@code read}: reads the blocks of markdown files into term files beside them, keeping declarations and
     * edited terms.
     * @param markdownFiles the markdown files
     * @throws IOException when a file cannot be read or written
     */
    public void read(List<Path> markdownFiles) throws IOException {
        for (Path markdown : markdownFiles) {
            Path termPath = TermFile.pathFor(markdown);
            TermFile existing = Files.exists(termPath) ? TermFile.read(termPath, toolchain.factory())
                    : new TermFile(Declaration.empty(), List.of());
            List<EquationRecord> fresh = MarkdownEquations.read(markdown, existing.declaration());
            TermFile merged = existing.mergeFresh(fresh);
            merged.writeTo(termPath);
            long unreadable = merged.records().stream().filter((EquationRecord r) -> r.term().isEmpty()).count();
            out.println(termPath + ": " + merged.records().size() + " equations, " + unreadable + " unreadable");
        }
    }

    /**
     * {@code check}: runs the four checks on term files and rewrites them.
     * @param termFiles the term files
     * @throws IOException when a file cannot be read or written
     */
    public void check(List<Path> termFiles) throws IOException {
        for (Path path : termFiles) {
            TermFile file = TermFile.read(path, toolchain.factory());
            TermFile checked = toolchain.checker().check(file);
            checked.writeTo(path);
            out.println(path + ": " + summary(checked));
        }
    }

    /**
     * {@code project}: prints every equation of term files for one target.
     * @param target the target
     * @param termFiles the term files
     * @throws IOException when a file cannot be read
     */
    public void project(Target target, List<Path> termFiles) throws IOException {
        for (Path path : termFiles) {
            TermFile file = TermFile.read(path, toolchain.factory());
            for (EquationRecord record : file.records()) {
                if (record.term().isEmpty()) {
                    out.println(record.id() + "\t(unreadable: " + SexpWriter.writeFlat(record.checks().get(":parse")) + ")");
                    continue;
                }
                try {
                    out.println(record.id() + "\t" + toolchain.projector().project(record.term().get(), target));
                } catch (ProjectionException e) {
                    out.println(record.id() + "\t(" + e.getMessage() + ")");
                }
            }
        }
    }

    /**
     * {@code report}: prints the count per status and the suspect equations.
     * @param termFiles the term files
     * @throws IOException when a file cannot be read
     */
    public void report(List<Path> termFiles) throws IOException {
        Map<String, Integer> totals = new LinkedHashMap<>();
        Map<String, Integer> reasons = new LinkedHashMap<>();
        StringBuilder suspects = new StringBuilder();
        int all = 0;
        for (Path path : termFiles) {
            TermFile file = TermFile.read(path, toolchain.factory());
            for (EquationRecord record : file.records()) {
                all++;
                totals.merge(record.status(), 1, Integer::sum);
                if (record.status().equals(":unparseable") && record.checks().get(":parse") instanceof SExp.SList failure
                        && failure.items().size() > 1 && failure.items().get(1) instanceof SExp.SString reason) {
                    reasons.merge(reason.value(), 1, Integer::sum);
                }
                if (record.status().equals(":suspect")) {
                    suspects.append(record.id()).append('\n').append("  source: ").append(record.source()).append('\n');
                    for (Map.Entry<String, SExp> check : record.checks().entrySet()) {
                        if (check.getValue() instanceof SExp.SList detail) {
                            suspects.append("  ").append(check.getKey()).append(": ").append(SexpWriter.writeFlat(detail)).append('\n');
                        }
                    }
                    if (record.checks().get(":smt") instanceof SExp.SSymbol smt && smt.name().equals(":sat")) {
                        suspects.append("  :smt: :sat\n");
                    }
                }
            }
        }
        out.println("equations: " + all);
        for (String status : List.of(":ok", ":suspect", ":not-checkable", ":unparseable")) {
            out.println("  " + status + ": " + totals.getOrDefault(status, 0));
        }
        if (!reasons.isEmpty()) {
            out.println("unparseable by reason:");
            for (Map.Entry<String, Integer> entry : reasons.entrySet()) {
                out.println("  " + entry.getKey() + ": " + entry.getValue());
            }
        }
        if (!suspects.isEmpty()) {
            out.println("suspect equations:");
            out.print(suspects);
        }
    }

    private static String summary(TermFile file) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (EquationRecord record : file.records()) {
            totals.merge(record.status(), 1, Integer::sum);
        }
        return totals.toString();
    }
}
