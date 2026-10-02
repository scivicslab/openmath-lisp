package com.scivicslab.openmathlisp.markdown;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the identifier prefix of a markdown file, used for the om blocks conversion creates. The prefix is
 * {@code <book>-<pages>}: the book is the directory above the page directory, and the pages come from the
 * file name ({@code ..._p051-060.md}).
 */
public final class DocumentIdentifier {

    private static final Pattern PAGES = Pattern.compile("_(p\\d+-\\d+)$");

    private DocumentIdentifier() {
    }

    /**
     * Derives the prefix.
     * @param markdown the file
     * @return e.g. {@code SlaterVol1-p051-060}
     */
    public static String prefixFor(Path markdown) {
        Path absolute = markdown.toAbsolutePath().normalize();
        String name = absolute.getFileName().toString();
        String stem = name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
        Path parent = absolute.getParent();
        String book;
        if (parent != null && parent.getFileName().toString().equals(stem) && parent.getParent() != null) {
            book = parent.getParent().getFileName().toString();
        } else if (parent != null) {
            book = parent.getFileName().toString();
        } else {
            book = "doc";
        }
        Matcher pages = PAGES.matcher(stem);
        String pagePart = pages.find() ? pages.group(1) : sanitize(stem);
        return sanitize(book) + "-" + pagePart;
    }

    /** Keeps letters and digits only; a page range is matched before sanitizing and keeps its hyphen. */
    private static String sanitize(String text) {
        return text.replaceAll("[^A-Za-z0-9]+", "");
    }
}
