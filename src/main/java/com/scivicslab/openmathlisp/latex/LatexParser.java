package com.scivicslab.openmathlisp.latex;

import com.scivicslab.openmathlisp.project.VariableNames;
import com.scivicslab.openmathlisp.record.Declaration;
import com.scivicslab.openmathlisp.term.Term;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recursive-descent parser from LaTeX tokens to terms, following LatexReaderDecisions_261002_oo01:
 * juxtaposition is multiplication, a parenthesis-free function takes the product up to the next additive
 * operator, derivatives and integrals name their variables explicitly, and context-dependent letters
 * come from the {@link Declaration}.
 */
public final class LatexParser {

    private static final Map<String, String> RELATIONS = Map.of(
            "=", "relation1:eq", "<", "relation1:lt", ">", "relation1:gt",
            "neq", "relation1:neq", "ne", "relation1:neq", "leq", "relation1:leq", "le", "relation1:leq",
            "geq", "relation1:geq", "ge", "relation1:geq", "approx", "relation1:approx");

    private static final Set<String> TRIG = Set.of("sin", "cos", "tan", "sec", "csc", "cot",
            "sinh", "cosh", "tanh", "arcsin", "arccos", "arctan", "ln", "log", "exp");

    private static final Map<String, String> UNSUPPORTED = Map.ofEntries(
            Map.entry("dots", "ellipsis"), Map.entry("cdots", "ellipsis"), Map.entry("ldots", "ellipsis"),
            Map.entry("vdots", "ellipsis"), Map.entry("ddots", "ellipsis"),
            Map.entry("pm", "plus-minus"), Map.entry("mp", "plus-minus"),
            Map.entry("oint", "multiple-integral"), Map.entry("iint", "multiple-integral"), Map.entry("iiint", "multiple-integral"),
            Map.entry("vec", "accent"), Map.entry("bar", "accent"), Map.entry("overline", "accent"), Map.entry("hat", "accent"),
            Map.entry("tilde", "accent"), Map.entry("mathcal", "accent"), Map.entry("boldsymbol", "accent"),
            Map.entry("mathbf", "accent"), Map.entry("stackrel", "accent"), Map.entry("underline", "accent"),
            Map.entry("to", "arrow"), Map.entry("rightarrow", "arrow"), Map.entry("Rightarrow", "arrow"),
            Map.entry("Leftrightarrow", "arrow"), Map.entry("leftrightarrow", "arrow"), Map.entry("uparrow", "arrow"),
            Map.entry("mid", "arrow"), Map.entry("ni", "arrow"), Map.entry("notin", "arrow"), Map.entry("in", "arrow"),
            Map.entry("bowtie", "arrow"), Map.entry("sim", "arrow"), Map.entry("propto", "arrow"), Map.entry("equiv", "arrow"),
            Map.entry("begin", "array"), Map.entry("end", "array"),
            Map.entry("lim", "limit"), Map.entry("prod", "product"), Map.entry("binom", "binomial"));

    private final List<LatexToken> tokens;
    private final Declaration declaration;
    private int index;

    private LatexParser(List<LatexToken> tokens, Declaration declaration) {
        this.tokens = tokens;
        this.declaration = declaration;
        this.index = 0;
    }

    /**
     * Parses one unit of text (a line without alignment marks) into its chain of relations.
     * @param text the LaTeX text
     * @param declaration the file's declaration
     * @return the operands and relations: for {@code a = b < c} the operands {@code [a, b, c]} and the relation
     *         symbols {@code [eq, lt]}; a text without relation gives one operand and no relation
     * @throws LatexReadException when the text cannot be read
     */
    public static RelationChain parse(String text, Declaration declaration) {
        LatexParser parser = new LatexParser(LatexTokenizer.tokenize(text), declaration);
        if (parser.peek().kind() == LatexToken.Kind.END) {
            throw new LatexReadException("empty", "nothing but spacing", 0);
        }
        if (parser.relationAt(parser.peek()) != null) {
            throw new LatexReadException("continuation", "line starts with a relation but the previous line was not read", 0);
        }
        RelationChain chain = parser.parseRelationChain();
        if (parser.peek().kind() != LatexToken.Kind.END) {
            throw parser.syntax("unexpected '" + parser.peek().text() + "'");
        }
        return chain;
    }

