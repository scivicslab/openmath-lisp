package com.scivicslab.openmathlisp.cli;

import com.scivicslab.openmathlisp.check.EquationChecker;
import com.scivicslab.openmathlisp.check.ExternalProcess;
import com.scivicslab.openmathlisp.check.NumericChecker;
import com.scivicslab.openmathlisp.check.SmtChecker;
import com.scivicslab.openmathlisp.check.StructureChecker;
import com.scivicslab.openmathlisp.write.TermWriter;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.util.List;

/** The objects every command shares: symbol table, factory, writer and the checkers wired to Maxima and Z3. */
public final class Toolchain {

    private static final long MAXIMA_TIMEOUT_SECONDS = 120;
    private static final long Z3_TIMEOUT_SECONDS = 120;

    private final SymbolTable symbols;
    private final TermFactory factory;
    private final TermWriter writer;
    private final EquationChecker checker;

    /** Creates the toolchain over the bundled symbol table. */
    public Toolchain() {
        this.symbols = SymbolTable.loadBundled();
        this.factory = new TermFactory(symbols);
        this.writer = new TermWriter(symbols);
        NumericChecker numeric = new NumericChecker(writer, (String script) -> {
            dump("maxima", script);
            return ExternalProcess.isAvailable("maxima")
                    ? ExternalProcess.run(List.of("maxima", "--very-quiet"), script, MAXIMA_TIMEOUT_SECONDS) : null;
        });
        SmtChecker smt = new SmtChecker(writer, (String script) -> {
            dump("z3", script);
            return ExternalProcess.isAvailable("z3")
                    ? ExternalProcess.run(List.of("z3", "-in", "-t:5000"), script, Z3_TIMEOUT_SECONDS) : null;
        });
        this.checker = new EquationChecker(new StructureChecker(symbols), numeric, smt, writer);
    }

    /** Writes the script to the directory named by OPENMATH_LISP_DUMP_SCRIPTS, when that variable is set. */
    private static void dump(String program, String script) {
        String directory = System.getenv("OPENMATH_LISP_DUMP_SCRIPTS");
        if (directory == null) {
            return;
        }
        try {
            java.nio.file.Path format = java.nio.file.Path.of(directory, program + "-" + System.currentTimeMillis() + ".txt");
            java.nio.file.Files.writeString(format, script);
        } catch (java.io.IOException e) {
            java.util.logging.Logger.getLogger(Toolchain.class.getName()).warning("cannot dump script: " + e.getMessage());
        }
    }

    /** @return the symbol table */
    public SymbolTable symbols() {
        return symbols;
    }

    /** @return the term factory */
    public TermFactory factory() {
        return factory;
    }

    /** @return the writer */
    public TermWriter writer() {
        return writer;
    }

    /** @return the checker */
    public EquationChecker checker() {
        return checker;
    }
}
