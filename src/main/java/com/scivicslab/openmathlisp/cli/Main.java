package com.scivicslab.openmathlisp.cli;

import com.scivicslab.openmathlisp.symbols.Target;
import com.scivicslab.pluggablecli.CommandRepository;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point of the {@code openmath-lisp} command line tool.
 * Usage: {@code openmath-lisp <read|check|project|report> [options] <files...>}.
 */
public final class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    /**
     * Runs the tool.
     * @param args the subcommand, its options and the files
     */
    public static void main(String[] args) {
        CommandRepository commands = new CommandRepository();
        Toolchain toolchain = new Toolchain();
        Commands implementation = new Commands(toolchain, System.out);

        commands.addCommand("Equations", "read", new Options(),
                "Read the $$ blocks of markdown files into term files (X.md -> X.lisp)",
                (CommandLine cl) -> run(() -> implementation.read(paths(cl))));
        commands.addCommand("Equations", "check", new Options(),
                "Run the structural, numeric (Maxima) and SMT (Z3) checks on term files",
                (CommandLine cl) -> run(() -> implementation.check(paths(cl))));
        Options projectOptions = new Options();
        projectOptions.addOption(Option.builder("t").longOpt("target").hasArg().argName("latex|maxima|smt")
                .desc("projection target (default latex)").build());
        commands.addCommand("Equations", "project", projectOptions,
                "Print every equation of term files for one target",
                (CommandLine cl) -> run(() -> implementation.project(Target.fromName(cl.getOptionValue("target", "latex")), paths(cl))));
        commands.addCommand("Equations", "report", new Options(),
                "Print the count per status and the suspect equations of term files",
                (CommandLine cl) -> run(() -> implementation.report(paths(cl))));

        if (args.length == 0) {
            commands.printCommandList("openmath-lisp <command> [options] <files...>");
            return;
        }
        try {
            CommandLine cl = commands.parse(args);
            String given = commands.getGivenCommand();
            if (given == null) {
                commands.printCommandList("openmath-lisp <command> [options] <files...>");
            } else if (commands.isHelpRequested()) {
                commands.printCommandHelp(given);
            } else if (commands.hasCommand(given)) {
                commands.execute(given, cl);
            } else {
                System.err.println("Unknown command: " + given);
                System.exit(1);
            }
        } catch (ParseException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static List<Path> paths(CommandLine cl) {
        List<Path> paths = new ArrayList<>();
        for (String arg : cl.getArgs()) {
            paths.add(Path.of(arg));
        }
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("no files given");
        }
        return paths;
    }

    /** An action that may fail with an I/O error. */
    @FunctionalInterface
    interface Action {
        void run() throws IOException;
    }

    private static void run(Action action) {
        try {
            action.run();
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
