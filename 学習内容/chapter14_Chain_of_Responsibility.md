# Chain of Responsibility（チェーンオブレスポンシビリティ）パターン ― 要求を処理できるオブジェクトまで順に回す

次のような経験をしたことはありませんか？

> 処理を行うオブジェクトや条件を判定する分岐処理を 1 つのメソッドにまとめて書いていたら、オブジェクトの種類や条件が増えるたびに、その分岐へ手を入れる羽目になった。おまけに、条件を書く順番を 1 つ間違えただけで、本来とは違うオブジェクトが処理してしまうこともあった。

この記事では、社内の経費精算システムに承認フローを追加するシナリオを通して、Chain of Responsibility パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】鎖の並び順が結果を左右する](#深堀り1)
    - [鎖の終端まで到達した場合の安全策](#鎖の終端まで到達した場合の安全策)
    - [鎖が非常に長い場合の実装の選び方](#鎖が非常に長い場合の実装の選び方)
- [【深堀り②】Decorator パターンとの構造的な違い](#深堀り2)
- [【深堀り③】OCP（オープン・クローズドの原則）](#深堀り3)
    - [金額以外の軸でルールが増えた場合](#金額以外の軸でルールが増えた場合)
- [【深堀り④】Java 標準ライブラリにおける Chain of Responsibility パターンの例](#深堀り4)
- [【深堀り⑤】GoF デザインパターンとの位置づけ](#深堀り5)

---

## 【具体例】

### シナリオ

> あなたは社内の経費精算システムの開発チームに所属しています。<br>
> 現在、社員が経費を申請すると「主任が内容を確認して承認する」という 1 段階の仕組みだけが実装されています。申請額の大小にかかわらず、承認権限が主任 1 人に集中している状態です。<br>
> ある日、経理部から「決裁権限規程を見直すので、申請額や費目に応じて、権限のある承認者まで自動的に確認が回るようにしてほしい」という依頼が来ました。あなたは、申請額に応じて 3 万円未満なら主任、10 万円未満なら課長、50 万円未満なら部長、50 万円以上なら役員が承認し、費目が「接待交際費」の場合は金額にかかわらず役員が承認する、という仕組みを実装することになりました。

※実際の経費精算システムでは、承認結果をデータベースへ保存し、申請者へメールなどで結果を通知する実装が必要ですが、本記事では Chain of Responsibility パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `ExpenseRequest` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `ExpenseRequest`（既存クラス）

経費申請 1 件の情報を保持するクラスです。

| フィールド      | 型       | 説明         |
| --------------- | -------- | ------------ |
| `applicantName` | `String` | 申請者名     |
| `amount`        | `int`    | 申請額（円） |
| `category`      | `String` | 費目         |

| メソッド           | 引数 | 戻り値の型 | 説明                           |
| ------------------ | ---- | ---------- | ------------------------------ |
| `getApplicantName` | なし | `String`   | 申請者名を取得する             |
| `getAmount`        | なし | `int`      | 申請額を取得する               |
| `getCategory`      | なし | `String`   | 費目を取得する                 |
| `toString`         | なし | `String`   | 申請内容を文字列として整形する |

**`ExpenseRequest.java`**

```java
package example;

public class ExpenseRequest {
    private String applicantName;
    private int amount;
    private String category;

    public ExpenseRequest(String applicantName, int amount, String category) {
        this.applicantName = applicantName;
        this.amount = amount;
        this.category = category;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public int getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String toString() {
        return applicantName + "さんの申請（" + category + "・" + amount + "円）";
    }
}
```

<br>

- `ApprovalService`（既存クラス）

経費申請の承認処理を行うクラスです。<br>
常に主任が承認した結果を出力する処理を行います。

| メソッド  | 引数                     | 戻り値の型 | 説明                                         |
| --------- | ------------------------ | ---------- | -------------------------------------------- |
| `approve` | `ExpenseRequest request` | `void`     | 常に主任が承認した結果をコンソールに出力する |

**`ApprovalService.java`**

```java
package example;

public class ApprovalService {
    public void approve(ExpenseRequest request) {
        System.out.println(request + " → 主任が承認しました。");
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
        ApprovalService approvalService = new ApprovalService();

        approvalService.approve(new ExpenseRequest("鈴木", 5000, "交通費"));
        approvalService.approve(new ExpenseRequest("佐藤", 80000, "消耗品費"));
        approvalService.approve(new ExpenseRequest("田中", 450000, "備品費"));
        approvalService.approve(new ExpenseRequest("伊藤", 900000, "設備費"));
        approvalService.approve(new ExpenseRequest("高橋", 3000, "接待交際費"));
    }
}
```

**実行結果**

```
鈴木さんの申請（交通費・5000円） → 主任が承認しました。
佐藤さんの申請（消耗品費・80000円） → 主任が承認しました。
田中さんの申請（備品費・450000円） → 主任が承認しました。
伊藤さんの申請（設備費・900000円） → 主任が承認しました。
高橋さんの申請（接待交際費・3000円） → 主任が承認しました。
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、`ApprovalService` クラスの `approve` メソッドに、申請額や費目に応じた条件分岐を追加する、という実装ではないでしょうか？

**`ApprovalService.java`**

```java
package example;

public class ApprovalService {
    public void approve(ExpenseRequest request) {
        if ("接待交際費".equals(request.getCategory())) {
            System.out.println(request + " → 役員が承認しました。");
        } else if (request.getAmount() < 30000) {
            System.out.println(request + " → 主任が承認しました。");
        } else if (request.getAmount() < 100000) {
            System.out.println(request + " → 課長が承認しました。");
        } else if (request.getAmount() < 500000) {
            System.out.println(request + " → 部長が承認しました。");
        } else {
            System.out.println(request + " → 役員が承認しました。");
        }
    }
}
```

実行クラスの変更はなし。

**実行結果**

```
鈴木さんの申請（交通費・5000円） → 主任が承認しました。
佐藤さんの申請（消耗品費・80000円） → 課長が承認しました。
田中さんの申請（備品費・450000円） → 部長が承認しました。
伊藤さんの申請（設備費・900000円） → 役員が承認しました。
高橋さんの申請（接待交際費・3000円） → 役員が承認しました。
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 承認権限のルールが変わる（例えば「新しい金額区分を追加する」）たびに、`approve` メソッド自体を直接修正しなければならない。
    - その結果、ルールが増えるほど条件分岐の連鎖が長くなり、見通しが悪化したり、修正漏れが出てきたりする。
- 各々の承認条件が 1 つのメソッドの中に混在しており、金額区分や費目の組み合わせに対応する個々のルールだけを切り出してテストすることができない。
- 条件分岐の並び順によって承認結果が変わるため、書き間違えると意図しない権限レベルで承認されてしまう。
    - 例えば「接待交際費」の判定を条件分岐の途中に置いて修正をした場合、金額次第では主任や課長が承認することになってしまう。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Chain of Responsibility パターン**です。<br>
承認処理を「金額や費目を見て、自分が承認できるか判断し、できなければ次のクラスに回す」という独立したクラスとして切り出すことで、既存のクラスを修正することなく承認権限のルールの追加や変更を行えるようになります。

まず、承認処理を担うクラスに共通する振る舞いを定義する抽象クラスから見ていきましょう。

**`Approver.java`**

```java
package example;

public abstract class Approver {
    private String name;
    private Approver nextApprover;

    public Approver(String name) {
        this.name = name;
    }

    public Approver setNext(Approver nextApprover) {
        this.nextApprover = nextApprover;
        return nextApprover;
    }

    public void approve(ExpenseRequest request) {
        if (canApprove(request)) {
            System.out.println(request + " → " + name + "が承認しました。");
        } else if (nextApprover != null) {
            nextApprover.approve(request);
        } else {
            System.out.println(request + " → 承認できる担当者が見つかりませんでした。");
        }
    }

    protected abstract boolean canApprove(ExpenseRequest request);

    protected boolean isEntertainment(ExpenseRequest request) {
        return "接待交際費".equals(request.getCategory());
    }
}
```

`Approver` は既存のコードの `ApprovalService` クラスを吸収した抽象クラスで、次の承認処理を担うクラスへの参照（`nextApprover` フィールド）を自分自身の中に持ち、`setNext` メソッドで鎖を組み立てます。ちなみに、`setNext` メソッドの戻り値が `nextApprover` なのは、`a.setNext(b).setNext(c)` のように呼び出しをつなげて `a → b → c` と順番通りに 1 本の鎖を組み立てられるようにするためです。

`approve` メソッドは、`canApprove` メソッドの判定結果をもとに、承認できればその場で処理を行い、承認できなければ `nextApprover.approve(request)` を呼び出して次の承認処理を担うクラスに処理を委譲する、という手順を共通で担っています。この委譲を再帰的に繰り返すことで、鎖全体をたどっていきます。<br>
抽象メソッド `canApprove` は、「自分がその申請を承認できるかどうか」の具体的な判定をサブクラスに委ねています。<br>
`isEntertainment` メソッドは、複数のサブクラスで共通して使う「費目が接待交際費かどうか」の判定をまとめたものです。

続いて、具体的な承認処理を担うクラスを見ていきましょう。

**`SupervisorApprover.java`**

```java
package example;

public class SupervisorApprover extends Approver {
    private static final int LIMIT = 30000;

    public SupervisorApprover(String name) {
        super(name);
    }

    @Override
    protected boolean canApprove(ExpenseRequest request) {
        return !isEntertainment(request) && request.getAmount() < LIMIT;
    }
}
```

**`SectionChiefApprover.java`**

```java
package example;

public class SectionChiefApprover extends Approver {
    private static final int LIMIT = 100000;

    public SectionChiefApprover(String name) {
        super(name);
    }

    @Override
    protected boolean canApprove(ExpenseRequest request) {
        return !isEntertainment(request) && request.getAmount() < LIMIT;
    }
}
```

**`DepartmentHeadApprover.java`**

```java
package example;

public class DepartmentHeadApprover extends Approver {
    private static final int LIMIT = 500000;

    public DepartmentHeadApprover(String name) {
        super(name);
    }

    @Override
    protected boolean canApprove(ExpenseRequest request) {
        return !isEntertainment(request) && request.getAmount() < LIMIT;
    }
}
```

**`ExecutiveApprover.java`**

```java
package example;

public class ExecutiveApprover extends Approver {
    public ExecutiveApprover(String name) {
        super(name);
    }

    @Override
    protected boolean canApprove(ExpenseRequest request) {
        return true;
    }
}
```

`SupervisorApprover`・`SectionChiefApprover`・`DepartmentHeadApprover` はいずれも新たに追加したクラスで、抽象クラス `Approver` を継承し、`canApprove` メソッドで「申請額が自分の上限金額未満かどうか」を具体的に判定しています。また、`!isEntertainment(request)` によって「接待交際費」を判定対象から除外しているため、この費目の申請は金額にかかわらずこれらのクラスでは承認されず、鎖を順にたどって次のオブジェクトへの委譲が繰り返されます。<br>
`ExecutiveApprover` も新たに追加したクラスで、抽象クラス `Approver` を継承し、`canApprove` メソッドで常に `true` を返します。これにより、自分に回ってきた申請を、金額や費目にかかわらず必ず承認します。

最後に、実行クラスを見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        Approver supervisor = new SupervisorApprover("主任");
        Approver sectionChief = new SectionChiefApprover("課長");
        Approver departmentHead = new DepartmentHeadApprover("部長");
        Approver executive = new ExecutiveApprover("役員");

        supervisor.setNext(sectionChief).setNext(departmentHead).setNext(executive);

        supervisor.approve(new ExpenseRequest("鈴木", 5000, "交通費"));
        supervisor.approve(new ExpenseRequest("佐藤", 80000, "消耗品費"));
        supervisor.approve(new ExpenseRequest("田中", 450000, "備品費"));
        supervisor.approve(new ExpenseRequest("伊藤", 900000, "設備費"));
        supervisor.approve(new ExpenseRequest("高橋", 3000, "接待交際費"));
    }
}
```

**実行結果**

```
鈴木さんの申請（交通費・5000円） → 主任が承認しました。
佐藤さんの申請（消耗品費・80000円） → 課長が承認しました。
田中さんの申請（備品費・450000円） → 部長が承認しました。
伊藤さんの申請（設備費・900000円） → 役員が承認しました。
高橋さんの申請（接待交際費・3000円） → 役員が承認しました。
```

`Main` クラスを振り返ると、既存の `ApprovalService` クラスに代わって `Approver` クラスの各サブクラス（`SupervisorApprover`・`SectionChiefApprover`・`DepartmentHeadApprover`・`ExecutiveApprover`）のインスタンスを生成し、`setNext` メソッドで一本の鎖に組み立てています。また、承認処理に関しては、先頭の `supervisor` に対して `approve` メソッドを呼び出すだけになっています。<br>
ただし、好ましくない実装の問題点 3 つ目にあった `if-else` の並び順によって承認結果が変わるという点に関して、正しい実装でも `setNext` メソッドの呼び出し順によって変わるため、問題点が完全に解決できたわけではありません（→ [鎖の並び順が結果を左右する](#深堀り1)）。ただし、`if-else` の場合の金額や費目の判定ロジックと絡み合っていた依存が `setNext` メソッドにより `Main` クラスの 1 行に切り出されたことで、見直すべき箇所は明確にはなっています。

実行結果は、好ましくない実装とまったく同じになっています。

以上のような実装を行うと、以下のメリットがあります。

- 承認権限のルールが変わる（例えば「新しい金額区分を追加する」）場合、`Approver` のサブクラスを新たに 1 つ追加し、`Main` クラス側で鎖に組み込むだけで済み、既存のクラスを変更する必要がない。
    - ただし、既存の複数の `Approver` サブクラスにまたがる新しい費目のルールを追加する場合は、この限りではない（→ [金額以外の軸でルールが増えた場合](#金額以外の軸でルールが増えた場合)）。
- 主任・課長・部長・役員それぞれの承認条件が、対応するクラスの `canApprove` メソッドに閉じているため、金額区分や費目の組み合わせに対応する個々のルールだけを切り出してテストができる。

## まとめ

正しい実装を振り返ると、`Approver` クラスの各サブクラスは「自分がその申請を承認できるか」の判断だけに専念し、承認処理を担うクラス同士のつながり（鎖）は `setNext` メソッドによって外部から組み立てられています。<br>
このように、Chain of Responsibility パターンは、要求を処理できるオブジェクトが見つかるまで、複数の候補オブジェクトを鎖状につないで順に確認していくパターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】鎖の並び順が結果を左右する

正しい実装を振り返ると、承認処理の順番を次のようにしています。

**`Main.java`（一部抜粋）**

```java
package example;

public class Main {
    public static void main(String[] args) {
        supervisor.setNext(sectionChief).setNext(departmentHead).setNext(executive);
    }
}
```

もし、鎖の並び順を変えて、次のように `ExecutiveApprover` クラスのインスタンスを鎖の先頭に置くとどうなるでしょうか。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        Approver supervisor = new SupervisorApprover("主任");
        Approver sectionChief = new SectionChiefApprover("課長");
        Approver departmentHead = new DepartmentHeadApprover("部長");
        Approver executive = new ExecutiveApprover("役員");

        executive.setNext(supervisor).setNext(sectionChief).setNext(departmentHead);

        executive.approve(new ExpenseRequest("鈴木", 5000, "交通費"));
    }
}
```

**実行結果**

```
鈴木さんの申請（交通費・5000円） → 役員が承認しました。
```

実行結果を振り返ると、`ExecutiveApprover` クラスの `canApprove` メソッドは常に `true` を返すため、鎖の先頭に置くと、後続の主任・課長・部長には一切処理が回ってこなくなります。

Chain of Responsibility パターンは、承認条件そのものを各 `Approver` のサブクラスへ分離してくれますが、オブジェクトをどの順番で鎖につなぐかという責任までは肩代わりしてくれません。好ましくない実装で問題になっていた「条件の並び順への依存」は、`if-else` の並び順から `setNext` の呼び出し順へと形を変えて残っています。ただし、好ましくない実装ではその依存が 1 つのメソッドの中の条件分岐に埋もれていたのに対し、正しい実装では `Main` クラスの鎖の組み立て箇所という 1 か所に集約されているため、見直しや確認がしやすくなっています。

### 鎖の終端まで到達した場合の安全策

正しい実装の抽象クラス `Approver` の `approve` メソッドを振り返ると、鎖の末端で `canApprove` メソッドが `true` を返すオブジェクトが見つからなかった場合の分岐を用意しています。

本記事では `ExecutiveApprover` クラスの `canApprove` メソッドが `true` を返すため、この分岐は実行されません。しかし、`Main` クラスで鎖の組み立てを誤り `ExecutiveApprover` クラスのインスタンスを鎖に加え忘れた場合など、鎖の構成ミスによってどのオブジェクトも条件に一致しない状況は起こり得ます。そのような場合に何も出力されないと、申請がどこにも承認されずに消えてしまっても、テストはおろか本番でも誰も気づけません。

鎖の最後に「必ず処理できるオブジェクト」を置くか、あるいは今回のように鎖の末端に到達した場合の挙動を明示的に用意しておくかは、Chain of Responsibility パターンを使う際に意識しておきたい設計判断です。

### 鎖が非常に長い場合の実装の選び方

正しい実装の抽象クラス `Approver` の `approve` メソッドを振り返ると、自分が承認できなければ `nextApprover.approve(request)` を呼び出して次のオブジェクトへ処理を委譲する、という再帰呼び出しで鎖をたどっています。「自分で対応できなければ次のオブジェクトに委ねる」という Chain of Responsibility パターンの考え方が、そのままコードの構造として表れるため、パターンの意図が伝わりやすい実装です。

ただし、再帰呼び出しは、呼び出すたびにメソッドの実行状態をスタックと呼ばれるメモリ領域に積んでいく仕組みのため、鎖の長さがそのままスタックの深さになります。本記事のシナリオのようにオブジェクトが数段程度であれば問題になりませんが、鎖につながるオブジェクトの数が数万に達するような極端なケースでは、`StackOverflowError` が発生するおそれがあります。

そのような場合は、次のように `for` 文で鎖をたどる実装にすることで、スタックを消費せずに済みます。

**`Approver.java`（一部抜粋）**

```java
package example;

public abstract class Approver {
    public void approve(ExpenseRequest request) {
        for (Approver approver = this; approver != null; approver = approver.nextApprover) {
            if (approver.canApprove(request)) {
                System.out.println(request + " → " + approver.name + "が承認しました。");
                return;
            }
        }
        System.out.println(request + " → 承認できる担当者が見つかりませんでした。");
    }
}
```

※ここでは `for` 文を使っていますが、`while` 文でも同様の実装は可能です。ただし `while` 文で書くと、ループ変数 `approver` の宣言がループの外に必要になり、スコープが不必要に広がってしまいます。ループ変数をループ内に閉じ込められる分、`for` 文の方がこの種の処理には向いています。

再帰呼び出しによる実装と `for` 文による実装は、どちらも「鎖をたどって処理できるオブジェクトを探す」という結果は変わりません。パターンの意図を素直に表現したいか、鎖が極端に長くなる可能性を踏まえて安全性を優先したいかによって、どちらの実装を選ぶかが変わってきます。

<a id="深堀り2"></a>

## 【深堀り②】Decorator パターンとの構造的な違い

Chain of Responsibility パターンの `Approver` クラスは、次の対象への参照（`nextApprover`）を自分自身の中に持ち、`setNext` メソッドでその参照を外部から設定する、という構造をしています。これは、Decorator パターンにおいて、処理する対象を包み込む各クラスがラップ対象への参照をコンストラクタなどで受け取り、内部に保持する構造とよく似ています。どちらも、共通の抽象クラスを継承したオブジェクト同士を鎖状につなぐ、という構造をしています。

しかし、鎖につながれたオブジェクトの働き方は対照的です。
Decorator パターンは、鎖につながれたオブジェクトが**すべて**処理に関与します。自分の処理を行ってから、内部に保持している対象へ処理を委譲する（またはその逆）ため、呼び出しは必ず鎖の最後まで到達し、処理結果が積み重なっていきます。
一方、Chain of Responsibility パターンは、鎖につながれたオブジェクトのうち条件に合致した**1 つだけ**が処理を行います。鎖の途中で条件に合致したオブジェクトがあれば、以降の処理は行われません。

以上から、「自分自身と同じ型への参照を持つオブジェクトを鎖状につなぐ」という構造は共通していますが、Decorator パターンは「鎖状につながった全オブジェクトが処理に加算的に関与する」という目的のために使用されるのに対し、Chain of Responsibility パターンは「条件に合ったオブジェクトだけが排他的に処理する」という目的のために使用される点で異なります。

<a id="深堀り3"></a>

## 【深堀り③】OCP（オープン・クローズドの原則）

正しい実装を振り返ると、承認権限のルールが変わる場合（例えば「新しい金額区分を追加する」場合）、`Approver` のサブクラスを新たに 1 つ追加し、`Main` クラス側で鎖に組み込むだけで済み、既存の `Approver` クラス、`Approver` のサブクラスには一切手を加える必要がありません。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。Chain of Responsibility パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

### 金額以外の軸でルールが増えた場合

例えば、新しい費目の判定軸が増える場合を考えてみましょう。<br>
費目の判定を担う `isEntertainment` メソッドは、`SupervisorApprover` をはじめとする既存の `Approver` の多くのサブクラスが、`canApprove` メソッド内でこのメソッドを直接呼び出しています。そのため、新しい費目の判定軸を追加しようとすると、呼び出し元である既存の複数クラスに手を加えることになり、**OCP** に違反しています。

一方、金額区分が増える場合は異なります。例えば「50 万円以上 100 万円未満は本部長、100 万円以上は役員が承認する」という規程改定があった場合を考えてみましょう。<br>
この場合、次のように `Approver` のサブクラスを追加し、鎖の組み立てを変更するだけで対応できます。

**`GeneralManagerApprover.java`**

```java
package example;

public class GeneralManagerApprover extends Approver {
    private static final int LIMIT = 1000000;

    public GeneralManagerApprover(String name) {
        super(name);
    }

    @Override
    protected boolean canApprove(ExpenseRequest request) {
        return !isEntertainment(request) && request.getAmount() < LIMIT;
    }
}
```

**`Main.java`（一部抜粋）**

```java
package example;

public class Main {
    public static void main(String[] args) {
        Approver generalManager = new GeneralManagerApprover("本部長");

        supervisor.setNext(sectionChief).setNext(departmentHead).setNext(generalManager).setNext(executive);
    }
}
```

上記の実装から、既存の `Approver` クラス、`Approver` のサブクラスには一切手を加えていないため、**OCP** を守っています。

以上から、本記事では、金額の区切りの追加は修正範囲が閉じている一方、費目のような判定軸の追加では閉じていない、という非対称な設計となっています。

実務では、この種の条件をサブクラスやメソッドとして増やしていくのではなく、`Predicate<ExpenseRequest>` インターフェースのような条件オブジェクトをコンストラクタで注入したり、承認ルールをデータベースや設定ファイルなどのデータとして持たせたりすることで、判定軸が増えてもコードを変更せずに対応できるようにするのが一般的です。

<a id="深堀り4"></a>

## 【深堀り④】Java 標準ライブラリにおける Chain of Responsibility パターンの例

Java 標準ライブラリにおける Chain of Responsibility パターンの例として、`java.util.logging` パッケージの `Logger` クラスによるログ出力の仕組みを見ていきましょう。

**`Logger.java`（抜粋）**

```java
public class Logger {
    public void log(LogRecord record) {
        if (!isLoggable(record.getLevel())) {
            return;
        }
        Filter theFilter = config.filter;
        if (theFilter != null && !theFilter.isLoggable(record)) {
            return;
        }

        Logger logger = this;
        while (logger != null) {
            final Handler[] loggerHandlers = isSystemLogger
                ? logger.accessCheckedHandlers()
                : logger.getHandlers();

            for (Handler handler : loggerHandlers) {
                handler.publish(record);
            }

            final boolean useParentHdls = isSystemLogger
                ? logger.config.useParentHandlers
                : logger.getUseParentHandlers();

            if (!useParentHdls) {
                break;
            }

            logger = isSystemLogger ? logger.parent : logger.getParent();
        }
    }
}
```

> 引用元: OpenJDK [Logger.java](https://github.com/openjdk/jdk/blob/master/src/java.logging/share/classes/java/util/logging/Logger.java)

`isSystemLogger` に関する分岐は JDK 内部向けの最適化のための実装なので読み飛ばして構いません。

`while` ループの中身を振り返ると、`Logger` クラスは、自分に登録された `Handler` クラスのインスタンス（`loggerHandlers`）すべてに対して `publish` メソッドを呼び出します。その後 `useParentHandlers` が `false` であればそこでループを抜け（`if (!useParentHdls)`）、`true` であれば `logger = logger.getParent()` によって親の `Logger` クラスへと処理を移し、同じ手順を繰り返します。

本記事の `Approver` クラスは、`canApprove` メソッドが `true` を返したオブジェクトが処理した時点で鎖を止めます。一方 `Logger` クラスは、`useParentHandlers` が `false` になるか鎖の終端に達するまで `publish` メソッドの呼び出しを続けるため、鎖上のすべてのオブジェクトが処理に関与し続けます。この「途中で止まるか、最後まで関与し続けるか」という結果の違いはありますが、「自分自身で対応しきれない場合に、次のオブジェクトへ処理を委ねる」という構造は共通しており、`Logger` クラスも Chain of Responsibility パターンの一例だと言えます。

先ほどの `Logger` クラスの「鎖上のすべてのオブジェクトが処理に関与し続ける」という結果だけを見ると、Decorator パターンと同じに見えるかもしれません（→ [Decorator パターンとの構造的な違い](#深堀り2)）。しかし両者を分けるのは、関与するオブジェクトの数ではなく、次のオブジェクトへ処理を委ねるかどうかを鎖上の各オブジェクトがその都度、条件で判断しているかどうかです。Decorator パターンは鎖の構造自体が全オブジェクトの関与を保証しており、途中で処理を打ち切る分岐がありません。対して `Logger` クラスは `useParentHandlers` という条件を各オブジェクトがその都度評価しており、鎖の末端まで関与が続くのはその条件判定の結果に過ぎません。次に委ねるかどうかをオブジェクト自身がその都度判断する、という構造こそが Chain of Responsibility パターンの本質です。

<a id="深堀り5"></a>

## 【深堀り⑤】GoF デザインパターンとの位置づけ

今回使った Chain of Responsibility パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
