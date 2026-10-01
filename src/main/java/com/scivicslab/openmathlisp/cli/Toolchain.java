package com.scivicslab.openmathlisp.cli;

import com.scivicslab.openmathlisp.check.EquationChecker;
import com.scivicslab.openmathlisp.check.ExternalProcess;
import com.scivicslab.openmathlisp.check.NumericChecker;
import com.scivicslab.openmathlisp.check.SmtChecker;
import com.scivicslab.openmathlisp.check.StructureChecker;
import com.scivicslab.openmathlisp.project.Projector;
import com.scivicslab.openmathlisp.symbols.SymbolTable;
import com.scivicslab.openmathlisp.term.TermFactory;

import java.util.List;

/** The objects every command shares: symbol table, factory, projector and the checkers wired to Maxima and Z3. */
public final class Toolchain {

    private static final long MAXIMA_TIMEOUT_SECONDS = 120;
    private static final long Z3_TIMEOUT_SECONDS = 120;

    private final SymbolTable symbols;
    private final TermFactory factory;
    private final Projector projector;
    private final EquationChecker checker;

    /** Creates the toolchain over the bundled symbol table. */
    public Toolchain() {
        this.symbols = SymbolTable.loadBundled();
        this.factory = new TermFactory(symbols);
        this.projector = new Projector(symbols);
        NumericChecker numeric = new NumericChecker(projector, (String script) -> {
            dump("maxima", script);
            return ExternalProcess.isAvailable("maxima")
                    ? ExternalProcess.run(List.of("maxima", "--very-quiet"), script, MAXIMA_TIMEOUT_SECONDS) : null;
        });
        SmtChecker smt = new SmtChecker(projector, (String script) -> {
            dump("z3", script);
            return ExternalProcess.isAvailable("z3")
                    ? ExternalProcess.run(List.of("z3", "-in", "-t:5000"), script, Z3_TIMEOUT_SECONDS) : null;
        });
        this.checker = new EquationChecker(new StructureChecker(symbols), numeric, smt);
    }

    /** Writes the script to the directory named by OPENMATH_LISP_DUMP_SCRIPTS, when that variable is set. */
    private static void dump(String program, String script) {
        String directory = System.getenv("OPENMATH_LISP_DUMP_SCRIPTS");
        if (directory == null) {
            return;
        }
        try {
            java.nio.file.Path target = java.nio.file.Path.of(directory, program + "-" + System.currentTimeMillis() + ".txt");
            java.nio.file.Files.writeString(target, script);
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

    /** @return the projector */
    public Projector projector() {
        return projector;
    }

    /** @return the checker */
    public EquationChecker checker() {
        return checker;
    }
}
