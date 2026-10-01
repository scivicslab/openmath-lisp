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

```bash
bin/openmath-lisp read  path/to/X.md        # $$ blocks -> X.lisp beside the markdown
bin/openmath-lisp check path/to/X.lisp      # structural, Maxima and Z3 checks; rewrites X.lisp
bin/openmath-lisp report path/to/*.lisp     # counts per status and the suspect equations
bin/openmath-lisp project --target maxima path/to/X.lisp
```

The first line of `X.lisp` is a declaration that fixes what the LaTeX text cannot: the time
variable for `\dot{}`, which letters are the imaginary unit and Euler's number, which variables
are vectors, which coordinates are independent when dotted, and which variables depend on which.
Edit it and run `read` again.

```lisp
(declare :time t :imaginary i :euler e :functions ((x t) (u r t)))
```

Each equation becomes a record with the four check results and a status:
`:ok`, `:suspect`, `:not-checkable` or `:unparseable`. Mark a record `:edited t` after
correcting its term by hand; `read` keeps such terms.

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
