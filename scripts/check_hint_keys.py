#!/usr/bin/env python3
"""
check_hint_keys.py — regression net for the hint-explanation i18n keys.

The backend builds hint strings with ``LanguageKeyBuilder``:

    hintKey(fragment, step, field, "var" to value, ...)  ->  {{hints.<fragment>.step<step>.<field>|var=...}}
    commonKey(field, "var" to value, ...)               ->  {{hints.common.<field>|var=...}}

``HintStringInterpolation`` resolves those keys against the active locale, and
(any key the locale lacks) falls back to ``en.json``. So ``en.json`` is the
single authoritative source for hint keys. This script is the net that proves
every key the generators reference actually exists there, and that the
variables each generator passes match the ``{{var}}`` placeholders the
templates consume.

It reports four kinds of problem:

  ERROR (hard)   a definitely-broken reference — a concrete ``hints.*`` leaf key
                 that is absent from en.json, a dynamic step whose counter is
                 unbounded but only a few steps are defined, or a numeric
                 template whose value range is only partially defined (all
                 render literally as ``[key]``)
  NEEDS-REVIEW   a fragment/step that is dynamic, a complex string template, or a
                 variable whose full value set can't be confirmed — en.json is
                 shown with whatever it defines under the prefix so a human can
                 confirm coverage
  UNUSED-VAR     a variable a generator passes that the target template never
                 consumes (e.g. the ``isLong`` in the AIC long-chain step)
  UNSATISFIED    a ``{{var}}`` placeholder in a referenced template that no
                 branch passing to that key supplies

Exit status: by default 1 when there is any hard ERROR (the "net"); add
``--strict`` to also fail on the WARN-severity verify notes. Run from anywhere:

    python3 scripts/check_hint_keys.py
    python3 scripts/check_hint_keys.py --en <path/to/en.json>
    python3 scripts/check_hint_keys.py --strict

The net is *expected* to be red while known gaps exist (ALS per-step titles,
UR elimination templates, the AIC ``isLong`` var, ...). It goes green as the
later phases add the missing keys and fix the variable plumbing.
"""

import argparse
import json
import os
import re
import sys


# --------------------------------------------------------------------------- #
# Locating the repo / en.json
# --------------------------------------------------------------------------- #

def find_repo_root(start):
    d = os.path.abspath(start)
    while True:
        if os.path.exists(os.path.join(d, "settings.gradle.kts")):
            return d
        parent = os.path.dirname(d)
        if parent == d:
            return os.path.abspath(start)
        d = parent


EN_CANDIDATES = [
    "web/src/jsMain/resources/languages/en.json",
    "shared/src/commonMain/resources/languages/en.json",
    "backend/src/main/resources/languages/en.json",
]


def resolve_en_path(root, override):
    if override:
        return override
    for cand in EN_CANDIDATES:
        p = os.path.join(root, cand)
        if os.path.exists(p):
            return p
    return os.path.join(root, EN_CANDIDATES[0])


# --------------------------------------------------------------------------- #
# en.json: leaf keys + template placeholders
# --------------------------------------------------------------------------- #

