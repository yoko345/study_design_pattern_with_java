#!/usr/bin/env python3
import argparse
import re

# ぀-ゟ: ひらがな / ァ-ヺ・ー: カタカナ（中黒「・」は除く）/ 一-鿿: 漢字
JP_CHAR = r"[぀-ゟァ-ヺー一-鿿]"
EXCLUDE_BEFORE = "。、・「（："
EXCLUDE_AFTER = "」）』（"
CODE_SPAN = re.compile(r"(`[^`\n]+`)")

JP_AT_END = re.compile(f"{JP_CHAR}$")
JP_AT_START = re.compile(f"^{JP_CHAR}")
EXCLUDED_THEN_SPACES_AT_END = re.compile(f"([{re.escape(EXCLUDE_BEFORE)}])\\s+$")
SPACES_THEN_EXCLUDED_AT_START = re.compile(f"^\\s+([{re.escape(EXCLUDE_AFTER)}])")


def process_line(line):
    # インラインコードを行頭から順に対応付けて切り出す。正規表現で行全体を置換すると、
    # あるインラインコードの閉じ記号と次のインラインコードの開き記号の間（` と ` など）を
    # インラインコードと誤認して、インラインコードの内側に空白を挿入してしまうため
    parts = CODE_SPAN.split(line)
    for i in range(1, len(parts), 2):
        parts[i - 1] = EXCLUDED_THEN_SPACES_AT_END.sub(r"\1", parts[i - 1])
        parts[i + 1] = SPACES_THEN_EXCLUDED_AT_START.sub(r"\1", parts[i + 1])
        if JP_AT_END.search(parts[i - 1]):
            parts[i - 1] += " "
        if JP_AT_START.search(parts[i + 1]):
            parts[i + 1] = " " + parts[i + 1]
    return "".join(parts)


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
