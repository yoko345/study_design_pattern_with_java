# Interpreter（インタプリタ）パターン ― 文法の規則をクラスに分けて、式を解釈する

次のような経験をしたことはありませんか？

> 利用者が文字列で書いた決まりごとを、記号ごとに文字列を分割して順番に処理していたら、記号の種類が増えるたびに分割の処理を書き足す羽目になった。おまけに、括弧で囲んだ部分を先に処理する決まりごとが増えると、分割の処理では対応しきれなくなった。

この記事では、見積の計算式を営業企画の担当者が文字列で設定できるようにするシナリオを通して、Interpreter（インタプリタ）パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Composite パターンとの違い](#深堀り1)
- [【深堀り②】文字列の式から木を組み立てる（構文解析）](#深堀り2)
- [【深堀り③】規則が増えたときのデメリット](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは見積システムの開発チームに所属しています。<br>
> 見積システムでは、見積の金額を `基本料金 + 追加料金 * 利用日数` という式で計算しています。この式は見積を計算するコードに直接書かれているため、式を変えるにはコードを修正する必要があります。<br>
> しかし、商品によって料金の決め方が違うことが分かってきました。営業企画の担当者から、商品ごとに式を変えたいという要望が来ています。式を変えるたびに開発者がコードを修正してリリースするのは手間がかかるため、担当者が商品の設定画面で式を文字列として入力できるようにすることになりました。<br>
> 式の中では、`基本料金`・`追加料金`・`利用日数` のように、見積ごとに入力される値の名前を使います。また、担当者からは、`(基本料金 + 追加料金) * 利用日数` のように、括弧で囲んだ部分を先に計算する式も入力できるようにしてほしいと言われています。<br>

※正しい実装のコードでは、Interpreter パターンの解説に集中するため、文字列の式から木を組み立てる処理（構文解析）を、木を直接組み立てることで代用します。（→ [【深堀り②】文字列の式から木を組み立てる（構文解析）](#深堀り2)）

### 既存コードの仕様

※実務では、次の `Estimate` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `Estimate`

見積 1 件を表すクラスです。<br>
見積の基本料金・追加料金・利用日数を保持し、それらから金額を計算します。

| フィールド   | 型    | 説明     |
| ------------ | ----- | -------- |
| `basePrice`  | `int` | 基本料金 |
| `extraPrice` | `int` | 追加料金 |
| `days`       | `int` | 利用日数 |

| メソッド          | 引数 | 戻り値の型 | 説明                                                         |
| ----------------- | ---- | ---------- | ------------------------------------------------------------ |
| `calculateAmount` | なし | `int`      | 基本料金と、追加料金に利用日数を掛けた金額の合計を返す       |

**`Estimate.java`**

```java
package example;

public class Estimate {
    private int basePrice;
    private int extraPrice;
    private int days;

    public Estimate(int basePrice, int extraPrice, int days) {
        this.basePrice = basePrice;
        this.extraPrice = extraPrice;
        this.days = days;
    }

    public int calculateAmount() {
        return basePrice + extraPrice * days;
    }
}
```

<br>

- `Main`（実行クラス）

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        Estimate estimate = new Estimate(3000, 500, 4);

        System.out.println("見積金額：" + estimate.calculateAmount());
    }
}
```

**実行結果**

```
見積金額：5000
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、式の文字列を `+` と `*` の記号で分割して計算する `FormulaCalculator` クラスを作り、`Estimate` クラスからそれを呼び出す、という実装ではないでしょうか？

**`FormulaCalculator.java`**

```java
package example;

public class FormulaCalculator {
    public int calculate(String formula, Map<String, Integer> values) {
        int total = 0;

        for (String term: formula.split("\\+")) {
            int product = 1;

            for (String factor: term.split("\\*")) {
                product *= values.get(factor.trim());
            }

            total += product;
        }

        return total;
    }
}
```

**`Estimate.java`**

```java
package example;

public class Estimate {
    /* ここを追加（ここから） */
    private String formula;
    /* ここを追加（ここまで） */
    private int basePrice;
    private int extraPrice;
    private int days;

    public Estimate(String formula, int basePrice, int extraPrice, int days) { // ←ここを修正
        this.formula = formula; // ←ここを追加
        this.basePrice = basePrice;
        this.extraPrice = extraPrice;
        this.days = days;
    }

    public int calculateAmount() {
        /* ここを修正（ここから） */
        Map<String, Integer> values = new HashMap<>();
        values.put("基本料金", basePrice);
        values.put("追加料金", extraPrice);
        values.put("利用日数", days);

        return new FormulaCalculator().calculate(formula, values);
        /* ここを修正（ここまで） */
    }
}
```

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        Estimate estimate = new Estimate("基本料金 + 追加料金 * 利用日数", 3000, 500, 4); // ←ここを修正

        System.out.println("見積金額：" + estimate.calculateAmount());
    }
}
```

**実行結果**

```
見積金額：5000
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 優先順位が分割の順番として `calculate` メソッドの中に隠れている。そのため、記号を 1 つ追加するたびに（例えば、料金から割引額を差し引く減算（`-`）を式で使えるようにする場合）、既存の分割処理と順番を考えながら、分割の処理を書き足す必要がある。
    - その結果、分割の順番を 1 つ間違えると、正しい式でも計算結果がずれるリスクがある。例えば、`+` と `*` の分割の順番を入れ替えると、`基本料金 + 追加料金 * 利用日数` が「基本料金 × 追加料金 + 利用日数」として計算され、正しい金額にならない。
