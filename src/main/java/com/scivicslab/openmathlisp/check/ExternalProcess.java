package com.scivicslab.openmathlisp.check;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs an external program with text on standard input and returns its standard output. */
public final class ExternalProcess {

    private static final Logger LOGGER = Logger.getLogger(ExternalProcess.class.getName());

    private ExternalProcess() {
    }

    /**
     * Runs the command, feeding the input and collecting the output.
     * @param command the program and its arguments
     * @param input the text for standard input
     * @param timeoutSeconds how long to wait before killing the process
     * @return standard output, or null when the program is not installed or was killed
     */
    public static String run(List<String> command, String input, long timeoutSeconds) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "cannot start " + command.get(0) + ": " + e.getMessage(), e);
            return null;
        }
        try {
            Thread writer = Thread.ofVirtual().start(() -> {
                try (var out = process.getOutputStream()) {
                    out.write(input.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "writing to " + command.get(0) + " failed: " + e.getMessage());
                }
            });
            byte[] output;
            try (var in = process.getInputStream()) {
                output = in.readAllBytes();
            }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOGGER.log(Level.SEVERE, command.get(0) + " exceeded " + timeoutSeconds + " s and was killed");
                return null;
            }
            writer.join();
            return new String(output, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException e) {
            process.destroyForcibly();
            LOGGER.log(Level.SEVERE, command.get(0) + " failed: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Tells whether a program is on the PATH.
     * @param program the program name
     * @return true when it can be found
     */
    public static boolean isAvailable(String program) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (java.nio.file.Files.isExecutable(java.nio.file.Path.of(dir, program))) {
                return true;
            }
        }
        return false;
    }
}