    /**
     * Parses a text that begins with a relation (a continuation line) using the given left operand.
     * @param leftOperand the right-hand side of the previous line
     * @param text the LaTeX text starting with a relation
     * @param declaration the file's declaration
     * @return the chain
     */
    public static RelationChain parseContinuation(Term leftOperand, String text, Declaration declaration) {
        LatexParser parser = new LatexParser(LatexTokenizer.tokenize(text), declaration);
        List<Term> operands = new ArrayList<>();
        List<Term.SymbolTerm> relations = new ArrayList<>();
        operands.add(leftOperand);
        while (parser.relationAt(parser.peek()) != null) {
            relations.add(parser.relationAt(parser.next()));
            operands.add(parser.parseSum(false));
        }
        if (parser.peek().kind() != LatexToken.Kind.END) {
            throw parser.syntax("unexpected '" + parser.peek().text() + "'");
        }
        return new RelationChain(operands, relations);
    }

    /** Operands and the relations between consecutive operands. */
    public record RelationChain(List<Term> operands, List<Term.SymbolTerm> relations) {
    }

    /**
     * Tells whether a text starts with a relation symbol, i.e. is a continuation line.
     * @param text the LaTeX text
     * @return true when the first token is a relation
     */
    public static boolean startsWithRelation(String text) {
        List<LatexToken> tokens = LatexTokenizer.tokenize(text);
        return !tokens.isEmpty() && new LatexParser(tokens, Declaration.empty()).relationAt(tokens.get(0)) != null;
    }

    // ---- grammar ----

    private RelationChain parseRelationChain() {
        List<Term> operands = new ArrayList<>();
        List<Term.SymbolTerm> relations = new ArrayList<>();
        operands.add(parseSum(false));
        while (relationAt(peek()) != null) {
            relations.add(relationAt(next()));
            operands.add(parseSum(false));
        }
        return new RelationChain(operands, relations);
    }

    private Term.SymbolTerm relationAt(LatexToken token) {
        String name = null;
        if (token.kind() == LatexToken.Kind.PUNCT && RELATIONS.containsKey(token.text())) {
            name = RELATIONS.get(token.text());
        } else if (token.kind() == LatexToken.Kind.COMMAND && RELATIONS.containsKey(token.text())) {
            name = RELATIONS.get(token.text());
        }
        return name == null ? null : symbol(name);
    }

    /** sum := ['-'] product (('+'|'-') product)* ; stops before a differential when inside an integrand. */
    private Term parseSum(boolean insideIntegrand) {
        Term left;
        if (peek().isPunct("-")) {
            next();
            left = unaryMinus(parseProduct(insideIntegrand));
        } else {
            if (peek().isPunct("+")) {
                next();
            }
            left = parseProduct(insideIntegrand);
        }
        List<Term> plusTerms = new ArrayList<>();
        plusTerms.add(left);
        while (peek().isPunct("+") || peek().isPunct("-")) {
            boolean minus = next().isPunct("-");
            if (insideIntegrand && atDifferential()) {
                break;
            }
            Term right = parseProduct(insideIntegrand);
            if (minus) {
                Term accumulated = plusTerms.size() == 1 ? plusTerms.get(0) : apply("arith1:plus", plusTerms);
                plusTerms = new ArrayList<>();
                plusTerms.add(apply("arith1:minus", List.of(accumulated, right)));
            } else {
                plusTerms.add(right);
            }
        }
        return plusTerms.size() == 1 ? plusTerms.get(0) : apply("arith1:plus", plusTerms);
    }