- 分割の処理では、括弧で囲まれた部分を取り出せない。そのため、括弧を扱うには、括弧の対応を探す処理を書き足す必要がある。
    - 現状の `calculate` メソッドでは、括弧を含む式を渡すと、`(基本料金` という名前の値を探して NullPointerException が発生する。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

この問題を解決するのが **Interpreter パターン**です。<br>
Interpreter パターンは、文法の 1 つ 1 つの規則を 1 つのクラスとして表し、それらを組み合わせた木の形に従って式を解釈するパターンです。本記事では、変数・足し算・掛け算の規則を、それぞれ 1 つのクラスとして表します。

まず、規則に共通のインターフェースから見ていきましょう。

**`Formula.java`**

```java
package example;

public interface Formula {
    int calculate(Map<String, Integer> values);
}
```

`Formula` は新たに追加したインターフェースで、式の計算結果を返す抽象メソッド `calculate` を持ちます。引数の `values` には、変数の名前（「基本料金」など）と値の対応を受け取ります。<br>
なお、GoF の Interpreter パターンでは、この抽象メソッドは `interpret` と呼ばれています。本記事では、計算結果を返すメソッドであることを名前で示すため、`calculate` と名付けています。

次に、このインターフェースを実装したクラスを見ていきましょう。

**`VariableFormula.java`**

```java
package example;

public class VariableFormula implements Formula {
    private String name;

    public VariableFormula(String name) {
        this.name = name;
    }

    @Override
    public int calculate(Map<String, Integer> values) {
        return values.get(name);
    }
}
```

**`AddFormula.java`**

```java
package example;

public class AddFormula implements Formula {
    private Formula left;
    private Formula right;

    public AddFormula(Formula left, Formula right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public int calculate(Map<String, Integer> values) {
        return left.calculate(values) + right.calculate(values);
    }
}
```

**`MultiplyFormula.java`**

```java
package example;

public class MultiplyFormula implements Formula {
    private Formula left;
    private Formula right;

    public MultiplyFormula(Formula left, Formula right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public int calculate(Map<String, Integer> values) {
        return left.calculate(values) * right.calculate(values);
    }
}
```

