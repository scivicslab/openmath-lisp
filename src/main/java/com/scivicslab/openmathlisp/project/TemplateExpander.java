package com.scivicslab.openmathlisp.project;

import com.scivicslab.openmathlisp.symbols.Target;
import com.scivicslab.openmathlisp.term.Term;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expands {@code (:template ...)} rules; see {@link com.scivicslab.openmathlisp.symbols.Rule.Template}. */
final class TemplateExpander {

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "~(\\*(?:\\{([^}]*)\\})?|v|b|(\\d+)(?:\\.(\\d+|v\\d+|b))?)");

    private TemplateExpander() {
    }

    static String expand(String template, List<Term> args, Term.BindingTerm binding, Projector projector,
                         Target target, Integer priority) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement;
            if (matcher.group(1).startsWith("*")) {
                String separator = matcher.group(2) == null ? ", " : matcher.group(2);
                List<String> parts = new ArrayList<>();
                for (Term arg : args) {
                    parts.add(projectChild(arg, projector, target, priority));
                }
                replacement = String.join(separator, parts);
            } else if (matcher.group(1).equals("v")) {
                replacement = joinVariables(requireBinding(binding, template), target);
            } else if (matcher.group(1).equals("b")) {
                replacement = projectChild(requireBinding(binding, template).body(), projector, target, priority);
            } else {
                int index = Integer.parseInt(matcher.group(3)) - 1;
                if (index >= args.size()) {
                    throw new ProjectionException("template needs argument " + (index + 1) + " but only " + args.size() + " given: " + template);
                }
                Term arg = args.get(index);
                String part = matcher.group(4);
                if (part == null) {
                    replacement = projectChild(arg, projector, target, priority);
                } else if (part.equals("b")) {
                    replacement = projectChild(asBinding(arg, template).body(), projector, target, priority);
                } else if (part.startsWith("v")) {
                    int variableIndex = Integer.parseInt(part.substring(1)) - 1;
                    replacement = projector.project(asBinding(arg, template).variables().get(variableIndex), target);
                } else {
                    int elementIndex = Integer.parseInt(part) - 1;
                    if (!(arg instanceof Term.ApplicationTerm application) || elementIndex >= application.args().size()) {
                        throw new ProjectionException("template asks for element " + part + " of a non-application argument: " + template);
                    }
                    replacement = projectChild(application.args().get(elementIndex), projector, target, priority);
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String projectChild(Term child, Projector projector, Target target, Integer priority) {
        Projector.Projected projected = projector.projectWithPriority(child, target);
        if (priority == null) {
            return projected.text();
        }
        return projector.parenthesize(projected, priority, target);
    }

    private static Term.BindingTerm requireBinding(Term.BindingTerm binding, String template) {
        if (binding == null) {
            throw new ProjectionException("template uses ~v or ~b but the symbol is not a binder: " + template);
        }
        return binding;
    }

    private static Term.BindingTerm asBinding(Term arg, String template) {
        if (!(arg instanceof Term.BindingTerm binding)) {
            throw new ProjectionException("template expects a binding argument: " + template);
        }
        return binding;
    }

    static String joinVariables(Term.BindingTerm binding, Target target) {
        List<String> names = new ArrayList<>();
        for (Term.VariableTerm variable : binding.variables()) {
            names.add(VariableNames.render(variable.name(), target));
        }
        return String.join(", ", names);
    }
}