    /** product := factor (('/' factor) | ('\cdot'|'\times')? factor)* */
    private Term parseProduct(boolean insideIntegrand) {
        List<Term> factors = new ArrayList<>();
        factors.add(parseFactor(insideIntegrand));
        while (true) {
            if (insideIntegrand && atDifferential()) {
                break;
            }
            if (peek().isPunct("/")) {
                next();
                Term denominator = parseFactor(insideIntegrand);
                Term numerator = factors.size() == 1 ? factors.get(0) : apply("arith1:times", factors);
                factors = new ArrayList<>();
                factors.add(apply("arith1:divide", List.of(numerator, denominator)));
                continue;
            }
            if (peek().isCommand("cdot") || peek().isCommand("times")) {
                String operator = next().text();
                Term right = parseFactor(insideIntegrand);
                Term left = factors.get(factors.size() - 1);
                if (isVector(left) && isVector(right)) {
                    factors.remove(factors.size() - 1);
                    factors.add(apply(operator.equals("cdot") ? "linalg1:scalarproduct" : "linalg1:vectorproduct", List.of(left, right)));
                } else {
                    factors.add(right);
                }
                continue;
            }
            if (startsFactor(peek())) {
                factors.add(parseFactor(insideIntegrand));
                continue;
            }
            break;
        }
        return factors.size() == 1 ? factors.get(0) : apply("arith1:times", factors);
    }

    private boolean isVector(Term term) {
        return term instanceof Term.VariableTerm variable && declaration.vectors().contains(baseName(variable.name()));
    }