`VariableFormula`・`AddFormula`・`MultiplyFormula` は新たに追加したクラスで、それぞれ変数・足し算・掛け算の規則を表します。<br>
`VariableFormula` クラスは、変数の名前を `name` フィールドに保持しています。`calculate` メソッドにより、`values` から `name` に対応する値を取り出して返しています。<br>
`AddFormula` クラスは、足し算の左右の式を `left`・`right` フィールドに保持しています。`calculate` メソッドにより、左右それぞれの式の `calculate` メソッドを呼び出し、その結果を足して返しています。<br>
`MultiplyFormula` クラスは、掛け算の左右の式を保持しており、`AddFormula` クラスと同じく左右それぞれの `calculate` メソッドの結果を掛けて返しています。左右の式は `Formula` 型で受け取るため、足し算の中に掛け算がある、といった入れ子の形を組み立てられます（→ [【深堀り①】Composite パターンとの違い](#深堀り1)）。

次に、式を使う側のクラスを見ていきましょう。

**`Estimate.java`**

```java
package example;

public class Estimate {
    private Formula formula; // ←ここを追加
    private int basePrice;
    private int extraPrice;
    private int days;

    public Estimate(Formula formula, int basePrice, int extraPrice, int days) { // ←ここを修正
        this.formula = formula; // ←ここを追加
        this.basePrice = basePrice;
        this.extraPrice = extraPrice;
        this.days = days;
    }

    public int calculateAmount() {
        /* ここを修正（ここから） */
        Map<String, Integer> values = new HashMap<>();
        values.put("基本料金", basePrice);
        values.put("追加料金", extraPrice);
        values.put("利用日数", days);

        return formula.calculate(values);
        /* ここを修正（ここまで） */
    }
}
```

`Estimate` クラスを振り返ると、コンストラクターの引数に `Formula` 型の `formula` を追加し、組み立てた木をそのまま受け取れるようにしています。`calculateAmount` メソッドでは、変数の名前と値の対応を `values` に用意してから、`formula` フィールドの `calculate` メソッドに計算を任せ、その結果を金額として返しています。同じ `Formula` のオブジェクトは、`values` を変えれば、別の `Estimate` クラスの計算にも使い回せます。

最後に、実行クラスを見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        /* ここを修正（ここから） */
        Formula formula = new AddFormula(
                new VariableFormula("基本料金"),
                new MultiplyFormula(new VariableFormula("追加料金"), new VariableFormula("利用日数")));
        Estimate estimate = new Estimate(formula, 3000, 500, 4);
        /* ここを修正（ここまで） */

        System.out.println("見積金額：" + estimate.calculateAmount());

        /* ここを追加（ここから） */
        Formula bracketFormula = new MultiplyFormula(
                new AddFormula(new VariableFormula("基本料金"), new VariableFormula("追加料金")),
                new VariableFormula("利用日数"));
        Estimate bracketEstimate = new Estimate(bracketFormula, 3000, 500, 4);

        System.out.println("見積金額（括弧あり）：" + bracketEstimate.calculateAmount());
        /* ここを追加（ここまで） */
    }
}
```

**実行結果**

```
見積金額：5000
見積金額（括弧あり）：14000
```

`Main` クラスを振り返ると、式を `new` で組み立てて木の形にし、その木を `Estimate` クラスのコンストラクターに渡しています。1 つ目の式は、次の木の形になります。

```
AddFormula
├─ VariableFormula（基本料金）
└─ MultiplyFormula
    ├─ VariableFormula（追加料金）
    └─ VariableFormula（利用日数）
