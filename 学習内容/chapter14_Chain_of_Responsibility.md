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
    - [承認者の並べ方を間違えるとどうなるか](#承認者の並べ方を間違えるとどうなるか)
    - [鎖の終端まで到達した場合の安全策](#鎖の終端まで到達した場合の安全策)
- [【深堀り②】Decorator パターンとの構造的な違い](#深堀り2)
- [【深堀り③】OCP（オープン・クローズドの原則）](#深堀り3)
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

実行クラスは次のようになると思います。

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
佐藤さんの申請（消耗品費・80000円） → 課長が承認しました。
田中さんの申請（備品費・450000円） → 部長が承認しました。
伊藤さんの申請（設備費・900000円） → 役員が承認しました。
高橋さんの申請（接待交際費・3000円） → 役員が承認しました。
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 承認権限のルールが変わる（例えば「新しい費目のルールを追加する」「金額の区切りを変更する」）たびに、`approve` メソッド自体を直接修正しなければならない。ルールが増えるほど `if-else` の連鎖が長くなり、見通しが悪化していく。
- 主任・課長・部長・役員それぞれの承認条件が 1 つのメソッドの中に混在しており、ある承認者の条件だけを個別にテストすることができない。
- 条件を判定する順序に承認結果が依存しているため、`if` の並び順を書き間違えると、意図しない承認者が先に承認してしまう（例えば「接待交際費」の判定を先頭に置き忘れると、金額次第で主任や課長が承認してしまう）（→ [鎖の並び順が結果を左右する](#深堀り1)）。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Chain of Responsibility パターン**です。<br>
承認者を「金額や費目を見て、自分が承認できるか判断し、できなければ次の承認者に回す」という独立したオブジェクトとして表現することで、承認ルールの追加や変更を、既存のクラスに手を入れずに行えるようになります。

まず、承認者に共通する振る舞いを定義する抽象クラスから見ていきましょう。

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
        for (Approver approver = this; approver != null; approver = approver.nextApprover) {
            if (approver.canApprove(request)) {
                System.out.println(request + " → " + approver.name + "が承認しました。");
                return;
            }
        }
        System.out.println(request + " → 承認できる担当者が見つかりませんでした。");
    }

    protected abstract boolean canApprove(ExpenseRequest request);

    protected boolean isEntertainment(ExpenseRequest request) {
        return "接待交際費".equals(request.getCategory());
    }
}
```

`Approver` クラスは、次の承認者への参照 `nextApprover` を自分自身の中に持ち、`setNext` メソッドで鎖を組み立てます。`setNext` メソッドの戻り値を `this` ではなく引数の `nextApprover` にしているのは、`a.setNext(b).setNext(c)` のように呼び出しをつなげるだけで、`a → b → c` という 1 本の鎖を順番通りに組み立てられるようにするためです。

`approve` メソッドは、自分自身から鎖を順にたどり、`canApprove` メソッドが `true` を返した最初の承認者が処理を行う、という共通の手順を担っています。一方、「自分がその申請を承認できるかどうか」の具体的な判定は、抽象メソッド `canApprove` としてサブクラスに委ねています。`isEntertainment` メソッドは、複数のサブクラスで共通して使う「費目が接待交際費かどうか」の判定をまとめたものです。

続いて、具体的な承認者のクラスを見ていきましょう。

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

`ExecutiveApprover` クラスの `canApprove` メソッドは常に `true` を返します。これにより、金額が 50 万円以上の申請だけでなく、他の承認者では処理できない「接待交際費」の申請も含め、鎖の末端で必ず承認が行われるようになっています。

最後に、`Main` クラスで鎖を組み立てて実行します。

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

好ましくない実装と同じ結果を保ったまま、以下のようなメリットが得られました。

- 承認権限のルールが変わる場合、新しい `Approver` のサブクラスを 1 つ追加し、`Main` クラス側で鎖に組み込むだけで済み、既存のクラスを変更する必要がない（→ [OCP（オープン・クローズドの原則）](#深堀り3)）。
- 主任・課長・部長・役員それぞれの承認条件が、対応するクラスの `canApprove` メソッドに閉じているため、承認者ごとに個別にテストできる。
- 鎖の組み立て（`setNext` の呼び出し順）が `Main` クラスの 1 箇所にまとまっているため、承認の順序を見通しやすい。

## まとめ

正しい実装を振り返ると、`Approver` クラスの各サブクラスは「自分がその申請を承認できるか」の判断だけに専念し、承認者どうしのつながり（鎖）は `setNext` メソッドによって外部から組み立てられています。<br>
このように、Chain of Responsibility パターンは、要求を処理できるオブジェクトが見つかるまで、複数の候補オブジェクトを鎖状につないで順に確認していくパターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】鎖の並び順が結果を左右する

### 承認者の並べ方を間違えるとどうなるか

正しい実装では、`Main` クラスで次のように鎖を組み立てました。

```java
supervisor.setNext(sectionChief).setNext(departmentHead).setNext(executive);
```

もし、この並び順を間違えて `ExecutiveApprover` クラスのインスタンスを鎖の先頭に置いてしまうとどうなるでしょうか。

```java
executive.setNext(supervisor).setNext(sectionChief).setNext(departmentHead);

executive.approve(new ExpenseRequest("鈴木", 5000, "交通費"));
```

```
鈴木さんの申請（交通費・5000円） → 役員が承認しました。
```

`ExecutiveApprover` クラスの `canApprove` メソッドは常に `true` を返すため、鎖の先頭に置かれた時点で、後続の主任・課長・部長には一切処理が回ってこなくなります。

Chain of Responsibility パターンは、承認条件そのものを各承認者のクラスへ分離してくれますが、承認者をどの順番で鎖につなぐかという責任までは肩代わりしてくれません。好ましくない実装で問題になっていた「条件の並び順への依存」は、`if-else` の並び順から `setNext` の呼び出し順へと形を変えて残っています。ただし、好ましくない実装ではその依存が 1 つのメソッドの中の条件分岐に埋もれていたのに対し、正しい実装では `Main` クラスの鎖の組み立て箇所という 1 か所に集約されているため、見直しや確認がしやすくなっています。

### 鎖の終端まで到達した場合の安全策

`Approver` クラスの `approve` メソッドには、鎖の末端まで進んでも `canApprove` メソッドが `true` を返す承認者が見つからなかった場合の分岐（「承認できる担当者が見つかりませんでした。」という出力）を用意しています。

本記事のシナリオでは `ExecutiveApprover` クラスが常に承認できるため、この分岐が実行されることはありません。しかし、鎖の組み立てを誤って `ExecutiveApprover` クラスのインスタンスを鎖に加え忘れた場合など、鎖の構成ミスによってどの承認者も条件に一致しない状況は起こり得ます。そのような場合に何も出力されないまま処理が終わってしまうと、申請がどこにも承認されずに消えてしまったことに誰も気づけません。

鎖の最後に「必ず処理できるオブジェクト」を置くか、あるいは今回のように鎖の末端に到達した場合の挙動を明示的に用意しておくかは、Chain of Responsibility パターンを使う際に意識しておきたい設計判断です。

<a id="深堀り2"></a>

## 【深堀り②】Decorator パターンとの構造的な違い

Chain of Responsibility パターンの `Approver` クラスは、次の対象への参照（`nextApprover`）を自分自身の中に持ち、`setNext` メソッドでその参照を外部から設定する、という構造をしています。これは、Decorator パターンにおいて、各デコレータークラスが対象（ラップ対象）への参照をコンストラクタなどで受け取り、内部に保持する構造とよく似ています。どちらも、共通の抽象型を実装したオブジェクトどうしを数珠つなぎにする、という点では共通しています。

しかし、鎖につながれたオブジェクトの働き方は対照的です。

- Decorator パターンでは、鎖につながれたオブジェクトが**すべて**処理に関与します。各デコレーターは、自分の処理を行ってから対象へ処理を委譲する（またはその逆）ため、呼び出しは必ず鎖の最後まで到達し、それぞれの処理結果が積み重なっていきます。
- Chain of Responsibility パターンでは、鎖につながれたオブジェクトのうち、条件に合致した**1 つだけ**が処理を行います。`canApprove` メソッドが `true` を返した時点で処理は完結し、それより後ろの承認者へは処理が渡りません。

同じ「自分自身と同じ型への参照を持つオブジェクトを鎖状につなぐ」という構造でありながら、Decorator パターンは「全員が処理に加算的に関与する」ことを、Chain of Responsibility パターンは「条件に合った 1 人だけが排他的に処理する」ことを目的にしている、という違いを押さえておくと、両者を混同しにくくなります。

<a id="深堀り3"></a>

## 【深堀り③】OCP（オープン・クローズドの原則）

正しい実装をもとに、新しい決裁権限のルールを追加してみましょう。<br>
例えば「100 万円以上は役員決裁、50 万円以上 100 万円未満は本部長決裁とする」という規程改定があったとします。

まず、新しい承認者のクラスを追加します。

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

次に、`Main` クラス側で鎖の組み立てを変更します。

```java
Approver generalManager = new GeneralManagerApprover("本部長");

supervisor.setNext(sectionChief).setNext(departmentHead).setNext(generalManager).setNext(executive);
```

上記の修正のみで、新しい決裁権限のルールを反映できました。`Approver`・`SupervisorApprover`・`SectionChiefApprover`・`DepartmentHeadApprover`・`ExecutiveApprover` クラスには一切手を加えていません。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。Chain of Responsibility パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

<a id="深堀り4"></a>

## 【深堀り④】Java 標準ライブラリにおける Chain of Responsibility パターンの例

Java 標準ライブラリにおける Chain of Responsibility パターンの例として、`java.util.logging` パッケージの `Logger` クラスによるログ出力の仕組みを見ていきましょう。

**`Logger.java`（抜粋）**

```java
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
```

> 引用元: OpenJDK [Logger.java](https://github.com/openjdk/jdk/blob/master/src/java.logging/share/classes/java/util/logging/Logger.java)

`isSystemLogger` に関する分岐は JDK 内部向けの最適化のための実装なので読み飛ばして構いません。注目すべきは `while` ループの本体です。`Logger` クラスは、自分に登録された `Handler` すべてに対して `publish` メソッドを呼び出したあと、`useParentHandlers` が `true` である限り、`logger = logger.getParent()` によって親の `Logger` クラスへと処理を移し、同じ手順を繰り返します。

本記事の `Approver` クラスでは、`canApprove` メソッドが `true` を返した最初の 1 人だけが処理を行い、そこで鎖が止まる仕組みでした。一方、`Logger` クラスの場合は、途中で処理を打ち切るのではなく、`useParentHandlers` が `false` になるか、親をたどりきるまで、鎖上のすべての `Logger` クラスの `Handler` が呼び出され続けます。「途中で条件に合った 1 人が処理して止まる」か「鎖上の全員が処理に関与し続ける」かの違いはありますが、「自分自身では対応しきれない場合に、鎖でつながった次のオブジェクトへ処理を委ねていく」という構造は、本記事の Chain of Responsibility パターンと共通しています。

<a id="深堀り5"></a>

## 【深堀り⑤】GoF デザインパターンとの位置づけ

今回使った Chain of Responsibility パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