    private static String baseName(String name) {
        int cut = name.length();
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) == '_' || name.charAt(i) == '^' || name.charAt(i) == '\'') {
                cut = i;
                break;
            }
        }
        return name.substring(0, cut);
    }

    private boolean startsFactor(LatexToken token) {
        switch (token.kind()) {
            case NUMBER, LETTER -> {
                return true;
            }
            case PUNCT -> {
                return token.text().equals("(") || token.text().equals("[") || token.text().equals("{");
            }
            case COMMAND -> {
                String name = token.text();
                return VariableNames.isGreek(name) || TRIG.contains(name) || name.equals("frac") || name.equals("sqrt")
                        || name.equals("int") || name.equals("sum") || name.equals("nabla") || name.equals("dot")
                        || name.equals("ddot") || name.equals("operatorname") || name.equals("text") || name.equals("pi")
                        || name.equals("infty") || name.equals("{") || name.equals("|") || name.equals("partial")
                        || name.equals("lim") || name.equals("prod") || UNSUPPORTED.containsKey(name);
            }
            default -> {
                return false;
            }
        }
    }

    /** True when the next tokens are a differential {@code d<variable>} closing an integrand. */
    private boolean atDifferential() {
        LatexToken token = peek();
        if (token.kind() != LatexToken.Kind.LETTER || !token.text().equals("d")) {
            return false;
        }
        LatexToken following = peekAt(1);
        return following.kind() == LatexToken.Kind.LETTER
                || (following.kind() == LatexToken.Kind.COMMAND && VariableNames.isGreek(following.text()));
    }

    /** factor := atom ('^' group | '!' )* */
    private Term parseFactor(boolean insideIntegrand) {
        Term atom = parseAtom(insideIntegrand);
        while (true) {
            if (peek().isPunct("^")) {
                next();
                Term exponent = parseGroupOrSingle();
                atom = power(atom, exponent);
            } else if (peek().isPunct("!")) {
                next();
                atom = apply("integer1:factorial", List.of(atom));
            } else {
                return atom;
            }
        }
    }

    private Term power(Term base, Term exponent) {
        if (base instanceof Term.VariableTerm variable && declaration.euler().isPresent()
                && variable.name().equals(declaration.euler().get())) {
            return apply("transc1:exp", List.of(exponent));
        }
        return apply("arith1:power", List.of(base, exponent));
    }

    private Term parseAtom(boolean insideIntegrand) {
        LatexToken token = peek();
        switch (token.kind()) {
            case NUMBER -> {
                next();
                return number(token.text());
            }
            case LETTER -> {
                next();
                return variableWithSubscript(token.text(), token.position());
            }
            case PUNCT -> {
                return parsePunctAtom(token);
            }
            case COMMAND -> {
                return parseCommandAtom(token, insideIntegrand);
            }
            default -> throw syntax("unexpected end of input");
        }
    }

    private Term parsePunctAtom(LatexToken token) {
        switch (token.text()) {
            case "(" -> {
                next();
                Term inner = parseSum(false);
                expectPunct(")");
                return inner;
            }
            case "[" -> {
                next();
                Term inner = parseSum(false);
                expectPunct("]");
                return inner;
            }
            case "{" -> {
                next();
                Term inner = parseSum(false);
                expectPunct("}");
                return inner;
            }
            case "|" -> {
                next();
                Term inner = parseSum(false);
                expectPunct("|");
                return apply("arith1:abs", List.of(inner));
            }
            default -> throw syntax("unexpected '" + token.text() + "'");
        }
    }

    private Term parseCommandAtom(LatexToken token, boolean insideIntegrand) {
        String name = token.text();
        if (UNSUPPORTED.containsKey(name)) {
            throw new LatexReadException(UNSUPPORTED.get(name), "\\" + name, token.position());
        }
        next();
        switch (name) {
            case "{" -> {
                Term inner = parseSum(false);
                if (!peek().isCommand("}")) {
                    throw syntax("expected \\}");
                }
                next();
                return inner;
            }
            case "|" -> {
                Term inner = parseSum(false);
                if (!peek().isCommand("|")) {
                    throw syntax("expected \\|");
                }
                next();
                return apply("arith1:abs", List.of(inner));
            }
            case "pi" -> {
                return symbol("nums1:pi");
            }
            case "infty" -> {
                return symbol("nums1:infinity");
            }
            case "frac" -> {
                return parseFraction(token.position());
            }
            case "sqrt" -> {
                Term degree = null;
                if (peek().isPunct("[")) {
                    next();
                    degree = parseSum(false);
                    expectPunct("]");
                }
                Term radicand = parseGroup();
                return apply("arith1:root", List.of(radicand, degree == null ? number("2") : degree));
            }
            case "dot", "ddot" -> {
                return parseDotted(name, token.position());
            }
            case "int" -> {
                return parseIntegral(token.position());
            }
            case "sum" -> {
                return parseSum(token.position());
            }
            case "nabla" -> {
                return parseNabla();
            }
            case "operatorname", "text" -> {
                return parseNamedOperator(name, token.position());
            }
            case "partial" -> throw syntax("\\partial outside a derivative");
            default -> {
                if (VariableNames.isGreek(name)) {
                    return variableWithSubscript(name, token.position());
                }
                if (TRIG.contains(name)) {
                    return parseFunction(name, insideIntegrand);
                }
                throw new LatexReadException("syntax", "unknown command \\" + name, token.position());
            }
        }
    }

    private Term variableWithSubscript(String base, int position) {
        String name = base;
        while (peek().isPunct("'")) {
            next();
            name = name + "'";
        }
        if (peek().isPunct("_")) {
            next();
            name = name + "_" + parseLabel(position);
        }
        while (peek().isPunct("'")) {
            next();
            name = name + "'";
        }
        if (declaration.imaginary().isPresent() && name.equals(declaration.imaginary().get())) {
            return symbol("nums1:i");
        }
        if (declaration.functions().containsKey(name)) {
            return declaredFunctionApplication(name);
        }
        return variable(name);
    }

    /** A declared dependent variable: {@code f(a, b)} with explicit arguments, or applied to its declared ones. */
    private Term declaredFunctionApplication(String name) {
        List<Term> args = new ArrayList<>();
        if (peek().isPunct("(")) {
            next();
            args.add(parseSum(false));
            while (peek().isPunct(",")) {
                next();
                args.add(parseSum(false));
            }
            expectPunct(")");
        } else {
            for (String argument : declaration.functions().get(name)) {
                args.add(variable(argument));
            }
        }
        return apply(variable(name), args);
    }

    /** A subscript label: letters and digits (braced or a single token); anything else is unsupported. */
    private String parseLabel(int position) {
        StringBuilder label = new StringBuilder();
        if (peek().isPunct("{")) {
            next();
            while (!peek().isPunct("}")) {
                LatexToken t = next();
                if (t.kind() == LatexToken.Kind.LETTER || t.kind() == LatexToken.Kind.NUMBER) {
                    label.append(t.text());
                } else if (t.kind() == LatexToken.Kind.COMMAND && VariableNames.isGreek(t.text())) {
                    label.append(t.text());
                } else {
                    throw new LatexReadException("subscript", "'" + t.text() + "' in a subscript", t.position());
                }
            }
            next();
        } else {
            LatexToken t = next();
            if (t.kind() == LatexToken.Kind.LETTER || t.kind() == LatexToken.Kind.NUMBER
                    || (t.kind() == LatexToken.Kind.COMMAND && VariableNames.isGreek(t.text()))) {
                label.append(t.text());
            } else {
                throw new LatexReadException("subscript", "'" + t.text() + "' after _", t.position());
            }
        }
        if (label.isEmpty()) {
            throw new LatexReadException("subscript", "empty subscript", position);
        }
        return label.toString();
    }

    private Term parseFraction(int position) {
        // derivative forms: numerator starts with d or \partial and denominator starts with d or \partial
        int save = index;
        expectPunct("{");
        boolean partial = peek().isCommand("partial");
        boolean ordinary = peek().kind() == LatexToken.Kind.LETTER && peek().text().equals("d");
        if (partial || ordinary) {
            Term derivative = tryParseDerivative(partial, position);
            if (derivative != null) {
                return derivative;
            }
        }
        index = save;
        Term numerator = parseGroup();
        Term denominator = parseGroup();
        return apply("arith1:divide", List.of(numerator, denominator));
    }

    /**
     * Called after "{" with the cursor on d or \partial. Reads {@code d^n expr}{d x^n} or
     * {@code \partial^n expr}{\partial x \partial y ...}; returns null when the shape does not match so the
     * caller falls back to an ordinary fraction.
     */
    private Term tryParseDerivative(boolean partial, int position) {
        next(); // d or \partial
        int order = 1;
        if (peek().isPunct("^")) {
            next();
            Term exponent = parseGroupOrSingle();
            if (!(exponent instanceof Term.IntegerTerm integer)) {
                return null;
            }
            order = integer.value().intValueExact();
        }
        Term numeratorBody = peek().isPunct("}") ? null : parseSum(false);
        expectPunct("}");
        expectPunct("{");
        List<String> variables = new ArrayList<>();
        List<Integer> degrees = new ArrayList<>();
        while (!peek().isPunct("}")) {
            LatexToken marker = next();
            boolean markerPartial = marker.isCommand("partial");
            boolean markerOrdinary = marker.kind() == LatexToken.Kind.LETTER && marker.text().equals("d");
            if (!(markerPartial || markerOrdinary)) {
                return null;
            }
            boolean dotted = peek().isCommand("dot") || peek().isCommand("ddot");
            Term variable = parseAtom(false);
            if (!(variable instanceof Term.VariableTerm variableTerm)) {
                if (dotted) {
                    throw new LatexReadException("dot-as-variable", "derivative with respect to a dotted variable", position);
                }
                return null;
            }
            int degree = 1;
            if (peek().isPunct("^")) {
                next();
                Term exponent = parseGroupOrSingle();
                if (!(exponent instanceof Term.IntegerTerm integer)) {
                    return null;
                }
                degree = integer.value().intValueExact();
            }
            variables.add(variableTerm.name());
            degrees.add(degree);
        }
        next(); // }
        if (variables.isEmpty()) {
            return null;
        }
        Term body = numeratorBody;
        if (body == null) {
            // \frac{d}{dt} expr : the operand follows the fraction
            body = parseFactor(false);
        }
        if (!partial) {
            if (variables.size() != 1 || degrees.get(0) != order) {
                throw new LatexReadException("syntax", "ordinary derivative with mismatched variables", position);
            }
            Term.VariableTerm variable = variable(variables.get(0));
            Term.BindingTerm function = lambda(List.of(variable), body);
            return order == 1 ? apply("calculus1:diff", List.of(function))
                    : apply("calculus1:nthdiff", List.of(number(Integer.toString(order)), function));
        }
        int total = 0;
        for (int degree : degrees) {
            total += degree;
        }
        if (total != order) {
            throw new LatexReadException("syntax", "partial derivative order " + order + " but " + total + " variables", position);
        }
        List<Term.VariableTerm> distinct = new ArrayList<>();
        List<Term> indices = new ArrayList<>();
        for (int i = 0; i < variables.size(); i++) {
            int found = -1;
            for (int j = 0; j < distinct.size(); j++) {
                if (distinct.get(j).name().equals(variables.get(i))) {
                    found = j;
                }
            }
            if (found < 0) {
                distinct.add(variable(variables.get(i)));
                found = distinct.size() - 1;
            }
            for (int k = 0; k < degrees.get(i); k++) {
                indices.add(number(Integer.toString(found + 1)));
            }
        }
        return apply("calculus1:partialdiff", List.of(apply("list1:list", indices), lambda(distinct, body)));
    }

    private Term parseDotted(String command, int position) {
        expectPunct("{");
        LatexToken inner = next();
        String name;
        if (inner.kind() == LatexToken.Kind.LETTER || (inner.kind() == LatexToken.Kind.COMMAND && VariableNames.isGreek(inner.text()))) {
            name = inner.text();
        } else {
            throw new LatexReadException("syntax", "\\" + command + " of a non-variable", position);
        }
        if (peek().isPunct("_")) {
            next();
            name = name + "_" + parseLabel(position);
        }
        expectPunct("}");
        if (peek().isPunct("_")) {
            next();
            name = name + "_" + parseLabel(position);
        }
        if (declaration.coordinates().contains(baseName(name))) {
            String base = baseName(name);
            String suffix = name.substring(base.length());
            String dotted = base + (command.equals("ddot") ? "ddot" : "dot") + suffix;
            return variable(dotted);
        }
        if (declaration.time().isEmpty()) {
            throw new LatexReadException("dot-without-time", "\\" + command + "{" + name + "}", position);
        }
        Term.VariableTerm time = variable(declaration.time().get());
        Term.BindingTerm function = lambda(List.of(time), apply(variable(name), List.of(time)));
        return command.equals("dot") ? apply("calculus1:diff", List.of(function))
                : apply("calculus1:nthdiff", List.of(number("2"), function));
    }

    private Term parseIntegral(int position) {
        Term lower = null;
        Term upper = null;
        while (peek().isPunct("_") || peek().isPunct("^")) {
            boolean isLower = next().isPunct("_");
            Term bound = parseGroupOrSingle();
            if (isLower) {
                lower = bound;
            } else {
                upper = bound;
            }
        }
        if (peek().isCommand("int")) {
            throw new LatexReadException("multiple-integral", "nested \\int", position);
        }
        Term body;
        String variableName;
        if (peek().isCommand("frac") && differentialNumeratorVariable() != null) {
            // \int \frac{dx}{expr}: the integrand is 1/expr and x is the variable
            next();
            expectPunct("{");
            next();
            variableName = next().text();
            if (peek().isPunct("_")) {
                next();
                variableName = variableName + "_" + parseLabel(position);
            }
            expectPunct("}");
            Term denominator = parseGroup();
            body = apply("arith1:divide", List.of(number("1"), denominator));
        } else {
            body = atDifferential() ? number("1") : parseSum(true);
            if (!atDifferential()) {
                throw new LatexReadException("syntax", "integral without d<variable>", position);
            }
            next(); // d
            variableName = next().text();
            if (peek().isPunct("_")) {
                next();
                variableName = variableName + "_" + parseLabel(position);
            }
        }
        Term.VariableTerm variable = variable(variableName);
        Term.BindingTerm function = lambda(List.of(variable), body);
        if (lower == null && upper == null) {
            return apply("calculus1:int", List.of(function));
        }
        if (lower == null || upper == null) {
            throw new LatexReadException("syntax", "integral with only one bound", position);
        }
        return apply("calculus1:defint", List.of(apply("interval1:interval", List.of(lower, upper)), function));
    }

    /** When the cursor is on \frac{d<var>}..., returns the variable token text; otherwise null. */
    private String differentialNumeratorVariable() {
        LatexToken brace = peekAt(1);
        LatexToken d = peekAt(2);
        LatexToken variable = peekAt(3);
        LatexToken close = peekAt(4);
        if (!brace.isPunct("{") || d.kind() != LatexToken.Kind.LETTER || !d.text().equals("d")) {
            return null;
        }
        boolean isVariable = variable.kind() == LatexToken.Kind.LETTER
                || (variable.kind() == LatexToken.Kind.COMMAND && VariableNames.isGreek(variable.text()));
        if (!isVariable || !(close.isPunct("}") || close.isPunct("_"))) {
            return null;
        }
        return variable.text();
    }

    private Term parseSum(int position) {
        if (!peek().isPunct("_")) {
            throw new LatexReadException("sum-without-bounds", "\\sum without subscript", position);
        }
        next();
        expectPunct("{");
        LatexToken variableToken = next();
        if (variableToken.kind() != LatexToken.Kind.LETTER && !(variableToken.kind() == LatexToken.Kind.COMMAND
                && VariableNames.isGreek(variableToken.text()))) {
            throw new LatexReadException("sum-without-bounds", "\\sum index is not a variable", position);
        }
        if (!peek().isPunct("=")) {
            throw new LatexReadException("sum-without-bounds", "\\sum_{" + variableToken.text() + "} without range", position);
        }
        next();
        Term lower = parseSum(false);
        expectPunct("}");
        if (!peek().isPunct("^")) {
            throw new LatexReadException("sum-without-bounds", "\\sum without upper bound", position);
        }
        next();
        Term upper = parseGroupOrSingle();
        Term body = parseProduct(false);
        Term.VariableTerm variable = variable(variableToken.text());
        return apply("arith1:sum", List.of(apply("interval1:integer_interval", List.of(lower, upper)), lambda(List.of(variable), body)));
    }

    private Term parseNabla() {
        if (peek().isCommand("cdot")) {
            next();
            return apply("veccalc1:divergence", List.of(parseFunctionArgument(false)));
        }
        if (peek().isCommand("times")) {
            next();
            return apply("veccalc1:curl", List.of(parseFunctionArgument(false)));
        }
        if (peek().isPunct("^")) {
            next();
            Term exponent = parseGroupOrSingle();
            if (!(exponent instanceof Term.IntegerTerm integer) || integer.value().intValueExact() != 2) {
                throw syntax("\\nabla with an exponent other than 2");
            }
            return apply("veccalc1:Laplacian", List.of(parseFunctionArgument(false)));
        }
        return apply("veccalc1:grad", List.of(parseFunctionArgument(false)));
    }

    private Term parseNamedOperator(String command, int position) {
        expectPunct("{");
        StringBuilder word = new StringBuilder();
        while (!peek().isPunct("}")) {
            LatexToken t = next();
            if (t.kind() == LatexToken.Kind.LETTER) {
                word.append(t.text());
            } else {
                throw new LatexReadException(command.equals("text") ? "text" : "syntax", "\\" + command + "{" + word + t.text() + "...}", position);
            }
        }
        next();
        String symbolName = switch (word.toString()) {
            case "div" -> "veccalc1:divergence";
            case "grad" -> "veccalc1:grad";
            case "curl", "rot" -> "veccalc1:curl";
            default -> null;
        };
        if (symbolName == null) {
            throw new LatexReadException("text", "\\" + command + "{" + word + "}", position);
        }
        return apply(symbolName, List.of(parseFunctionArgument(false)));
    }

    /** A trig/log function with optional power, then its argument. */
    private Term parseFunction(String name, boolean insideIntegrand) {
        Term exponent = null;
        if (peek().isPunct("^")) {
            next();
            exponent = parseGroupOrSingle();
        }
        String symbolName;
        if (exponent instanceof Term.IntegerTerm minusOne && minusOne.value().equals(BigInteger.ONE.negate())
                && Set.of("sin", "cos", "tan").contains(name)) {
            symbolName = "transc1:arc" + name;
            exponent = null;
        } else {
            symbolName = "transc1:" + name;
        }
        Term argument = parseFunctionArgument(insideIntegrand);
        Term result = apply(symbolName, List.of(argument));
        return exponent == null ? result : apply("arith1:power", List.of(result, exponent));
    }

    /**
     * The argument of a parenthesis-free function: a bracketed group; or a single {@code \frac}/{@code \sqrt};
     * or the product of the simple factors (numbers, letters, Greek letters with their labels and powers) that
     * follow, stopping at the next command, operator, bracket or differential.
     */
    private Term parseFunctionArgument(boolean insideIntegrand) {
        if (peek().isPunct("(")) {
            next();
            Term inner = parseSum(false);
            expectPunct(")");
            return inner;
        }
        if (peek().isPunct("{")) {
            next();
            Term inner = parseSum(false);
            expectPunct("}");
            return inner;
        }
        if (peek().isCommand("frac") || peek().isCommand("sqrt")) {
            return parseFactor(insideIntegrand);
        }
        List<Term> factors = new ArrayList<>();
        while (isSimpleFactorStart(peek()) && !(insideIntegrand && atDifferential())) {
            factors.add(parseFactor(insideIntegrand));
        }
        if (factors.isEmpty()) {
            throw syntax("function without argument");
        }
        return factors.size() == 1 ? factors.get(0) : apply("arith1:times", factors);
    }

    private boolean isSimpleFactorStart(LatexToken token) {
        return token.kind() == LatexToken.Kind.NUMBER || token.kind() == LatexToken.Kind.LETTER
                || (token.kind() == LatexToken.Kind.COMMAND
                    && (VariableNames.isGreek(token.text()) || token.text().equals("pi") || token.text().equals("infty")));
    }

    private Term parseGroup() {
        expectPunct("{");
        Term inner = parseSum(false);
        expectPunct("}");
        return inner;
    }

    /** {@code {...}} or a single token (digit run, letter, Greek command, or a signed single). */
    private Term parseGroupOrSingle() {
        if (peek().isPunct("{")) {
            return parseGroup();
        }
        if (peek().isPunct("-")) {
            next();
            return unaryMinus(parseSingleToken());
        }
        return parseSingleToken();
    }

    private Term parseSingleToken() {
        LatexToken token = next();
        switch (token.kind()) {
            case NUMBER -> {
                // LaTeX reads x^12 as x^{1}2; keep the first digit only
                String digits = token.text();
                if (digits.length() > 1 && !digits.contains(".")) {
                    index--;
                    tokens.set(index, new LatexToken(LatexToken.Kind.NUMBER, digits.substring(1), token.position() + 1));
                    return number(digits.substring(0, 1));
                }
                return number(digits);
            }
            case LETTER -> {
                return variable(token.text());
            }
            case COMMAND -> {
                if (VariableNames.isGreek(token.text())) {
                    return variable(token.text());
                }
                if (token.text().equals("infty")) {
                    return symbol("nums1:infinity");
                }
                if (token.text().equals("pi")) {
                    return symbol("nums1:pi");
                }
                throw syntax("unexpected \\" + token.text() + " as a single-token group");
            }
            default -> throw syntax("unexpected '" + token.text() + "' as a single-token group");
        }
    }

    // ---- term construction ----

    private static Term.SymbolTerm symbol(String qualifiedName) {
        int colon = qualifiedName.indexOf(':');
        return new Term.SymbolTerm(qualifiedName.substring(0, colon), qualifiedName.substring(colon + 1));
    }

    private static Term.VariableTerm variable(String name) {
        return new Term.VariableTerm(name);
    }

    private static Term number(String text) {
        if (text.contains(".")) {
            return new Term.DecimalTerm(text);
        }
        return new Term.IntegerTerm(new BigInteger(text));
    }

    private static Term apply(String qualifiedName, List<Term> args) {
        return new Term.ApplicationTerm(symbol(qualifiedName), args);
    }

    private static Term apply(Term.VariableTerm head, List<Term> args) {
        return new Term.ApplicationTerm(head, args);
    }

    private static Term.BindingTerm lambda(List<Term.VariableTerm> variables, Term body) {
        return new Term.BindingTerm(symbol("fns1:lambda"), variables, body);
    }

    private static Term unaryMinus(Term operand) {
        if (operand instanceof Term.IntegerTerm integer) {
            return new Term.IntegerTerm(integer.value().negate());
        }
        return apply("arith1:unary_minus", List.of(operand));
    }

    // ---- token cursor ----

    private LatexToken peek() {
        return tokens.get(Math.min(index, tokens.size() - 1));
    }

    private LatexToken peekAt(int offset) {
        return tokens.get(Math.min(index + offset, tokens.size() - 1));
    }

    private LatexToken next() {
        LatexToken token = peek();
        if (index < tokens.size() - 1) {
            index++;
        }
        return token;
    }

    private void expectPunct(String punct) {
        if (!peek().isPunct(punct)) {
            throw syntax("expected '" + punct + "', found '" + peek().text() + "'");
        }
        next();
    }

    private LatexReadException syntax(String detail) {
        return new LatexReadException("syntax", detail, peek().position());
    }
}