def load_en(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def hints_leaves(en):
    """Return {leaf dot-path -> string value} for every leaf under ``hints``."""
    leaves = {}

    def walk(node, prefix):
        if isinstance(node, dict):
            for k, v in node.items():
                walk(v, f"{prefix}.{k}")
        else:
            leaves[prefix] = node if isinstance(node, str) else str(node)

    walk(en.get("hints", {}), "hints")
    return leaves


def placeholders_in(value):
    return set(re.findall(r"\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}", value or ""))


def keys_with_prefix(leaves, prefix):
    """All leaf keys whose path starts with ``prefix``.

    Matching is by simple string prefix (not dot-boundary) so that a fragment
    like ``hints.unique_rectangle_type`` also matches ``...type1`` / ``...type6``
    where a digit follows directly."""
    return sorted(k for k in leaves if k.startswith(prefix))


def family_root(prefix):
    """Coarse family root of a fragment prefix = its first two underscore-words.

    ``unique_rectangle_elim_type`` and ``unique_rectangle_type`` both share the
    root ``unique_rectangle``; the numeric suffixes used across that root tell us
    the expected value range of a ``$type``-style template."""
    words = prefix.split("_")
    return "_".join(words[:2]) if len(words) >= 2 else prefix


def numeric_range_for_family(leaves, root):
    """Set of trailing-numeric suffixes used by any fragment starting with ``root``.

    E.g. for root ``unique_rectangle`` this yields {1..6} from ``..._type1``..``type6``.
    Used to infer the expected value range of a ``$type``-style numeric template."""
    vals = set()
    for k in leaves:
        if not k.startswith(f"hints.{root}"):
            continue
        parts = k.split(".")
        if len(parts) < 2:
            continue
        m = re.search(r"(\d+)$", parts[1])
        if m:
            vals.add(int(m.group(1)))
    return vals


def split_template(tpl):
    """Split a Kotlin string template into ``(prefix, simple_var_or_None)``.

    ``simple_var_or_None`` is the variable name when the template is of the form
    ``<prefix>$<var>`` (a bare identifier). It is ``None`` for complex templates
    such as ``<prefix>${expr}`` (braces / method calls), where the value set is
    not statically inferable."""
    if "$" not in tpl:
        return tpl, None
    prefix, rest = tpl.split("$", 1)
    if rest.startswith("{"):
        return prefix, None
    m = re.match(r"([A-Za-z_][A-Za-z0-9_]*)", rest)
    if m:
        return prefix, m.group(1)
    return prefix, None


# --------------------------------------------------------------------------- #
# Kotlin source scanning helpers
# --------------------------------------------------------------------------- #

def _skip_string(text, i):
    """Given text[i] == '"', return index just past the closing quote."""
    n = len(text)
    j = i + 1
    while j < n:
        if text[j] == "\\" and j + 1 < n:
            j += 2
            continue
        if text[j] == '"':
            return j + 1
        j += 1
    return n


def matching_close(text, open_idx, open_ch="(", close_ch=")"):
    depth = 0
    i = open_idx
    n = len(text)
    while i < n:
        c = text[i]
        if c == '"':
            i = _skip_string(text, i)
            continue
        if c == open_ch:
            depth += 1
        elif c == close_ch:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def matching_brace(text, open_idx):
    return matching_close(text, open_idx, "{", "}")


def split_top_level(s):
    """Split a call's argument list on depth-0 commas, respecting strings/brackets."""
    parts, cur = [], []
    depth = 0
    in_str = False
    i = 0
    n = len(s)
    while i < n:
        c = s[i]
        if in_str:
            cur.append(c)
            if c == "\\" and i + 1 < n:
                i += 1
                cur.append(s[i])
            elif c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
                cur.append(c)
            elif c in "([{":
                depth += 1
                cur.append(c)
            elif c in ")]}":
                depth -= 1
                cur.append(c)
            elif c == "," and depth == 0:
                parts.append("".join(cur).strip())
                cur = []
            else:
                cur.append(c)
        i += 1
    if cur:
        parts.append("".join(cur).strip())
    return [p for p in parts if p != ""]


def extract_strings(text):
    """Return [(content, is_template)] for every double-quoted string in ``text``.

    A string is a template when it contains ``$`` (Kotlin string interpolation)."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        if text[i] == '"':
            end = _skip_string(text, i)
            content = text[i + 1:end - 1]
            out.append((content, "$" in content))
            i = end
        else:
            i += 1
    return out


def all_strings(expr):
    """Split a block of expression text into (concrete_literals, templates)."""
    strings = extract_strings(expr)
    lits = [s for s, t in strings if not t]
    tpls = [s for s, t in strings if t]
    return lits, tpls


def find_functions(source):
    """Return [(name, body_text)] for every top-level ``fun`` in the file."""
    funcs = []
    for m in re.finditer(r"\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*[(<]", source):
        name = m.group(1)
        body_open = source.find("{", m.end())
        if body_open == -1:
            continue
        close = matching_brace(source, body_open)
        if close == -1:
            continue
        funcs.append((name, source[body_open:close + 1]))
    return funcs


def find_val_rhs(ident, body):
    """Find ``val <ident> = <rhs>`` in ``body``; return the RHS text (balanced).

    Handles the three shapes that occur in the generators:
      * ``when { ... }`` / ``when (x) { ... }``  -> a brace block
      * ``if (c) A else B``                       -> an expression (no braces)
      * a plain literal / template                -> a single line
    """
    m = re.search(r"\bval\s+" + re.escape(ident) + r"\s*=\s*", body)
    if not m:
        return None
    rest = body[m.end():]
    stripped = rest.lstrip()

    if stripped.startswith("when"):
        brace_idx = rest.find("{")
        if brace_idx != -1:
            close = matching_brace(rest, brace_idx)
            if close != -1:
                return rest[: close + 1]
        eol = rest.find("\n")
        return rest if eol == -1 else rest[:eol]

    if stripped.startswith("if"):
        # Is this a *block* if (body starts with '{') or an *expression* if?
        if_pos = rest.find("if")
        paren_open = rest.find("(", if_pos)
        block = False
        if paren_open != -1:
            paren_close = matching_close(rest, paren_open)
            if paren_close != -1:
                after = rest[paren_close + 1:]
                j = 0
                while j < len(after) and after[j] in " \t\r\n":
                    j += 1
                block = j < len(after) and after[j] == "{"
        if block:
            brace_idx = rest.find("{")
            close = matching_brace(rest, brace_idx)
            if close != -1:
                return rest[: close + 1]
        # expression if: the whole thing is one line
        eol = rest.find("\n")
        return rest if eol == -1 else rest[:eol]

    # plain literal / template: capture to end of line
    eol = rest.find("\n")
    return rest if eol == -1 else rest[:eol]


def extract_value_strings(expr):
    """Extract the *result* strings from an if/when expression.

    Condition strings (e.g. the ``"X-Wing"`` in ``contains("X-Wing")``) are
    deliberately excluded so they aren't mistaken for key values."""
    expr = expr.strip()
    if expr.startswith("when"):
        # In a when block the result is whatever follows the '->' arrow.
        results = re.findall(r"->\s*\"([^\"]*)\"", expr)
        if results:
            return [(r, "$" in r) for r in results]
        return extract_strings(expr)
    if expr.startswith("if"):
        # Strip the ``if (cond)`` part so condition strings don't leak in.
        m = re.match(r"if\s*\(", expr)
        if m:
            paren_open = expr.find("(", m.start())
            paren_close = matching_close(expr, paren_open)
            if paren_close != -1:
                branches = expr[:paren_open] + expr[paren_close + 1:]
                return extract_strings(branches)
        return extract_strings(expr)
    return extract_strings(expr)


def resolve_fragment(arg, body):
    """Resolve a hintKey fragment argument.

    Returns (concrete_values: list[str], templates: list[str], resolved: bool).
    ``resolved`` is True when we extracted at least one literal/template; the
    caller treats concrete values as checkable and templates as NEEDS-REVIEW."""
    arg = arg.strip()

    # 1) a single string literal (possibly a template)
    if arg.startswith('"') and arg.endswith('"') and len(arg) >= 2:
        content = arg[1:-1]
        if "$" in content:
            return [], [content], True
        return [content], [], True

    # 2) an if/when expression — collect the result literal/template values
    if re.match(r"^(if|when)\b", arg):
        strings = extract_value_strings(arg)
        lits = [s for s, t in strings if not t]
        tpls = [s for s, t in strings if t]
        if lits or tpls:
            return lits, tpls, True
        return [], [], False

    # 3) a bare identifier — look up its ``val`` definition in this function
    if re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", arg):
        rhs = find_val_rhs(arg, body)
        if rhs is not None:
            strings = extract_value_strings(rhs) if rhs.lstrip().startswith(("if", "when")) else extract_strings(rhs)
            lits = [s for s, t in strings if not t]
            tpls = [s for s, t in strings if t]
            if lits or tpls:
                return lits, tpls, True
        return [], [], False

    # 4) anything else (a call, ...) — best-effort: any literals inside
    lits, tpls = all_strings(arg)
    return lits, tpls, bool(lits or tpls)


def find_key_calls(body):
    """Return [(kind, arglist_text)] for every hintKey/commonKey call in body."""
    calls = []
    for m in re.finditer(r"\b(hintKey|commonKey)\s*\(", body):
        kind = m.group(1)
        open_idx = m.end() - 1
        close = matching_close(body, open_idx)
        if close == -1:
            continue
        calls.append((kind, body[open_idx + 1:close]))
    return calls


def parse_var_name(arg):
    """Return the name of a ``"name" to value`` variable argument, or None."""
    strings = extract_strings(arg)
    if strings:
        return strings[0][0]
    return None


# --------------------------------------------------------------------------- #
# The main analysis
# --------------------------------------------------------------------------- #

class Issue:
    def __init__(self, severity, code, message):
        self.severity = severity  # "ERROR" | "WARN"
        self.code = code
        self.message = message


def analyse(root, en):
    leaves = hints_leaves(en)
    issues = []

    # key -> union of variable names passed to it (for the var cross-checks)
    passed_vars = {}

    kt_files = []
    backend_src = os.path.join(root, "backend", "src")
    for dirpath, _dirs, files in os.walk(backend_src):
        for f in files:
            if f.endswith(".kt"):
                kt_files.append(os.path.join(dirpath, f))

    # ---- literal {{hints...}} key strings anywhere in the backend ---------- #
    literal_keys = set()
    for path in kt_files:
        with open(path, "r", encoding="utf-8") as fh:
            src = fh.read()
        for m in re.finditer(r"\{\{\s*(hints\.[A-Za-z0-9_.]+)", src):
            literal_keys.add(m.group(1).strip())

    # ---- hintKey / commonKey call sites ------------------------------------ #
    for path in kt_files:
        rel = os.path.relpath(path, root)
        with open(path, "r", encoding="utf-8") as fh:
            src = fh.read()

        for func_name, body in find_functions(src):
            for kind, arglist in find_key_calls(body):
                args = split_top_level(arglist)

                if kind == "hintKey":
                    if len(args) < 3:
                        continue
                    frag_arg, step_arg, field_arg = args[0], args[1], args[2]
                    var_args = args[3:]
                    field = field_arg.strip().strip('"')

                    # --- fragment ---
                    lits, tpls, resolved = resolve_fragment(frag_arg, body)

                    # --- step ---
                    step = step_arg.strip()
                    step_is_literal = bool(re.fullmatch(r"\d+", step))

                    if resolved:
                        for frag in lits:
                            _check_key(frag, step, field, step_is_literal,
                                       frag_arg, var_args, leaves, issues,
                                       passed_vars, rel, func_name, body)
                        for tpl in tpls:
                            _review_template_fragment(tpl, step, field,
                                                     step_is_literal, frag_arg,
                                                     leaves, issues, rel, func_name)
                    else:
                        _review_unresolved(frag_arg, step, field, step_is_literal,
                                           leaves, issues, rel, func_name,
                                           frag_arg)

                elif kind == "commonKey":
                    if len(args) < 1:
                        continue
                    field = args[0].strip().strip('"')
                    var_args = args[1:]
                    key = f"hints.common.{field}"
                    if key in leaves:
                        for va in var_args:
                            name = parse_var_name(va)
                            if name:
                                passed_vars.setdefault(key, set()).add(name)
                    else:
                        issues.append(Issue(
                            "ERROR", "MISSING",
                            f"{rel} :: commonKey(\"{field}\") -> `{key}` is not in en.json"))

    # ---- literal {{hints...}} keys ---------------------------------------- #
    for key in sorted(literal_keys):
        if key not in leaves:
            issues.append(Issue("ERROR", "MISSING",
                                f"literal `{{{key}}}` referenced in backend but not in en.json"))

    # ---- variable cross-checks -------------------------------------------- #
    for key in sorted(passed_vars):
        if key not in leaves:
            continue  # already reported as MISSING
        supplied = passed_vars[key]
        used = placeholders_in(leaves[key])
        unused = supplied - used
        unsat = used - supplied
        if unused:
            issues.append(Issue("WARN", "UNUSED-VAR",
                                f"`{key}` — generator passes {sorted(unused)} "
                                f"but the template only uses {sorted(used) if used else 'nothing'}"))
        if unsat:
            issues.append(Issue("WARN", "UNSATISFIED",
                                f"`{key}` — template uses {sorted(unsat)} "
                                f"but no branch supplies it (supplied: {sorted(supplied) if supplied else 'nothing'})"))

    # De-duplicate identical messages (the same template/dynamic reference is
    # often hit by many call sites in one generator).
    out, seen = [], set()
    for i in issues:
        if i.message in seen:
            continue
        seen.add(i.message)
        out.append(i)
    return out


def _check_key(frag, step, field, step_is_literal, frag_arg, var_args,
               leaves, issues, passed_vars, rel, func_name, body):
    """Handle a concrete fragment (and a step)."""
    if step_is_literal:
        key = f"hints.{frag}.step{step}.{field}"
        if key in leaves:
            _record_vars(key, var_args, leaves, passed_vars)
        else:
            issues.append(Issue("ERROR", "MISSING",
                                f"{rel} :: {func_name} -> `{key}` is not in en.json"))
    else:
        # dynamic step: report what en.json defines for this family
        _review_dynamic_step(frag, field, frag_arg, step, body,
                            leaves, issues, rel, func_name)


def _record_vars(key, var_args, leaves, passed_vars):
    for va in var_args:
        name = parse_var_name(va)
        if name:
            passed_vars.setdefault(key, set()).add(name)


def _family_defined(leaves, frag, field):
    """List the step numbers en.json defines for ``hints.<frag>.step*.<field>``."""
    pat = re.compile(rf"^hints\.{re.escape(frag)}\.step(\d+)\.{re.escape(field)}$")
    steps = set()
    for k in leaves:
        m = pat.match(k)
        if m:
            steps.add(int(m.group(1)))
    return sorted(steps)


def _is_counter(ident, body):
    """True if ``ident`` is incremented anywhere in ``body`` (a loop counter)."""
    return bool(re.search(
        rf"\b{re.escape(ident)}\s*\+\+|\+\+\s*\b{re.escape(ident)}\b|"
        rf"\b{re.escape(ident)}\s*\+=\s*\d+", body))


def _review_dynamic_step(frag, field, frag_arg, step_expr, body,
                        leaves, issues, rel, func_name):
    """A step whose number is a runtime expression.

    If the expression references a loop counter, the step number is unbounded, so
    any family that only defines a handful of steps will miss the higher ones ->
    a definite broken render (ERROR). Otherwise it is a verify note (WARN)."""
    defined = _family_defined(leaves, frag, field)
    expr = step_expr.strip()
    idents = [i for i in re.findall(r"[A-Za-z_][A-Za-z0-9_]*", expr)
              if i not in ("true", "false", "null", "else", "if", "when", "to")]
    counters = [i for i in idents if _is_counter(i, body)]

    if counters and (not defined or max(defined) < 4):
        issues.append(Issue(
            "ERROR", "MISSING",
            f"{rel} :: {func_name} -> `hints.{frag}.step<({expr})>.{field}` uses "
            f"counter(s) {counters}, so the step number is unbounded, but en.json "
            f"only defines {field} for steps {defined}; steps beyond that render as [...]"))
    else:
        issues.append(Issue(
            "WARN", "NEEDS-REVIEW",
            f"{rel} :: {func_name} -> dynamic step `hints.{frag}.step<({expr})>.{field}`; "
            f"en.json defines {field} for steps {defined}. If the runtime step number "
            f"exceeds {defined[-1] if defined else 'any'}, the key is missing."))


def _review_template_fragment(tpl, step, field, step_is_literal, frag_arg,
                             leaves, issues, rel, func_name):
    """Handle a string-template fragment (e.g. `unique_rectangle_elim_type$type`).

    For a bare ``$var`` suffix we infer the expected value range from the numeric
    suffixes the surrounding family already uses, and flag any value in that range
    that the template's prefix does not define. Complex templates (``${expr}``) get
    a verify note instead."""
    prefix, var = split_template(tpl)
    if not prefix:
        prefix = tpl
    root = family_root(prefix)

    if var is not None:
        expected = numeric_range_for_family(leaves, root)
        defined = set()
        for k in leaves:
            if k.startswith(f"hints.{prefix}"):
                parts = k.split(".")
                if len(parts) > 1:
                    m = re.search(r"(\d+)$", parts[1])
                    if m:
                        defined.add(int(m.group(1)))
        if expected:
            missing = expected - defined
            if missing:
                issues.append(Issue(
                    "ERROR", "MISSING",
                    f"{rel} :: {func_name} -> template `{frag_arg.strip()}` = "
                    f"`{tpl}`; family root `{root}` uses values {sorted(expected)} but "
                    f"en.json only defines `{tpl}` for {sorted(defined)}; "
                    f"missing {sorted(missing)} render as [...]"))
            return  # fully covered -> nothing to report

    # Complex template, or no numeric range inferable: prefix-match check.
    matching = keys_with_prefix(leaves, f"hints.{root}")
    if not matching:
        issues.append(Issue("ERROR", "MISSING",
                            f"{rel} :: {func_name} -> template `{frag_arg.strip()}` "
                            f"resolves to `hints.{tpl}...`; en.json has NO keys under "
                            f"`hints.{root}*`"))
    else:
        issues.append(Issue("WARN", "NEEDS-REVIEW",
                            f"{rel} :: {func_name} -> template `{frag_arg.strip()}` = `{tpl}`; "
                            f"en.json defines {len(matching)} keys under `hints.{root}*`. "
                            f"Verify every runtime value is covered."))


def _review_unresolved(frag_arg, step, field, step_is_literal, leaves, issues,
                      rel, func_name, _raw):
    issues.append(Issue("WARN", "NEEDS-REVIEW",
                        f"{rel} :: {func_name} -> could not statically resolve fragment "
                        f"`{frag_arg.strip()}`; verify the resulting `hints.<fragment>.step"
                        f"{step if step_is_literal else '<...>'}.{field}` keys exist in en.json"))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--en", help="path to en.json (default: auto-detected)")
    ap.add_argument("--strict", action="store_true",
                    help="also fail (exit 1) on WARN-severity issues (verify notes, "
                         "unused vars, unsatisfied placeholders); default fails only on "
                         "hard ERRORs (definitely-broken keys)")
    args = ap.parse_args()

    root = find_repo_root(os.path.dirname(os.path.abspath(__file__)))
    en_path = resolve_en_path(root, args.en)
    en = load_en(en_path)
    leaves = hints_leaves(en)

    print(f"en.json : {os.path.relpath(en_path, root)}")
    print(f"leaf keys under hints.*: {len(leaves)}\n")

    issues = analyse(root, en)

    errors = [i for i in issues if i.severity == "ERROR"]
    warns = [i for i in issues if i.severity == "WARN"]

    def print_group(title, group):
        print(f"== {title} ({len(group)}) ==")
        for i in group:
            print(f"  - {i.message}")
        print()

    print_group("MISSING / definitely broken", [i for i in errors])
    print_group("NEEDS-REVIEW / verify coverage", [i for i in warns if i.code == "NEEDS-REVIEW"])
    print_group("UNUSED-VAR / template doesn't consume", [i for i in warns if i.code == "UNUSED-VAR"])
    print_group("UNSATISFIED / placeholder no branch supplies", [i for i in warns if i.code == "UNSATISFIED"])

    print(f"Summary: {len(errors)} error(s), {len(warns)} warning(s)")

    if args.strict:
        fail = len(issues) > 0
    else:
        fail = len(errors) > 0

    print()
    print("RESULT: " + ("RED — problems found" if fail else "GREEN — all hint keys resolve"))
    sys.exit(1 if fail else 0)


if __name__ == "__main__":
    main()