```

掛け算の `MultiplyFormula` クラスは足し算の `AddFormula` クラスの子になっているため、掛け算の結果が先に求められ、その結果を足し算の `AddFormula` クラスが足します。2 つ目の式は、括弧で囲んだ足し算の `AddFormula` クラスを掛け算の `MultiplyFormula` クラスの左側に置いた木です。

以上のような実装を行うと、以下のメリットがあります。

- 式の規則ごとに `Formula` インターフェースを実装したクラスに分かれ、各クラスが自分の規則の計算だけを行っている。そのため、記号を 1 つ追加する場合（例えば減算）でも、`Formula` インターフェースを実装したクラスを 1 つ追加すればよく、既存の `AddFormula` クラスや `MultiplyFormula` クラスには手を加える必要がない。
    - その結果、式の優先順位は木の形として表されるため、分割の順番を組み直す処理がなく、順番を間違えて例外が発生したり計算結果がずれたりすることはない。
- 式の左右には `Formula` 型のオブジェクトを入れられるため、括弧で囲んだ式も `AddFormula` クラスや `MultiplyFormula` クラスを入れ子にして組み立てられ、括弧の対応を探す処理を書く必要がない。
    - その結果、`(基本料金 + 追加料金) * 利用日数` も、足し算の `AddFormula` クラスを掛け算の `MultiplyFormula` クラスの左側に置くだけで計算でき、NullPointerException は発生しない。

なお、規則の数だけクラスが増えるため、その注意点は（→ [【深堀り③】規則が増えたときのデメリット](#深堀り3)）で補足します。

## まとめ

正しい実装を振り返ると、式の規則である変数・足し算・掛け算が、それぞれ `Formula` インターフェースを実装した 1 つのクラスになっています。そのため、`Estimate` クラスは、式を `Formula` 型で受け取り、その `calculate` メソッドに計算を任せています。<br>
このように Interpreter パターンは、文法の規則ごとにクラスを作り、それらを組み合わせた木の形に従って式を解釈する設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Composite パターンとの違い

正しい実装の `AddFormula` クラスと `MultiplyFormula` クラスを振り返ると、どちらも `Formula` インターフェースを実装し、`left`・`right` フィールドに別の `Formula` を保持して、その `calculate` メソッドに計算を任せていました。<br>
この「共通のインターフェースを実装したオブジェクトを組み合わせて、全体を木の形で扱う」構造は、Composite パターンとよく似ています。

ただし、2 つのパターンでは、木の各クラスが同じ処理を行うか、規則ごとに違う処理を行うかが異なります。<br>
Composite パターンでは、個々の要素もまとめたものも同じ処理（金額を返すなど）を行い、まとめたものは子の結果を集めて返します。<br>
Interpreter パターンでは、木の各クラスが規則ごとに違う処理を行います。本記事では、`VariableFormula` クラスは値を取り出し、`AddFormula` クラスは左右の計算結果を足し、`MultiplyFormula` クラスは左右の計算結果を掛けて返します。

このように 2 つのパターンは、同じ木の形をしていても、木の各クラスが同じ処理を行うか、規則ごとに違う処理を行うかが異なります。<br>
どちらのパターンを使うかは、木の各クラスに同じ処理を行わせたいか、規則ごとに違う処理を行わせたいかで判断するとよいでしょう。本記事の式の計算では、変数・足し算・掛け算で処理が異なるため、Interpreter パターンが向いています。反対に、個々の要素とまとめたものを同じ処理で扱いたい場合は、Composite パターンを検討するのが適切です。<br>
詳しくは「Composite パターン」で検索してみてください。

<a id="深堀り2"></a>

## 【深堀り②】文字列の式から木を組み立てる（構文解析）

正しい実装の `Main` クラスを振り返ると、`AddFormula` クラスや `MultiplyFormula` クラスのオブジェクトを `new` で組み立てて木を作っていました。しかし、営業企画の担当者が入力するのは、`基本料金 + 追加料金 * 利用日数` のような文字列です。この文字列から木を組み立てる処理を、構文解析（パース）と呼びます。<br>
Interpreter パターンで扱うのは、木ができた後に式を計算する部分です。GoF の Interpreter パターンの説明でも木を組み立てる処理は扱われていないため、本記事では木を直接組み立てることで代用しています。

構文解析では、式の決まりを次のような規則として書きます。

```text
式 ::= 項 ( "+" 項 )*
項 ::= 因子 ( "*" 因子 )*
因子 ::= 変数 | "(" 式 ")"
```

この規則ごとにメソッドを用意すると、文字列から木を組み立てられます。

**`FormulaParser.java`**

```java
package example;

