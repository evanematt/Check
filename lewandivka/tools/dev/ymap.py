#!/usr/bin/env python3
"""Developer lookup tool for Yarn 1.20.1 mappings (clone https://github.com/FabricMC/yarn, branch 1.20.1).

    ymap.py class Entity              # classes whose simple name contains 'Entity'
    ymap.py members net.minecraft.entity.Entity [filter]   # fields and methods (named descriptors)
    ymap.py method getWorld           # every class declaring a method with this name
    ymap.py has net.minecraft.entity.Entity getWorld        # exit code 0/1

The mapping files contain names and descriptors but no inheritance and no generics, so inherited members are not listed.
Used while writing the game glue because the Minecraft jars cannot be downloaded in the authoring environment.
"""
import os
import re
import sys
from collections import defaultdict

YARN = os.environ.get("YARN_DIR", "/home/user/fabricmc/yarn/mappings")


def parse():
    inter_to_named = {}
    classes = {}        # named full name (dots, '$' for inner) -> {'fields': [(name, desc)], 'methods': [(name, desc)]}
    pending = []
    for root, _, files in os.walk(YARN):
        for fn in files:
            if not fn.endswith(".mapping"):
                continue
            stack = []   # (indent, inter, named)
            cur = None
            for line in open(os.path.join(root, fn), encoding="utf-8"):
                if not line.strip() or line.lstrip().startswith("COMMENT"):
                    continue
                indent = len(line) - len(line.lstrip("\t"))
                parts = line.strip().split(" ")
                kind = parts[0]
                if kind == "CLASS":
                    while stack and stack[-1][0] >= indent:
                        stack.pop()
                    inter = parts[1]
                    named = parts[2] if len(parts) > 2 else None
                    if stack:
                        pi, pn = stack[-1][1], stack[-1][2]
                        inter_full = pi + "$" + inter
                        named_full = pn + "$" + (named or inter)
                    else:
                        inter_full = inter
                        named_full = named or inter
                    stack.append((indent, inter_full, named_full))
                    inter_to_named[inter_full] = named_full
                    classes[named_full] = {"fields": [], "methods": [], "inter": inter_full}
                    cur = classes[named_full]
                elif kind in ("FIELD", "METHOD") and stack:
                    while stack and stack[-1][0] >= indent:
                        stack.pop()
                    owner = stack[-1][2] if stack else None
                    # members are indented one deeper than their class
                    owner = None
                    for ind, it, nm in reversed(stack):
                        if ind == indent - 1:
                            owner = nm
                            break
                    if owner is None:
                        continue
                    if kind == "FIELD":
                        if len(parts) >= 4:
                            classes[owner]["fields"].append((parts[2], parts[3]))
                    else:
                        # METHOD inter named desc   (named may be absent)
                        if len(parts) == 4:
                            classes[owner]["methods"].append((parts[2], parts[3]))
                        elif len(parts) == 3:
                            pass
    return inter_to_named, classes


def named_desc(desc: str, inter_to_named: dict) -> str:
    def repl(m):
        inter = m.group(1)
        return "L" + inter_to_named.get(inter, inter).replace("net/minecraft/", "") + ";"
    return re.sub(r"L([^;]+);", repl, desc)


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    i2n, classes = parse()
    cmd = argv[1]
    if cmd == "class":
        pat = argv[2].lower()
        for n in sorted(classes):
            simple = n.split("/")[-1].lower()
            if pat in simple:
                print(n.replace("/", "."))
    elif cmd == "members":
        n = argv[2].replace(".", "/")
        flt = argv[3].lower() if len(argv) > 3 else ""
        c = classes.get(n)
        if not c:
            print("no such class", n)
            return 1
        for name, d in sorted(c["fields"]):
            if flt in name.lower():
                print("  field ", name, named_desc(d, i2n))
        for name, d in sorted(c["methods"]):
            if flt in name.lower():
                print("  method", name, named_desc(d, i2n))
    elif cmd == "method":
        for n, c in sorted(classes.items()):
            for name, d in c["methods"]:
                if name == argv[2]:
                    print(n.replace("/", "."), name, named_desc(d, i2n))
    elif cmd == "has":
        n = argv[2].replace(".", "/")
        c = classes.get(n)
        ok = bool(c) and any(name == argv[3] for name, _ in c["methods"] + c["fields"])
        print("yes" if ok else "no")
        return 0 if ok else 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
