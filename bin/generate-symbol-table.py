#!/usr/bin/env python3
"""Generate src/main/resources/openmath/symbols.lisp from the OpenMath .ocd/.sts files
and the writing rules declared in RULES below. Role and arity come from the dictionaries;
only the three writing rules are hand-written."""
import re, sys, os
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CD = os.path.join(ROOT, 'src/test/resources/openmath/cd')
STS = os.path.join(ROOT, 'src/test/resources/openmath/sts')
CDS = ['arith1','relation1','transc1','calculus1','fns1','veccalc1','nums1','minmax1',
       'integer1','complex1','limit1','linalg1','linalg2','interval1','list1']

def infix(s, p, a='left'): return f'(:infix "{s}" {p} :{a})'
def prefix(s, p): return f'(:prefix "{s}" {p})'
def func(s): return f'(:function "{s}")'
def tmpl(s, p=None, c=None):
    tail = '' if p is None else (f' {p}' if c is None else f' {p} {c}')
    return f'(:template "{s}"{tail})'
def special(s): return f'(:special "{s}")'
NIL = 'nil'

# cd:name -> (latex, maxima, smt). Missing entries get a default LaTeX rule and nil elsewhere.
RULES = {
 'arith1:lcm': (func('\\\\operatorname{lcm}'), func('lcm'), NIL),
 'arith1:gcd': (func('\\\\gcd'), func('gcd'), NIL),
 'arith1:plus': (infix(' + ', 20), infix('+', 20), func('+')),
 'arith1:unary_minus': (prefix('-', 40), prefix('-', 40), func('-')),
 'arith1:minus': (infix(' - ', 20), infix('-', 20), func('-')),
 'arith1:times': (infix(' ', 30), infix('*', 30), func('*')),
 'arith1:divide': (tmpl('\\\\frac{~1}{~2}'), infix('/', 30), func('/')),
 'arith1:power': (special('power'), infix('^', 50, 'right'), func('^')),
 'arith1:abs': (tmpl('\\\\left|~1\\\\right|'), func('abs'), NIL),
 'arith1:root': (special('root'), tmpl('(~1)^(1/(~2))'), NIL),
 'arith1:sum': (tmpl('\\\\sum_{~2.v1 = ~1.1}^{~1.2} ~2.b', 35, 15), tmpl('sum(~2.b, ~2.v1, ~1.1, ~1.2)'), NIL),
 'arith1:product': (tmpl('\\\\prod_{~2.v1 = ~1.1}^{~1.2} ~2.b', 35, 15), tmpl('product(~2.b, ~2.v1, ~1.1, ~1.2)'), NIL),
 'relation1:eq': (infix(' = ', 10), infix(' = ', 10), func('=')),
 'relation1:lt': (infix(' < ', 10), infix(' < ', 10), func('<')),
 'relation1:gt': (infix(' > ', 10), infix(' > ', 10), func('>')),
 'relation1:neq': (infix(' \\\\neq ', 10), infix(' # ', 10), func('distinct')),
 'relation1:leq': (infix(' \\\\leq ', 10), infix(' <= ', 10), func('<=')),
 'relation1:geq': (infix(' \\\\geq ', 10), infix(' >= ', 10), func('>=')),
 'relation1:approx': (infix(' \\\\approx ', 10), NIL, NIL),
 'transc1:log': (tmpl('\\\\log_{~1} ~2', 45), tmpl('(log(~2)/log(~1))'), NIL),
 'transc1:ln': (prefix('\\\\ln ', 45), func('log'), NIL),
 'transc1:exp': (tmpl('e^{~1}'), func('exp'), NIL),
 'calculus1:diff': (tmpl('\\\\frac{d}{d~1.v1} ~1.b', 45), tmpl('diff(~1.b, ~1.v1)'), NIL),
 'calculus1:nthdiff': (tmpl('\\\\frac{d^{~1}}{d~2.v1^{~1}} ~2.b', 45), tmpl('diff(~2.b, ~2.v1, ~1)'), NIL),
 'calculus1:partialdiff': (special('partialdiff'), special('partialdiff'), NIL),
 'calculus1:partialdiffdegree': (special('partialdiffdegree'), NIL, NIL),
 # integrals stay noun forms ('integrate) in Maxima: symbolic integration during the numeric check can hang
 'calculus1:int': (tmpl('\\\\int ~1.b \\\\, d~1.v1', 35, 15), tmpl("'integrate(~1.b, ~1.v1)"), NIL),
 'calculus1:defint': (tmpl('\\\\int_{~1.1}^{~1.2} ~2.b \\\\, d~2.v1', 35, 15), tmpl("'integrate(~2.b, ~2.v1, ~1.1, ~1.2)"), NIL),
 'fns1:lambda': (tmpl('\\\\left(~v \\\\mapsto ~b\\\\right)'), tmpl('lambda([~v], ~b)'), NIL),
 'fns1:identity': (tmpl('\\\\mathrm{id}'), NIL, NIL),
 'fns1:inverse': (tmpl('{~1}^{-1}', 50), NIL, NIL),
 'fns1:left_compose': (infix(' \\\\circ ', 35), NIL, NIL),
 'veccalc1:divergence': (prefix('\\\\operatorname{div} ', 45), NIL, NIL),
 'veccalc1:grad': (prefix('\\\\operatorname{grad} ', 45), NIL, NIL),
 'veccalc1:curl': (prefix('\\\\operatorname{curl} ', 45), NIL, NIL),
 'veccalc1:Laplacian': (prefix('\\\\nabla^{2} ', 45), NIL, NIL),
 'nums1:rational': (tmpl('\\\\frac{~1}{~2}'), tmpl('((~1)/(~2))'), func('/')),
 'nums1:infinity': (tmpl('\\\\infty'), tmpl('inf'), NIL),
 'nums1:e': (tmpl('e'), tmpl('%e'), NIL),
 'nums1:i': (tmpl('i'), tmpl('%i'), NIL),
 'nums1:pi': (tmpl('\\\\pi'), tmpl('%pi'), NIL),
 'nums1:gamma': (tmpl('\\\\gamma'), tmpl('%gamma'), NIL),
 'nums1:NaN': (tmpl('\\\\mathrm{NaN}'), NIL, NIL),
 'minmax1:min': (func('\\\\min'), func('min'), NIL),
 'minmax1:max': (func('\\\\max'), func('max'), NIL),
 'integer1:factorial': (tmpl('{~1}!', 55), tmpl('(~1)!'), NIL),
 'integer1:quotient': (tmpl('\\\\left\\\\lfloor \\\\frac{~1}{~2} \\\\right\\\\rfloor'), tmpl('quotient(~1, ~2)'), NIL),
 'integer1:remainder': (infix(' \\\\bmod ', 30), tmpl('mod(~1, ~2)'), NIL),
 'complex1:complex_cartesian': (tmpl('~1 + ~2 i', 20), tmpl('(~1 + ~2*%i)'), NIL),
 'complex1:real': (prefix('\\\\operatorname{Re} ', 45), func('realpart'), NIL),
 'complex1:imaginary': (prefix('\\\\operatorname{Im} ', 45), func('imagpart'), NIL),
 'complex1:complex_polar': (tmpl('~1 e^{i ~2}', 30), tmpl('(~1*exp(%i*~2))'), NIL),
 'complex1:argument': (prefix('\\\\arg ', 45), func('carg'), NIL),
 'complex1:conjugate': (tmpl('\\\\overline{~1}'), func('conjugate'), NIL),
 'limit1:limit': (tmpl('\\\\lim_{~3.v1 \\\\to ~1} ~3.b', 35, 15), tmpl('limit(~3.b, ~3.v1, ~1)'), NIL),
 'limit1:both_sides': (tmpl('\\\\mathrm{both\\\\_sides}'), NIL, NIL),
 'limit1:above': (tmpl('\\\\mathrm{above}'), NIL, NIL),
 'limit1:below': (tmpl('\\\\mathrm{below}'), NIL, NIL),
 'limit1:null': (tmpl('\\\\mathrm{null}'), NIL, NIL),
 'linalg1:vectorproduct': (infix(' \\\\times ', 30), NIL, NIL),
 'linalg1:scalarproduct': (infix(' \\\\cdot ', 30), infix(' . ', 30), NIL),
 'linalg1:outerproduct': (infix(' \\\\otimes ', 30), NIL, NIL),
 'linalg1:transpose': (tmpl('{~1}^{T}', 50), func('transpose'), NIL),
 'linalg1:determinant': (prefix('\\\\det ', 45), func('determinant'), NIL),
 'linalg1:vector_selector': (tmpl('{~2}_{~1}', 50), tmpl('~2[~1]'), NIL),
 'linalg1:matrix_selector': (tmpl('{~3}_{~1 ~2}', 50), tmpl('~3[~1, ~2]'), NIL),
 'linalg2:vector': (tmpl('\\\\begin{pmatrix} ~*{ \\\\\\\\ } \\\\end{pmatrix}'), tmpl('[~*]'), NIL),
 'linalg2:matrixrow': (tmpl('~*{ & }'), tmpl('[~*]'), NIL),
 'linalg2:matrix': (tmpl('\\\\begin{pmatrix} ~*{ \\\\\\\\ } \\\\end{pmatrix}'), tmpl('matrix(~*)'), NIL),
 'interval1:integer_interval': (tmpl('\\\\left[~1, ~2\\\\right]'), tmpl('[~1, ~2]'), NIL),
 'interval1:interval': (tmpl('\\\\left[~1, ~2\\\\right]'), tmpl('[~1, ~2]'), NIL),
 'interval1:oriented_interval': (tmpl('\\\\left[~1 \\\\to ~2\\\\right]'), tmpl('[~1, ~2]'), NIL),
 'interval1:interval_oo': (tmpl('\\\\left(~1, ~2\\\\right)'), tmpl('[~1, ~2]'), NIL),
 'interval1:interval_cc': (tmpl('\\\\left[~1, ~2\\\\right]'), tmpl('[~1, ~2]'), NIL),
 'interval1:interval_oc': (tmpl('\\\\left(~1, ~2\\\\right]'), tmpl('[~1, ~2]'), NIL),
 'interval1:interval_co': (tmpl('\\\\left[~1, ~2\\\\right)'), tmpl('[~1, ~2]'), NIL),
 'list1:list': (tmpl('\\\\left[~*\\\\right]'), tmpl('[~*]'), NIL),
}
TRIG = ['sin','cos','tan','sec','csc','cot','sinh','cosh','tanh']
for t in TRIG:
    RULES['transc1:'+t] = (prefix('\\\\'+t+' ', 45), func(t), NIL)