public class FormulaParser {
    private Deque<String> tokens;

    public FormulaParser(String formula) {
        tokens = new ArrayDeque<>(Arrays.asList(formula.split(" ")));
    }

    public Formula parseExpression() {
        Formula left = parseTerm();
        while (!tokens.isEmpty() && tokens.peek().equals("+")) {
            tokens.poll();
            left = new AddFormula(left, parseTerm());
        }
        return left;
    }

    private Formula parseTerm() {
        Formula left = parseFactor();
        while (!tokens.isEmpty() && tokens.peek().equals("*")) {
            tokens.poll();
            left = new MultiplyFormula(left, parseFactor());
        }
        return left;
    }

    private Formula parseFactor() {
        String token = tokens.poll();
        if (token.equals("(")) {
            Formula inner = parseExpression();
            tokens.poll();
            return inner;
        }
        return new VariableFormula(token);
    }
}
```

`FormulaParser` は新たに追加したクラスで、式を空白で区切ったトークンを先頭から読み取りながら、木を組み立てます。トークンへの分け方は、空白区切りに簡略化しています。`parseExpression` メソッドは「式」の規則に、`parseTerm` メソッドは「項」の規則に、`parseFactor` メソッドは「因子」の規則にそれぞれ対応しています。括弧で囲まれた部分は、`parseFactor` メソッドが `parseExpression` メソッドを呼び出して読むため、入れ子の式も同じ仕組みで組み立てられます。

このように構文解析は、文字列を読む処理と、読んだ結果から木を組み立てる処理を担当します。また、木を計算する `Formula` インターフェースとその実装クラスは、この処理とは別に存在します。そのため、入力の書き方を変えても、直す必要があるのは構文解析の部分で、`Formula` の実装クラスは変更されません。<br>
構文解析の具体的な実装は、「再帰下降構文解析」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】規則が増えたときのデメリット

正しい実装を振り返ると、変数・足し算・掛け算の規則を、それぞれ `Formula` インターフェースを実装したクラスとして表していました。そのため、規則が増えると、その数だけクラスも増えるデメリットがあります。<br>
さらに、式の計算は `calculate` メソッドで木をたどって行うため、式が長くなるほど、オブジェクトの呼び出しの回数も増えます。

また、式の計算以外の操作（例えば、式を文字列に戻す操作）を追加する場合は、`Formula` インターフェースに抽象メソッドを追加し、`VariableFormula`・`AddFormula`・`MultiplyFormula` クラスすべてに実装を書き足す必要があります。<br>
この「操作を増やすと、すべての規則のクラスを修正する」点は、Visitor パターンとは反対の特徴です。Visitor パターンでは、操作を規則のクラスの外にまとめるため、操作を増やしても規則のクラスは変わりません。その代わり、規則のクラスを増やすと、すべての操作のクラスを修正する必要があります。<br>
このように、操作の追加が中心の場合は Visitor パターンを検討し、規則の追加が中心の場合は Interpreter パターンのまま増やしていくのが適切です。詳しくは「Visitor パターン」で検索してみてください。

文法が小さく、構文解析を含めても作業量が少ない場合は、Interpreter パターンを自分で実装するだけで十分です。反対に、文法が大きくなる場合は、構文解析を含めて自作するよりも、構文解析器を自動生成するツール（ANTLR など）を使うことを検討するのが適切です。<br>
実務では、Spring Framework の SpEL のように、式を木（AST）として扱って計算する仕組みも使われています。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Interpreter パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
