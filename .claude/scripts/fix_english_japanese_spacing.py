#!/usr/bin/env python3
import argparse
import re

ALNUM = r"[A-Za-z0-9]"
# ぁ-ゟ: ひらがな / ァ-ヺ・ー: カタカナ（中黒「・」は除く）/ 一-鿿: 漢字
JP_CHAR = r"[ぁ-ゟァ-ヺー一-鿿]"

HTML_TAG = re.compile(r"<[^>\n]+>")
CODE_SPAN = re.compile(r"`[^`\n]+`")
# 目次などの Markdown リンクのアンカー部分（例: (#深堀り1)）
MD_ANCHOR_LINK = re.compile(r"\(#[^\s()]+\)")

INSERT_JP_AFTER_ALNUM = re.compile(f"({ALNUM})({JP_CHAR})")
INSERT_ALNUM_AFTER_JP = re.compile(f"({JP_CHAR})({ALNUM})")


def mask(line, pattern, store):
    def repl(m):
        token = f"\x00{len(store)}\x00"
        store.append(m.group(0))
        return token

    return pattern.sub(repl, line)


def unmask(line, store):
    # 後からマスクしたものほど内側にネストし得ないよう、マスクした順の逆順で復元する。
    for i in reversed(range(len(store))):
        line = line.replace(f"\x00{i}\x00", store[i])
    return line


def process_line(line):
    store = []
    # インラインコード・HTML タグ・アンカーリンクは対象外とし、
    # 元の文字列に影響しないようプレースホルダに退避してから処理する。
    # コードスパンを先にマスクすることで、`List<TaskComponent>` のような
    # コード内のジェネリクス表記を HTML タグと誤認しないようにする。
    masked = mask(line, CODE_SPAN, store)
    masked = mask(masked, HTML_TAG, store)
    masked = mask(masked, MD_ANCHOR_LINK, store)
    masked = INSERT_JP_AFTER_ALNUM.sub(r"\1 \2", masked)
    masked = INSERT_ALNUM_AFTER_JP.sub(r"\1 \2", masked)
    return unmask(masked, store)


def main():
    parser = argparse.ArgumentParser(
        description="英数字と日本語の間のスペーシングを統一する"
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