for t in ['sech','csch','coth']:
    RULES['transc1:'+t] = (prefix('\\\\operatorname{'+t+'} ', 45), func(t), NIL)
ARC = {'arcsin':'asin','arccos':'acos','arctan':'atan','arcsec':'asec','arccsc':'acsc','arccot':'acot',
       'arcsinh':'asinh','arccosh':'acosh','arctanh':'atanh','arcsech':'asech','arccsch':'acsch','arccoth':'acoth'}
for k,v in ARC.items():
    lx = '\\\\'+k if k in ('arcsin','arccos','arctan') else '\\\\operatorname{'+k+'}'
    RULES['transc1:'+k] = (prefix(lx+' ', 45), func(v), NIL)

# Symbols whose official .sts file has no signature; the argument count comes from the .ocd description.
ARITY_WITHOUT_SIGNATURE = {'calculus1:partialdiffdegree': 3, 'fns1:restriction': 2, 'fns1:image': 1, 'nums1:based_float': 2}

def arity_of(sig):
    sig = re.sub(r'xmlns="[^"]*"', '', sig)
    body = re.search(r'<OMOBJ[^>]*>\s*(.*?)\s*</OMOBJ>', sig, re.S).group(1).strip()
    if not body.startswith('<OMA>'):
        return 0  # constant
    # top-level children of the outer OMA
    inner = body[len('<OMA>'):body.rfind('</OMA>')]
    children, depth, cur = [], 0, ''
    for tok in re.findall(r'<[^>]+>', inner):
        cur += tok
        if tok.startswith('</'): depth -= 1
        elif not tok.endswith('/>'): depth += 1
        if depth == 0:
            children.append(cur); cur = ''
    # integer1:factorof's official signature writes its own name where sts:mapsto belongs; treat it the same
    args = children[1:-1]
    for a in args:
        if a.startswith('<OMA>'):
            head = a[5:a.index('>', 5) + 1]
            if 'name="nary"' in head or 'name="nassoc"' in head:
                return 'nary'
    return len(args)

