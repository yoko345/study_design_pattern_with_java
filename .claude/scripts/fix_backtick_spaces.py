#!/usr/bin/env python3
import argparse
import re

# ぀-ゟ: ひらがな / ァ-ヺ・ー: カタカナ（中黒「・」は除く）/ 一-鿿: 漢字
JP_CHAR = r"[぀-ゟァ-ヺー一-鿿]"
EXCLUDE_BEFORE = "。、・「（："
EXCLUDE_AFTER = "」）』（"
CODE_SPAN = r"`[^`\n]+`"

INSERT_BEFORE = re.compile(f"({JP_CHAR})({CODE_SPAN})")
INSERT_AFTER = re.compile(f"({CODE_SPAN})({JP_CHAR})")
REMOVE_BEFORE = re.compile(f"([{re.escape(EXCLUDE_BEFORE)}])\\s+({CODE_SPAN})")
REMOVE_AFTER = re.compile(f"({CODE_SPAN})\\s+([{re.escape(EXCLUDE_AFTER)}])")


def process_line(line):
    line = REMOVE_BEFORE.sub(r"\1\2", line)
    line = REMOVE_AFTER.sub(r"\1\2", line)
    line = INSERT_BEFORE.sub(r"\1 \2", line)
    line = INSERT_AFTER.sub(r"\1 \2", line)
    return line


def main():
    parser = argparse.ArgumentParser(
        description="インラインコードと日本語の間のスペーシングを統一する"
    )
    parser.add_argument("file")
    parser.add_argument("--end-line", type=int, default=None)
    args = parser.parse_args()

    with open(args.file, encoding="utf-8") as f:
        lines = f.readlines()

    in_code_block = False
    for i, line in enumerate(lines):
        if args.end_line is not None and i >= args.end_line:
            break
        if line.lstrip().startswith("```"):
            in_code_block = not in_code_block
            continue
        if in_code_block:
            continue
        lines[i] = process_line(line)

    with open(args.file, "w", encoding="utf-8") as f:
        f.writelines(lines)

    print("Done")


if __name__ == "__main__":
    main()
