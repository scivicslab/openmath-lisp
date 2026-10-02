package com.scivicslab.openmathlisp.project;

import com.scivicslab.openmathlisp.symbols.Target;
import com.scivicslab.openmathlisp.term.Term;

import java.util.ArrayList;
import java.util.List;

/** Procedures for {@code (:special "name")} rules that a template cannot express. */
final class SpecialRules {

    private SpecialRules() {
    }

    static Projector.Projected apply(String name, List<Term> args, Projector projector, Target target) {
        return switch (name) {
            case "partialdiff" -> partialDiff(args, projector, target);
            case "partialdiffdegree" -> partialDiffDegree(args, projector, target);
            case "root" -> root(args, projector, target);
            case "power" -> power(args, projector, target);
            default -> throw new ProjectionException("unknown special rule " + name);
        };
    }

    /**
     * {@code (calculus1:partialdiff (list1:list i j ...) (fns1:lambda (vars) body))}: the integers index the
     * bound variables; consecutive equal indices become one factor with a degree.
     */
    private static Projector.Projected partialDiff(List<Term> args, Projector projector, Target target) {
        if (args.size() != 2 || !(args.get(0) instanceof Term.ApplicationTerm indexList)
                || !(args.get(1) instanceof Term.BindingTerm function)) {
            throw new ProjectionException("partialdiff expects (list1:list ...) and a lambda");
        }
        List<Term.VariableTerm> variables = new ArrayList<>();
        for (Term index : indexList.args()) {
            if (!(index instanceof Term.IntegerTerm integer)) {
                throw new ProjectionException("partialdiff index must be an integer");
            }
            int position = integer.value().intValueExact() - 1;
            if (position < 0 || position >= function.variables().size()) {
                throw new ProjectionException("partialdiff index " + (position + 1) + " exceeds the bound variables");
            }
            variables.add(function.variables().get(position));
        }
        List<String> runs = new ArrayList<>();
        List<Integer> degrees = new ArrayList<>();
        for (Term.VariableTerm variable : variables) {
            String rendered = VariableNames.render(variable.name(), target);
            if (!runs.isEmpty() && runs.get(runs.size() - 1).equals(rendered)) {
                degrees.set(degrees.size() - 1, degrees.get(degrees.size() - 1) + 1);
            } else {
                runs.add(rendered);
                degrees.add(1);
            }
        }
        Projector.Projected body = projector.projectWithPriority(function.body(), target);
        switch (target) {
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
                String numerator = "\\partial" + order + " " + projector.parenthesize(body, 45, target);
                return new Projector.Projected("\\frac{" + numerator + "}{" + denominator.toString().trim() + "}", 45);
            }
            case MAXIMA -> {
                StringBuilder out = new StringBuilder("diff(").append(body.text());
                for (int i = 0; i < runs.size(); i++) {
                    out.append(", ").append(runs.get(i)).append(", ").append(degrees.get(i));
                }
                return new Projector.Projected(out.append(')').toString(), Projector.ATOM_PRIORITY);
            }
            default -> throw new ProjectionException("smt cannot express calculus1:partialdiff");
        }
    }

    /**
     * {@code (arith1:power b e)} in LaTeX: {@code {b}^{e}}. The base is parenthesized when it binds more
     * weakly than the power, because {@code {a + b}^{2}} shows no bracket and reads as {@code a + b^2}.
     * The exponent never is: the braces of {@code ^{...}} already group it.
     */
    private static Projector.Projected power(List<Term> args, Projector projector, Target target) {
        if (target != Target.LATEX || args.size() != 2) {
            throw new ProjectionException("power special rule is only for LaTeX with 2 arguments");
        }
        Projector.Projected base = projector.projectWithPriority(args.get(0), target);
        return new Projector.Projected("{" + projector.parenthesize(base, 50, target) + "}^{"
                + projector.project(args.get(1), target) + "}", 50);
    }

    /** {@code (arith1:root x n)}: {@code \\sqrt{x}} when n is 2, else {@code \\sqrt[n]{x}}. */
    private static Projector.Projected root(List<Term> args, Projector projector, Target target) {
        if (target != Target.LATEX || args.size() != 2) {
            throw new ProjectionException("root special rule is only for LaTeX with 2 arguments");
        }
        String radicand = projector.project(args.get(0), target);
        if (args.get(1) instanceof Term.IntegerTerm degree && degree.value().intValueExact() == 2) {
            return new Projector.Projected("\\sqrt{" + radicand + "}", Projector.ATOM_PRIORITY);
        }
        return new Projector.Projected("\\sqrt[" + projector.project(args.get(1), target) + "]{" + radicand + "}", Projector.ATOM_PRIORITY);
    }

    private static Projector.Projected partialDiffDegree(List<Term> args, Projector projector, Target target) {
        if (target != Target.LATEX || args.size() != 3) {
            throw new ProjectionException("partialdiffdegree is only written to LaTeX with 3 arguments");
        }
        String degree = projector.project(args.get(1), target);
        String function = projector.project(args.get(2), target);
        String indices = projector.project(args.get(0), target);
        return new Projector.Projected("\\partial^{" + degree + "}_{" + indices + "} " + function, 45);
    }
}
