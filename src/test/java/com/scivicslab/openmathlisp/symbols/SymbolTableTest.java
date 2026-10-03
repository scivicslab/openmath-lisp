package com.scivicslab.openmathlisp.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The three build-time checks of TermGrammarAndSymbolTable_261002_oo01, decision 6: the table holds exactly the
 * symbols of the 15 dictionaries, every row's role and arity match the .ocd/.sts files, and every row has a
 * LaTeX rule and either a rule or nil for Maxima and SMT (the loader rejects anything else).
 */
@Tag("TermGrammarAndSymbolTable_261002_oo01")
class SymbolTableTest {

    private static final List<String> DICTIONARIES = List.of("arith1", "relation1", "transc1", "calculus1", "fns1",
            "veccalc1", "nums1", "minmax1", "integer1", "complex1", "limit1", "linalg1", "linalg2", "interval1", "list1");

    @Test
    void loadBundled_symbolsOfFifteenDictionaries_allPresentAndNothingElse() throws IOException {
        SymbolTable table = SymbolTable.loadBundled();
        Map<String, String> expectedRoles = new LinkedHashMap<>();
        for (String cd : DICTIONARIES) {
            Matcher m = Pattern.compile("<CDDefinition>(.*?)</CDDefinition>", Pattern.DOTALL).matcher(resource("openmath/cd/" + cd + ".ocd"));
            while (m.find()) {
                String name = find("<Name>\\s*(\\S+?)\\s*</Name>", m.group(1));
                expectedRoles.put(cd + ":" + name, find("<Role>\\s*(\\S+?)\\s*</Role>", m.group(1)));
            }
        }
        assertEquals(113, expectedRoles.size());
        List<String> missing = new ArrayList<>();
        for (String symbol : expectedRoles.keySet()) {
            if (!table.contains(symbol)) {
                missing.add(symbol);
            }
        }
        assertTrue(missing.isEmpty(), "missing from table: " + missing);
        List<String> extra = new ArrayList<>();
        for (SymbolEntry entry : table.entries()) {
            if (!expectedRoles.containsKey(entry.qualifiedName())) {
                extra.add(entry.qualifiedName());
            }
        }
        assertTrue(extra.isEmpty(), "not in the 15 dictionaries: " + extra);
        assertEquals(113, table.size());
    }

    @Test
    void loadBundled_roleAndArity_matchDictionaryAndSignatureFiles() throws IOException {
        SymbolTable table = SymbolTable.loadBundled();
        List<String> mismatches = new ArrayList<>();
        for (String cd : DICTIONARIES) {
            String ocd = resource("openmath/cd/" + cd + ".ocd");
            String sts = resource("openmath/sts/" + cd + ".sts");
            Matcher m = Pattern.compile("<CDDefinition>(.*?)</CDDefinition>", Pattern.DOTALL).matcher(ocd);
            while (m.find()) {
                String name = find("<Name>\\s*(\\S+?)\\s*</Name>", m.group(1));
                SymbolEntry entry = table.get(cd + ":" + name);
                Role role = Role.fromName(find("<Role>\\s*(\\S+?)\\s*</Role>", m.group(1)));
                if (entry.role() != role) {
                    mismatches.add(entry.qualifiedName() + " role " + entry.role() + " vs " + role);
                }
                if (role == Role.CONSTANT || !sts.contains("<Signature name=\"" + name + "\"")) {
                    continue; // constants take no argument; calculus1:partialdiffdegree has no signature in the official .sts
                }
                int arity = arityFromSignature(sts, name);
                if (entry.arity() != arity) {
                    mismatches.add(entry.qualifiedName() + " arity " + entry.arity() + " vs " + arity);
                }
            }
        }
        assertTrue(mismatches.isEmpty(), String.join("\n", mismatches));
    }

    @Test
    void loadBundled_everyRow_hasLatexRule() {
        for (SymbolEntry entry : SymbolTable.loadBundled().entries()) {
            assertTrue(entry.rule(InputFormat.LATEX).isPresent(), entry.qualifiedName());
        }
    }

    /** Top-level arguments of the sts:mapsto application; nary when one is wrapped in sts:nary or sts:nassoc. */
    static int arityFromSignature(String sts, String name) {
        Matcher m = Pattern.compile("<Signature name=\"" + Pattern.quote(name) + "\"\\s*>(.*?)</Signature>", Pattern.DOTALL).matcher(sts);
        assertTrue(m.find(), "no signature for " + name);
        String body = m.group(1).replaceAll("xmlns=\"[^\"]*\"", "");
        body = body.substring(body.indexOf("<OMOBJ"));
        body = body.substring(body.indexOf('>') + 1, body.lastIndexOf("</OMOBJ>")).trim();
        if (!body.startsWith("<OMA>")) {
            return 0;
        }
        String inner = body.substring(5, body.lastIndexOf("</OMA>"));
        List<String> children = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        Matcher tags = Pattern.compile("<[^>]+>").matcher(inner);
        while (tags.find()) {
            String tag = tags.group();
            current.append(tag);
            if (tag.startsWith("</")) {
                depth--;
            } else if (!tag.endsWith("/>")) {
                depth++;
            }
            if (depth == 0) {
                children.add(current.toString());
                current.setLength(0);
            }
        }
        List<String> args = children.subList(1, children.size() - 1);
        for (String arg : args) {
            if (arg.startsWith("<OMA>")) {
                String head = arg.substring(5, arg.indexOf('>', 5) + 1);
                if (head.contains("\"nary\"") || head.contains("\"nassoc\"")) {
                    return SymbolEntry.NARY;
                }
            }
        }
        return args.size();
    }

    private static String find(String regex, String text) {
        Matcher m = Pattern.compile(regex).matcher(text);
        assertTrue(m.find(), regex);
        return m.group(1);
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = SymbolTableTest.class.getClassLoader().getResourceAsStream(path)) {
            assertTrue(in != null, "missing test resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
