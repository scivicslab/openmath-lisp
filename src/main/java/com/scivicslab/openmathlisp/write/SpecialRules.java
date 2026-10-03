package com.scivicslab.openmathlisp.write;

import com.scivicslab.openmathlisp.symbols.InputFormat;
import com.scivicslab.openmathlisp.term.Term;

import java.util.ArrayList;
import java.util.List;

/** Procedures for {@code (:special "name")} rules that a template cannot express. */
final class SpecialRules {

    private SpecialRules() {
    }

    static TermWriter.Written apply(String name, List<Term> args, TermWriter writer, InputFormat format) {
        return switch (name) {
            case "partialdiff" -> partialDiff(args, writer, format);
            case "partialdiffdegree" -> partialDiffDegree(args, writer, format);
            case "root" -> root(args, writer, format);
            case "power" -> power(args, writer, format);
            default -> throw new TermWriterException("unknown special rule " + name);
        };
    }

    /**
     * {@code (calculus1:partialdiff (list1:list i j ...) (fns1:lambda (vars) body))}: the integers index the
     * bound variables; consecutive equal indices become one factor with a degree.
     */
    private static TermWriter.Written partialDiff(List<Term> args, TermWriter writer, InputFormat format) {
        if (args.size() != 2 || !(args.get(0) instanceof Term.ApplicationTerm indexList)
                || !(args.get(1) instanceof Term.BindingTerm function)) {
            throw new TermWriterException("partialdiff expects (list1:list ...) and a lambda");
        }
        List<Term.VariableTerm> variables = new ArrayList<>();
        for (Term index : indexList.args()) {
            if (!(index instanceof Term.IntegerTerm integer)) {
                throw new TermWriterException("partialdiff index must be an integer");
            }
            int position = integer.value().intValueExact() - 1;
            if (position < 0 || position >= function.variables().size()) {
                throw new TermWriterException("partialdiff index " + (position + 1) + " exceeds the bound variables");
            }
            variables.add(function.variables().get(position));
        }
        List<String> runs = new ArrayList<>();
        List<Integer> degrees = new ArrayList<>();
        for (Term.VariableTerm variable : variables) {
            String rendered = VariableNames.render(variable.name(), format);
            if (!runs.isEmpty() && runs.get(runs.size() - 1).equals(rendered)) {
                degrees.set(degrees.size() - 1, degrees.get(degrees.size() - 1) + 1);
            } else {
                runs.add(rendered);
                degrees.add(1);
            }
        }
        TermWriter.Written body = writer.writeWithPriority(function.body(), format);
        switch (format) {
            case LATEX -> {
                StringBuilder denominator = new StringBuilder();
                for (int i = 0; i < runs.size(); i++) {
                    denominator.append("\\partial ").append(runs.get(i));
                    if (degrees.get(i) > 1) {
                        denominator.append("^{").append(degrees.get(i)).append('}');
                    }
                    denominator.append(' ');
                }
                String order = variables.size() > 1 ? "^{" + variables.size() + "}" : "";
                String numerator = "\\partial" + order + " " + writer.parenthesize(body, 45, format);
                return new TermWriter.Written("\\frac{" + numerator + "}{" + denominator.toString().trim() + "}", 45);
            }
            case MAXIMA -> {
                StringBuilder out = new StringBuilder("diff(").append(body.text());
                for (int i = 0; i < runs.size(); i++) {
                    out.append(", ").append(runs.get(i)).append(", ").append(degrees.get(i));
                }
                return new TermWriter.Written(out.append(')').toString(), TermWriter.ATOM_PRIORITY);
            }
            default -> throw new TermWriterException("smt cannot express calculus1:partialdiff");
        }
    }

    /**
     * {@code (arith1:power b e)} in LaTeX: {@code {b}^{e}}. The base is parenthesized when it binds more
     * weakly than the power, because {@code {a + b}^{2}} shows no bracket and reads as {@code a + b^2}.
     * The exponent never is: the braces of {@code ^{...}} already group it.
     */
    private static TermWriter.Written power(List<Term> args, TermWriter writer, InputFormat format) {
        if (format != InputFormat.LATEX || args.size() != 2) {
            throw new TermWriterException("power special rule is only for LaTeX with 2 arguments");
        }
        TermWriter.Written base = writer.writeWithPriority(args.get(0), format);
        return new TermWriter.Written("{" + writer.parenthesize(base, 50, format) + "}^{"
                + writer.write(args.get(1), format) + "}", 50);
    }

    /** {@code (arith1:root x n)}: {@code \\sqrt{x}} when n is 2, else {@code \\sqrt[n]{x}}. */
    private static TermWriter.Written root(List<Term> args, TermWriter writer, InputFormat format) {
        if (format != InputFormat.LATEX || args.size() != 2) {
            throw new TermWriterException("root special rule is only for LaTeX with 2 arguments");
        }
        String radicand = writer.write(args.get(0), format);
        if (args.get(1) instanceof Term.IntegerTerm degree && degree.value().intValueExact() == 2) {
            return new TermWriter.Written("\\sqrt{" + radicand + "}", TermWriter.ATOM_PRIORITY);
        }
        return new TermWriter.Written("\\sqrt[" + writer.write(args.get(1), format) + "]{" + radicand + "}", TermWriter.ATOM_PRIORITY);
    }

    private static TermWriter.Written partialDiffDegree(List<Term> args, TermWriter writer, InputFormat format) {
        if (format != InputFormat.LATEX || args.size() != 3) {
            throw new TermWriterException("partialdiffdegree is only written to LaTeX with 3 arguments");
        }
        String degree = writer.write(args.get(1), format);
        String function = writer.write(args.get(2), format);
        String indices = writer.write(args.get(0), format);
        return new TermWriter.Written("\\partial^{" + degree + "}_{" + indices + "} " + function, 45);
    }
}
