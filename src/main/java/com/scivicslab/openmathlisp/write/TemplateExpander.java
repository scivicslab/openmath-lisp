package com.scivicslab.openmathlisp.write;

import com.scivicslab.openmathlisp.symbols.InputFormat;
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

    static String expand(String template, List<Term> args, Term.BindingTerm binding, TermWriter writer,
                         InputFormat format, Integer priority) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement;
            if (matcher.group(1).startsWith("*")) {
                String separator = matcher.group(2) == null ? ", " : matcher.group(2);
                List<String> parts = new ArrayList<>();
                for (Term arg : args) {
                    parts.add(writeChild(arg, writer, format, priority));
                }
                replacement = String.join(separator, parts);
            } else if (matcher.group(1).equals("v")) {
                replacement = joinVariables(requireBinding(binding, template), format);
            } else if (matcher.group(1).equals("b")) {
                replacement = writeChild(requireBinding(binding, template).body(), writer, format, priority);
            } else {
                int index = Integer.parseInt(matcher.group(3)) - 1;
                if (index >= args.size()) {
                    throw new TermWriterException("template needs argument " + (index + 1) + " but only " + args.size() + " given: " + template);
                }
                Term arg = args.get(index);
                String part = matcher.group(4);
                if (part == null) {
                    replacement = writeChild(arg, writer, format, priority);
                } else if (part.equals("b")) {
                    replacement = writeChild(asBinding(arg, template).body(), writer, format, priority);
                } else if (part.startsWith("v")) {
                    int variableIndex = Integer.parseInt(part.substring(1)) - 1;
                    replacement = writer.write(asBinding(arg, template).variables().get(variableIndex), format);
                } else {
                    int elementIndex = Integer.parseInt(part) - 1;
                    if (!(arg instanceof Term.ApplicationTerm application) || elementIndex >= application.args().size()) {
                        throw new TermWriterException("template asks for element " + part + " of a non-application argument: " + template);
                    }
                    replacement = writeChild(application.args().get(elementIndex), writer, format, priority);
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String writeChild(Term child, TermWriter writer, InputFormat format, Integer priority) {
        TermWriter.Written written = writer.writeWithPriority(child, format);
        if (priority == null) {
            return written.text();
        }
        return writer.parenthesize(written, priority, format);
    }

    private static Term.BindingTerm requireBinding(Term.BindingTerm binding, String template) {
        if (binding == null) {
            throw new TermWriterException("template uses ~v or ~b but the symbol is not a binder: " + template);
        }
        return binding;
    }

    private static Term.BindingTerm asBinding(Term arg, String template) {
        if (!(arg instanceof Term.BindingTerm binding)) {
            throw new TermWriterException("template expects a binding argument: " + template);
        }
        return binding;
    }

    static String joinVariables(Term.BindingTerm binding, InputFormat format) {
        List<String> names = new ArrayList<>();
        for (Term.VariableTerm variable : binding.variables()) {
            names.add(VariableNames.render(variable.name(), format));
        }
        return String.join(", ", names);
    }
}