out = ['; Symbol table of openmath-lisp. GENERATED by bin/generate-symbol-table.py; edit RULES there.',
       '; One list per symbol: role and arity come from the OpenMath .ocd/.sts files,',
       '; :latex/:maxima/:smt are writing rules (nil = the format cannot express the symbol).', '']
total = 0
for cd in CDS:
    ocd = open(os.path.join(CD, cd+'.ocd'), encoding='utf-8', errors='replace').read()
    sts = open(os.path.join(STS, cd+'.sts'), encoding='utf-8', errors='replace').read()
    out.append(f';;; {cd}')
    for d in re.findall(r'<CDDefinition>(.*?)</CDDefinition>', ocd, re.S):
        name = re.search(r'<Name>\s*(\S+?)\s*</Name>', d).group(1)
        key = f'{cd}:{name}'
        role = re.search(r'<Role>\s*(\S+?)\s*</Role>', d).group(1)
        sig = re.search(r'<Signature name="'+re.escape(name)+r'"\s*>(.*?)</Signature>', sts, re.S)
        # calculus1:partialdiffdegree has no .sts signature; its .ocd text says 3 arguments
        arity = arity_of(sig.group(1)) if sig else ARITY_WITHOUT_SIGNATURE[key]
        if role == 'constant': arity = 0
        key = f'{cd}:{name}'
        latex, maxima, smt = RULES.get(key, (tmpl('\\\\operatorname{'+name.replace('_','\\\\_')+'}\\\\left(~*\\\\right)'), NIL, NIL))
        out.append(f'({key}\n  :role {role} :arity {arity}\n  :latex  {latex}\n  :maxima {maxima}\n  :smt    {smt})')
        total += 1
    out.append('')
open(os.path.join(ROOT, 'src/main/resources/openmath/symbols.lisp'), 'w', encoding='utf-8').write('\n'.join(out))
print('symbols written:', total)
