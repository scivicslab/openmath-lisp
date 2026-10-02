# openmath-lisp

Stores formulas as s-expressions whose heads are OpenMath Content Dictionary symbols, projects
them to LaTeX, Maxima and SMT-LIB with one projector driven by a symbol table, and checks
equations extracted from OCR'd books with Maxima (numeric substitution) and Z3 (polynomial identities).

```lisp
(relation1:eq
  (arith1:power (arith1:plus a b) 2)
  (arith1:plus (arith1:power a 2) (arith1:times a b) (arith1:power b 2)))
```

projects to

```
LaTeX   : {\left(a + b\right)}^{2} = {a}^{2} + a b + {b}^{2}
Maxima  : (a+b)^2 = a^2+a*b+b^2
SMT-LIB : (= (^ (+ a b) 2) (+ (^ a 2) (* a b) (^ b 2)))
```

and the checks report it as suspect with the difference `a*b`.

## Build

GraalVM CE 25 and Maven. Maxima and Z3 must be on the PATH for the `check` command
(`sudo apt install maxima z3` on Ubuntu).

```bash
rm -rf target && mvn install
```

`target/openmath-lisp-<version>-cli.jar` is the command line tool; `bin/openmath-lisp` runs it.

## Use

Formulas live in the markdown itself. A display formula is a fenced code block with the info
string `om`, an inline one is a code span starting with `om:`; both hold an s-expression. The
LaTeX a reader needs is generated at display time and never stored.

````markdown
```om id=SlaterVol1-p051-060-eq1 tag=17
(relation1:eq (arith1:times m (calculus1:nthdiff 2 (fns1:lambda (t) (x t)))) 0)
```
角振動数 `om:omega_n` について
````

```bash
bin/openmath-lisp convert path/to/X.md      # LaTeX -> om blocks and om spans, in place
bin/openmath-lisp suggest path/to/X.md      # propose the declaration the document is missing
bin/openmath-lisp check   path/to/X.md      # structural, Maxima and Z3 checks; writes X.lisp
bin/openmath-lisp report  path/to/*/*.md    # counts per status, unreadable reasons, suspects
bin/openmath-lisp render  path/to/X.md      # om -> LaTeX markdown, for KaTeX
bin/openmath-lisp project --target maxima path/to/X.md
```


`convert` turns an inline variable or formula into an om span. A chain of two or more relations
stays LaTeX, since `relation1:eq` takes two arguments and one om span holds one s-expression; so
does anything the reader cannot read. Either way it leaves a marker naming the reason, on the line
before a display formula and immediately before an inline one, and `report` counts them.
A declaration block right after the front matter fixes what the LaTeX text cannot: the time
variable for `\dot{}`, which letters are the imaginary unit and Euler's number, which variables
are vectors, which coordinates are independent when dotted, and which variables depend on which.
Write it and run `convert` again; it converts only what is still LaTeX.

````markdown
```om
(declare :time t :imaginary i :euler e :functions ((x t) (u r t)))
```
````

`check` writes one record per formula into `X.lisp` with the term, the LaTeX projected from it, the
four check results and a status: `:ok`, `:suspect`, `:not-checkable` or `:unparseable`. Everything in
it comes from the term, so `X.lisp` can be deleted and produced again.

When a formula is suspect, the fault is in one of four places, and each has a record:

| where | what holds it |
|---|---|
| the page | the PDF |
| the OCR reading of it | `X.ocr.lisp`, written by `convert` and kept in version control |
| the term the LaTeX reader built | the om block in `X.md` |
| the LaTeX projected back from the term | `X.lisp` |

`report` prints all three of the files' forms for each suspect formula. To correct a formula, edit
its om block in the markdown and run `check` again.

## Symbol table

`src/main/resources/openmath/symbols.lisp` holds the 113 symbols of 15 OpenMath dictionaries
(arith1, relation1, transc1, calculus1, fns1, veccalc1, nums1, minmax1, integer1, complex1,
limit1, linalg1, linalg2, interval1, list1). Role and arity come from the official `.ocd` and
`.sts` files; the three projection rules per symbol are written in
`bin/generate-symbol-table.py`, which regenerates the file. The unit tests verify the table
against the dictionary files.

## Design documents

The decisions behind the grammar, the LaTeX reader and the check records are in
`doc_Base010/docs/openmath-lisp/` (Japanese).

## License

Apache License 2.0.
