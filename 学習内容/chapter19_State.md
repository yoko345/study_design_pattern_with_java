# State（ステート）パターン ― 状態ごとの振る舞いをクラスに分ける

次のような経験をしたことはありませんか？

> 状態によって振る舞いが変わる処理を、メソッドごとに条件分岐で書いていたら、状態が 1 つ増えるたびに、すべてのメソッドの条件分岐を修正する羽目になった。おまけに、1 か所でも分岐を追加し忘れると、その状態のときだけ処理が何も行われない不具合を生んでしまうこともあった。

この記事では、経費精算システムの申請フローに承認のステップを追加するシナリオを通して、State パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】次の状態を誰が決めるか](#深堀り1)
- [【深堀り②】状態クラスを Singleton パターンで使い回す](#深堀り2)
- [【深堀り③】State パターンが苦手な変更](#深堀り3)
- [【深堀り④】Strategy パターンとの違い](#深堀り4)
- [【深堀り⑤】GoF デザインパターンとの位置づけ](#深堀り5)

---

## 【具体例】

### シナリオ

> あなたは社内の経費精算システムの開発チームに所属しています。<br>
> 現状の経費精算システムでは、社員が作成した経費申請は「下書き」「申請中」「承認済み」の 3 つの状態を持ち、申請者が申請すると「申請中」に、上長が承認すると「承認済み」になります。上長が内容に不備を見つけた場合は、申請を差し戻して「下書き」に戻すことができます。<br>
> ある日、経理部から「上長は支出が業務に必要かどうかは確認しているが、領収書の金額との照合までは行えていない」と指摘がありました。<br>
> そこであなたは、上長の承認後に経理部の確認を挟む「経理確認中」の状態を追加することになりました。<br>
> なお、経理部も上長と同じく、領収書の金額と照合して問題がなければ承認し、相違があれば申請を差し戻します。

※実際の経費申請では、申請内容や状態をデータベース（DB）に保存したり、申請者・承認者へ通知したりする実装が必要ですが、本記事では State パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `ExpenseReport` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `ReportStatus`（既存クラス）

経費申請の状態を表す列挙型です。<br>
状態ごとに、コンソールに出力する表示名を保持します。

| 定数             | 表示名     |
| ---------------- | ---------- |
| `DRAFT`          | `下書き`   |
| `MANAGER_REVIEW` | `申請中`   |
| `APPROVED`       | `承認済み` |

| メソッド   | 引数 | 戻り値の型 | 説明             |
| ---------- | ---- | ---------- | ---------------- |
| `getLabel` | なし | `String`   | 表示名を取得する |

**`ReportStatus.java`**

```java
package example;

public enum ReportStatus {
    DRAFT("下書き"),
    MANAGER_REVIEW("申請中"),
    APPROVED("承認済み");

    private String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
```

<br>

- `ExpenseReport`（既存クラス）

経費申請 1 件を表すクラスです。<br>
申請・承認・差し戻しの各操作で、現在の状態に応じて状態を切り替えるか、操作を受け付けられない理由を出力します。

| フィールド | 型             | 説明                         |
| ---------- | -------------- | ---------------------------- |
| `title`    | `String`       | 申請の件名                   |
| `status`   | `ReportStatus` | 現在の状態（初期値は下書き） |

| メソッド   | 引数             | 戻り値の型 | 説明                                                                         |
| ---------- | ---------------- | ---------- | ---------------------------------------------------------------------------- |
| `submit`   | なし             | `void`     | 申請する<br>下書きのときだけ申請中に切り替える                               |
| `approve`  | なし             | `void`     | 承認する<br>申請中のときだけ承認済みに切り替える                             |
| `sendBack` | なし             | `void`     | 差し戻す<br>申請中のときだけ下書きに切り替える                               |
| `print`    | `String message` | `void`     | 操作の結果に件名と現在の状態を添えてコンソールに出力する（クラス内部で使用） |

**`ExpenseReport.java`**

```java
package example;

public class ExpenseReport {
    private String title;
    private ReportStatus status = ReportStatus.DRAFT;

    public ExpenseReport(String title) {
        this.title = title;
    }

    public void submit() {
        switch (status) {
            case DRAFT -> {
                status = ReportStatus.MANAGER_REVIEW;
                print("申請しました");
            }
            case MANAGER_REVIEW -> print("すでに申請中です");
            case APPROVED -> print("承認済みのため申請できません");
        }
    }

    public void approve() {
        switch (status) {
            case DRAFT -> print("申請されていないため承認できません");
            case MANAGER_REVIEW -> {
                status = ReportStatus.APPROVED;
                print("承認しました");
            }
            case APPROVED -> print("すでに承認済みです");
        }
    }

    public void sendBack() {
        switch (status) {
            case DRAFT -> print("申請されていないため差し戻せません");
            case MANAGER_REVIEW -> {
                status = ReportStatus.DRAFT;
                print("差し戻しました");
            }
            case APPROVED -> print("承認済みのため差し戻せません");
        }
    }

    private void print(String message) {
        System.out.println("[経費申請] " + title + "：" + message + "（状態：" + status.getLabel() + "）");
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
        ExpenseReport report = new ExpenseReport("大阪出張の交通費");
        // 申請される前の経費申請を、上長が誤って承認しようとしている
        report.approve();
        // 申請者が申請している
        report.submit();
        // 上長が内容に不備を見つけたため、差し戻している
        report.sendBack();
        // 申請者が不備を直して、再度申請している
        report.submit();
        // 上長が承認している
        report.approve();
        // 承認済みの経費申請を、上長が誤って差し戻そうとしている
        report.sendBack();
    }
}
```

**実行結果**

```
[経費申請] 大阪出張の交通費：申請されていないため承認できません（状態：下書き）
[経費申請] 大阪出張の交通費：申請しました（状態：申請中）
[経費申請] 大阪出張の交通費：差し戻しました（状態：下書き）
[経費申請] 大阪出張の交通費：申請しました（状態：申請中）
[経費申請] 大阪出張の交通費：承認しました（状態：承認済み）
[経費申請] 大阪出張の交通費：承認済みのため差し戻せません（状態：承認済み）
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、列挙型 `ReportStatus` に「経理確認中」の定数を追加し、`ExpenseReport` クラスの各メソッドの `switch` 文に、既存の状態と同じように分岐を書き足す、という実装ではないでしょうか？

**`ReportStatus.java`**

```java
package example;

public enum ReportStatus {
    DRAFT("下書き"),
    MANAGER_REVIEW("申請中"),
    ACCOUNTING_REVIEW("経理確認中"), // ←ここを追加
    APPROVED("承認済み");

    private String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
```

**`ExpenseReport.java`**

```java
package example;

public class ExpenseReport {
    private String title;
    private ReportStatus status = ReportStatus.DRAFT;

    public ExpenseReport(String title) {
        this.title = title;
    }

    public void submit() {
        switch (status) {
            case DRAFT -> {
                status = ReportStatus.MANAGER_REVIEW;
                print("申請しました");
            }
            case MANAGER_REVIEW -> print("すでに申請中です");
            case ACCOUNTING_REVIEW -> print("経理確認中のため申請できません"); // ←ここを追加
            case APPROVED -> print("承認済みのため申請できません");
        }
    }

    public void approve() {
        switch (status) {
            case DRAFT -> print("申請されていないため承認できません");
            case MANAGER_REVIEW -> {
                status = ReportStatus.ACCOUNTING_REVIEW; // ←ここを修正
                print("承認しました");
            }
            /* ここを追加（ここから） */
            case ACCOUNTING_REVIEW -> {
                status = ReportStatus.APPROVED;
                print("承認しました");
            }
            /* ここを追加（ここまで） */
            case APPROVED -> print("すでに承認済みです");
        }
    }

    public void sendBack() {
        switch (status) {
            case DRAFT -> print("申請されていないため差し戻せません");
            case MANAGER_REVIEW -> {
                status = ReportStatus.DRAFT;
                print("差し戻しました");
            }
            /* ここを追加（ここから） */
            case ACCOUNTING_REVIEW -> {
                status = ReportStatus.DRAFT;
                print("差し戻しました");
            }
            /* ここを追加（ここまで） */
            case APPROVED -> print("承認済みのため差し戻せません");
        }
    }

    private void print(String message) {
        System.out.println("[経費申請] " + title + "：" + message + "（状態：" + status.getLabel() + "）");
    }
}
```

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        ExpenseReport report = new ExpenseReport("大阪出張の交通費");
        // 申請される前の経費申請を、上長が誤って承認しようとしている
        report.approve();
        // 申請者が申請している
        report.submit();
        // 上長が内容に不備を見つけたため、差し戻している
        report.sendBack();
        // 申請者が不備を直して、再度申請している
        report.submit();
        // 上長が承認している
        report.approve();
        /* ここを追加（ここから） */
        // 経理確認中の経費申請を、申請者が誤って再度申請しようとしている
        report.submit();
        // 経理部が領収書の金額との相違を見つけたため、差し戻している
        report.sendBack();
        // 申請者が金額を直して、再度申請している
        report.submit();
        // 上長が承認している
        report.approve();
        // 経理部が領収書の金額と照合したうえで承認している
        report.approve();
        /* ここを追加（ここまで） */
        // 承認済みの経費申請を、上長が誤って差し戻そうとしている
        report.sendBack();
    }
}
```

**実行結果**

```
[経費申請] 大阪出張の交通費：申請されていないため承認できません（状態：下書き）
[経費申請] 大阪出張の交通費：申請しました（状態：申請中）
[経費申請] 大阪出張の交通費：差し戻しました（状態：下書き）
[経費申請] 大阪出張の交通費：申請しました（状態：申請中）
[経費申請] 大阪出張の交通費：承認しました（状態：経理確認中）
[経費申請] 大阪出張の交通費：経理確認中のため申請できません（状態：経理確認中）
[経費申請] 大阪出張の交通費：差し戻しました（状態：下書き）
[経費申請] 大阪出張の交通費：申請しました（状態：申請中）
[経費申請] 大阪出張の交通費：承認しました（状態：経理確認中）
[経費申請] 大阪出張の交通費：承認しました（状態：承認済み）
[経費申請] 大阪出張の交通費：承認済みのため差し戻せません（状態：承認済み）
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 状態を 1 つ追加するたびに、`ExpenseReport` クラスの `submit`・`approve`・`sendBack` メソッドすべての `switch` 文に分岐を追加する必要がある。
    - もし、どれか 1 つのメソッドで分岐を追加し忘れると、コンパイルエラーも例外も発生しないまま、その状態のときだけ何も出力されずに処理が終わる不具合が生まれてしまう。
- 経理確認中の状態を追加するために、既存の状態の分岐が並ぶ `submit`・`approve`・`sendBack` メソッドの `switch` 文で、下書き・申請中・承認済みの `case` のすぐ隣に経理確認中の `case` を書き足す必要がある。特に、`approve` メソッドでは申請中の `case` の処理で、切り替え先の状態まで修正している。
    - その結果、すでにテストが完了している既存の状態の振る舞いまで再テストが必要になるうえ、作業中に既存の `case` の処理を誤って書き換えてしまうリスクもある。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **State パターン**です。<br>
State パターンは、オブジェクトの「状態」そのものを 1 つのクラスとして表し、オブジェクトの振る舞いを、そのときの状態に応じて変えられるようにするパターンです。本記事では、下書き・申請中・経理確認中・承認済みの 4 つの状態を、それぞれ 1 つのクラスとして表します。

まず、状態クラスに共通する操作を定義するインターフェースから見ていきましょう。

**`ReportState.java`**

```java
package example;

public interface ReportState {
    void submit(ExpenseReport report);

    void approve(ExpenseReport report);

    void sendBack(ExpenseReport report);

    String getLabel();
}
```

`ReportState` は新たに追加したインターフェースで、申請・承認・差し戻しの各操作に対応する抽象メソッド `submit`・`approve`・`sendBack` と、状態の表示名を返す抽象メソッド `getLabel` を持ちます。

次に、`ReportState` インターフェースを実装した状態ごとのクラスを見ていきましょう。

**`DraftState.java`**

```java
package example;

public class DraftState implements ReportState {
    @Override
    public void submit(ExpenseReport report) {
        report.changeState(new ManagerReviewState());
        report.print("申請しました");
    }

    @Override
    public void approve(ExpenseReport report) {
        report.print("申請されていないため承認できません");
    }

    @Override
    public void sendBack(ExpenseReport report) {
        report.print("申請されていないため差し戻せません");
    }

    @Override
    public String getLabel() {
        return "下書き";
    }
}
```

**`ManagerReviewState.java`**

```java
package example;

public class ManagerReviewState implements ReportState {
    @Override
    public void submit(ExpenseReport report) {
        report.print("すでに申請中です");
    }

    @Override
    public void approve(ExpenseReport report) {
        report.changeState(new AccountingReviewState());
        report.print("承認しました");
    }

    @Override
    public void sendBack(ExpenseReport report) {
        report.changeState(new DraftState());
        report.print("差し戻しました");
    }

    @Override
    public String getLabel() {
        return "申請中";
    }
}
```

**`AccountingReviewState.java`**

```java
package example;

public class AccountingReviewState implements ReportState {
    @Override
    public void submit(ExpenseReport report) {
        report.print("経理確認中のため申請できません");
    }

    @Override
    public void approve(ExpenseReport report) {
        report.changeState(new ApprovedState());
        report.print("承認しました");
    }

    @Override
    public void sendBack(ExpenseReport report) {
        report.changeState(new DraftState());
        report.print("差し戻しました");
    }

    @Override
    public String getLabel() {
        return "経理確認中";
    }
}
```

**`ApprovedState.java`**

```java
package example;

public class ApprovedState implements ReportState {
    @Override
    public void submit(ExpenseReport report) {
        report.print("承認済みのため申請できません");
    }

    @Override
    public void approve(ExpenseReport report) {
        report.print("すでに承認済みです");
    }

    @Override
    public void sendBack(ExpenseReport report) {
        report.print("承認済みのため差し戻せません");
    }

    @Override
    public String getLabel() {
        return "承認済み";
    }
}
```

`DraftState`・`ManagerReviewState`・`ApprovedState` の新たに追加した 3 つの状態クラスは、既存の `switch` 文で分岐の条件になっていた下書き・申請中・承認済みの状態を表すクラスです。<br>
また、`AccountingReviewState` クラスは、今回の要件で追加した経理確認中の状態を表すクラスです。

状態クラスでは、既存の `ExpenseReport` クラスの各メソッド（`submit`・`approve`・`sendBack`）の `switch` 文で分岐の中に書かれていた処理内容を、状態ごとに 1 つのクラスへまとめ直しています。経理確認中のときの処理内容も、`AccountingReviewState` クラス 1 つにまとまっています。<br>
そのため、状態クラスの各メソッドの処理内容も既存の `switch` 文の分岐の処理と同じく、操作を受け付けて状態を切り替えるか、操作を受け付けずに理由を出力するかのどちらかになっています。<br>
例えば、`DraftState` クラスの `submit` メソッドでは、引数で受け取った `ExpenseReport` クラスの `changeState` メソッドに `ManagerReviewState` クラスのインスタンスを渡すことにより、経費申請の状態を下書きから次の申請中へ切り替えています（→ [【深堀り②】状態クラスを Singleton パターンで使い回す](#深堀り2)）。一方、`approve`・`sendBack` メソッドでは、下書きの状態では操作を受け付けられないため、状態を切り替えずに `print` メソッドで理由を出力しています。

なお、`ManagerReviewState` クラスの `approve` メソッドでは、経理確認中の状態を追加したことに伴い、上長の承認後の切り替え先を承認済みから経理確認中（`AccountingReviewState` クラスのインスタンス）に変えています。

次に、状態クラスのインスタンス（以降、状態オブジェクト）を保持するクラスを見ていきましょう。

**`ExpenseReport.java`**

```java
package example;

public class ExpenseReport {
    private String title;
    private ReportState state = new DraftState();

    public ExpenseReport(String title) {
        this.title = title;
    }

    public void submit() {
        state.submit(this);
    }

    public void approve() {
        state.approve(this);
    }

    public void sendBack() {
        state.sendBack(this);
    }

    public void changeState(ReportState state) {
        this.state = state;
    }

    public void print(String message) {
        System.out.println("[経費申請] " + title + "：" + message + "（状態：" + state.getLabel() + "）");
    }
}
```

`ExpenseReport` クラスを振り返ると、既存コードの `ReportStatus` 型の `status` フィールドが、`ReportState` インターフェース型の `state` フィールドに置き換わり、現在の状態オブジェクトを保持しています。<br>
これにより、`submit`・`approve`・`sendBack` メソッドから `switch` 文がなくなり、`state` フィールドの状態オブジェクトの同名のメソッドを呼び出して、処理を任せています。その際に引数として渡している `this` は、`ExpenseReport` クラスのインスタンス自身、つまり操作の対象となっている経費申請そのものです。<br>
また、状態クラスが引数で受け取ったこの経費申請に対して結果の出力を依頼できるよう、`print` メソッドを `public` にしています。<br>
さらに、状態クラスが決めた次の状態への切り替えを依頼できるよう、`changeState` メソッドを追加しています（→ [【深堀り①】次の状態を誰が決めるか](#深堀り1)）。

なお、状態を表す列挙型 `ReportStatus` は、状態クラスに置き換わったため不要になります。

最後に、実行クラスを見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        ExpenseReport report = new ExpenseReport("大阪出張の交通費");
        // 申請される前の経費申請を、上長が誤って承認しようとしている
        report.approve();
        // 申請者が申請している
        report.submit();
        // 上長が内容に不備を見つけたため、差し戻している
        report.sendBack();
        // 申請者が不備を直して、再度申請している
        report.submit();
        // 上長が承認している
        report.approve();
        /* ここを追加（ここから） */
        // 経理確認中の経費申請を、申請者が誤って再度申請しようとしている
        report.submit();
        // 経理部が領収書の金額との相違を見つけたため、差し戻している
        report.sendBack();
        // 申請者が金額を直して、再度申請している
        report.submit();
        // 上長が承認している
        report.approve();
        // 経理部が領収書の金額と照合したうえで承認している
        report.approve();
        /* ここを追加（ここまで） */
        // 承認済みの経費申請を、上長が誤って差し戻そうとしている
        report.sendBack();
    }
}
```

**実行結果**

```
[経費申請] 大阪出張の交通費：申請されていないため承認できません（状態：下書き）
[経費申請] 大阪出張の交通費：申請しました（状態：申請中）
[経費申請] 大阪出張の交通費：差し戻しました（状態：下書き）
[経費申請] 大阪出張の交通費：申請しました（状態：申請中）
[経費申請] 大阪出張の交通費：承認しました（状態：経理確認中）
[経費申請] 大阪出張の交通費：経理確認中のため申請できません（状態：経理確認中）
[経費申請] 大阪出張の交通費：差し戻しました（状態：下書き）
[経費申請] 大阪出張の交通費：申請しました（状態：申請中）
[経費申請] 大阪出張の交通費：承認しました（状態：経理確認中）
[経費申請] 大阪出張の交通費：承認しました（状態：承認済み）
[経費申請] 大阪出張の交通費：承認済みのため差し戻せません（状態：承認済み）
```

`Main` クラスは好ましくない実装と同じです。

実行結果は、好ましくない実装とまったく同じになっています。

以上のような実装を行うと、以下のメリットがあります。

- `ExpenseReport` クラスは、`submit`・`approve`・`sendBack` メソッドの処理を、`ReportState` 型の `state` フィールドで保持している状態オブジェクトに任せている。また、`ReportState` インターフェースは、状態ごとに実装すべき操作を抽象メソッド `submit`・`approve`・`sendBack` として定義している。状態クラスはこのインターフェースを実装しているので、`ExpenseReport` クラスはどの状態クラスにも同じメソッドで処理を任せられる。そのうえ、新しい状態クラスでもこれらのメソッドの実装が強制され、実装し忘れるとコンパイルエラーになる。
    - その結果、特定の状態のときだけ何も出力されないまま処理が終わる不具合を、実行する前に防げる。
- 下書き・申請中・経理確認中・承認済みの振る舞いが、それぞれ `DraftState`・`ManagerReviewState`・`AccountingReviewState`・`ApprovedState` クラスに分かれているため、状態を追加しても、既存の状態クラスでは切り替え先を変える以外に手を入れずに済む。
    - 例えば、上長の承認後に「部長確認中」の状態を追加する場合、`ReportState` インターフェースを実装した新しい状態クラスを作成したうえで、既存の `ManagerReviewState` クラスの `approve` メソッドの切り替え先をその状態クラスに変えるだけで済む。
    - その結果、テストの範囲を、新しく作成した状態クラスと、切り替え先を変えた `ManagerReviewState` クラスの `approve` メソッドに絞り込めるうえ、既存の状態の振る舞いを誤って書き換えてしまうリスクも減る。

## まとめ

正しい実装を振り返ると、既存コードの列挙型 `ReportStatus` の値で表していた状態は、それぞれ 1 つの状態クラスになり、各状態のときに `submit`・`approve`・`sendBack` メソッドで何をするかも、その状態クラスに書かれています。`ExpenseReport` クラスは現在の状態を条件分岐で判定せず、保持している状態オブジェクトに処理を任せ、状態が変わるときは状態クラスからの依頼を受けて、別の状態オブジェクトに差し替えるだけでよくなっています。<br>
このように State パターンは、「状態」そのものを 1 つのクラスとして表し、状態オブジェクトを差し替えることで、同じ操作でも状態に応じて振る舞いを切り替えられるようにする設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】次の状態を誰が決めるか

正しい実装の `DraftState`・`ManagerReviewState`・`AccountingReviewState` クラスを振り返ると、状態クラス自身が `changeState` メソッドを呼び出し、次の状態を決めています。<br>
そのため、例えば `DraftState` クラスは、申請されたら次は申請中（`ManagerReviewState` クラス）の状態に切り替えることを知っている必要があります。

この実装方法には、状態クラスを読むだけで次にどの状態に進むかが分かるというメリットがあります。<br>
一方で、状態クラス同士が互いのクラス名を知っていて結びつきが強い（密結合）ため、状態を新しく追加する際は、その状態への切り替え元となる状態クラスを必ず修正する必要があるというデメリットがあります。<br>
また、状態オブジェクトを保持する `ExpenseReport` クラスでは、状態クラスから `changeState` メソッドを呼び出す必要があるため、アクセス修飾子を `private` にすることができません。そのため、`Main` クラスなどの外部のコードからも `changeState` メソッドを呼び出せてしまうというデメリットがあります。その結果、承認前の状態であっても、`report.changeState(new ApprovedState())` のように `submit`・`approve`・`sendBack` メソッドを経由せずに、承認済みに切り替えられてしまうリスクがあります。

これらのデメリットを解消する方法の 1 つに、状態クラスは「その状態のときに操作を受け付けるかどうか」の判断だけを担当し、次の状態の決定は `ExpenseReport` クラス側で行う実装方法があります。

本記事のコードをこの実装方法で書き換えると、次のようになります。

**`ReportState.java`**

```java
package example;

public interface ReportState {
    boolean submit(ExpenseReport report);

    boolean approve(ExpenseReport report);

    boolean sendBack(ExpenseReport report);

    String getLabel();
}
```

`ReportState` インターフェースを振り返ると、操作を受け付けたかどうかを `ExpenseReport` クラスに返せるよう、抽象メソッド `submit`・`approve`・`sendBack` の戻り値を、正しい実装の `void` から `boolean` に変えています。

**`DraftState.java`**

```java
package example;

public class DraftState implements ReportState {
    @Override
    public boolean submit(ExpenseReport report) {
        return true;
    }

    @Override
    public boolean approve(ExpenseReport report) {
        report.print("申請されていないため承認できません");
        return false;
    }

    @Override
    public boolean sendBack(ExpenseReport report) {
        report.print("申請されていないため差し戻せません");
        return false;
    }

    @Override
    public String getLabel() {
        return "下書き";
    }
}
```

**`ManagerReviewState.java`**

```java
package example;

public class ManagerReviewState implements ReportState {
    @Override
    public boolean submit(ExpenseReport report) {
        report.print("すでに申請中です");
        return false;
    }

    @Override
    public boolean approve(ExpenseReport report) {
        return true;
    }

    @Override
    public boolean sendBack(ExpenseReport report) {
        return true;
    }

    @Override
    public String getLabel() {
        return "申請中";
    }
}
```

**`AccountingReviewState.java`**

```java
package example;

public class AccountingReviewState implements ReportState {
    @Override
    public boolean submit(ExpenseReport report) {
        report.print("経理確認中のため申請できません");
        return false;
    }

    @Override
    public boolean approve(ExpenseReport report) {
        return true;
    }

    @Override
    public boolean sendBack(ExpenseReport report) {
        return true;
    }

    @Override
    public String getLabel() {
        return "経理確認中";
    }
}
```

**`ApprovedState.java`**

```java
package example;

public class ApprovedState implements ReportState {
    @Override
    public boolean submit(ExpenseReport report) {
        report.print("承認済みのため申請できません");
        return false;
    }

    @Override
    public boolean approve(ExpenseReport report) {
        report.print("すでに承認済みです");
        return false;
    }

    @Override
    public boolean sendBack(ExpenseReport report) {
        report.print("承認済みのため差し戻せません");
        return false;
    }

    @Override
    public String getLabel() {
        return "承認済み";
    }
}
```

`DraftState`・`ManagerReviewState`・`AccountingReviewState`・`ApprovedState` の 4 つの状態クラスを振り返ると、操作を受け付けない場合は `print` メソッドで理由を出力して `false` を返し、受け付ける場合は `true` を返すだけになっています。<br>
また、次の状態の決定を `ExpenseReport` クラスに任せたため、正しい実装にあった、次の状態オブジェクトを生成して `changeState` メソッドに渡す処理を削除しています。

**`ExpenseReport.java`**

```java
package example;

public class ExpenseReport {
    private String title;
    private ReportState state = new DraftState();

    public ExpenseReport(String title) {
        this.title = title;
    }

    public void submit() {
        if (!state.submit(this)) {
            return;
        }
        state = new ManagerReviewState();
        print("申請しました");
    }

    public void approve() {
        if (!state.approve(this)) {
            return;
        }
        if (state instanceof ManagerReviewState) {
            state = new AccountingReviewState();
        } else if (state instanceof AccountingReviewState) {
            state = new ApprovedState();
        }
        print("承認しました");
    }

    public void sendBack() {
        if (!state.sendBack(this)) {
            return;
        }
        state = new DraftState();
        print("差し戻しました");
    }

    public void print(String message) {
        System.out.println("[経費申請] " + title + "：" + message + "（状態：" + state.getLabel() + "）");
    }
}
```

`ExpenseReport` クラスを振り返ると、状態クラスから状態の切り替えを依頼されることがなくなったため、正しい実装から `changeState` メソッドを削除しています。<br>
また、`submit`・`approve`・`sendBack` メソッドでは、状態オブジェクトから `true` が返ってきた場合（操作を受け付けた場合）にだけ、次の状態オブジェクトを `state` フィールドに代入して状態を切り替え、`print` メソッドで結果を出力しています。なお、状態を切り替えてから `print` メソッドを呼び出しているため、出力される状態の表示名は、`state` フィールドに代入された次の状態オブジェクトの `getLabel` メソッドが返す表示名になります。

`Main` クラスに変更はなく、実行結果も正しい実装とまったく同じになります。

この実装方法では、`changeState` メソッドを削除したことで、外部のコードが `submit`・`approve`・`sendBack` メソッドを経由せずに、状態を切り替えることはできなくなるというメリットがあります。<br>
また、状態クラス同士が互いのクラス名を知らずに済み、結びつきが弱い（疎結合）ため、状態を新しく追加する際は、既存の状態クラスを修正する必要がないというメリットがあります。<br>
さらに、次の状態の決定が `ExpenseReport` クラスに集まるため、`ExpenseReport` クラスを読むだけで、すべての状態の切り替えのルールを見渡すことができるというメリットがあります。<br>
一方で、状態の切り替えのルールが `ExpenseReport` クラスに集まるため、状態を新しく追加する際は、`approve` メソッドのように、その状態への切り替え元となる状態の条件分岐を必ず書き足す必要があるというデメリットがあります。

以上から、状態ごとの振る舞いと切り替え先をまとめて管理したい場合は状態クラス側で次の状態を決める実装方法、状態の数が少なく切り替えのルールを一覧で見渡したい場合は状態オブジェクトを保持するクラス側で次の状態を決める実装方法、というように使い分けます。

<a id="深堀り2"></a>

## 【深堀り②】状態クラスを Singleton パターンで使い回す

正しい実装の `DraftState`・`ManagerReviewState`・`AccountingReviewState` クラスを振り返ると、状態を切り替えるたびに新しいインスタンスを生成しています。<br>
例えば、`DraftState` クラスの `submit` メソッドでは、`report.changeState(new ManagerReviewState());` のように `ManagerReviewState` クラスのインスタンスを生成しています。<br>
しかし、今回の状態クラスはフィールドを持たず、どのインスタンスでも振る舞いが同じなので、切り替えのたびに新しいインスタンスを生成するのは無駄です。そのため実務では、State パターンを Singleton パターンと組み合わせて実装することがよくあります。

では、正しい実装に Singleton パターンを適用した場合の実装を見ていきましょう。

**`DraftState.java`（一部抜粋）**

```java
package example;

public class DraftState implements ReportState {
    /* ここを追加（ここから） */
    private static DraftState instance = new DraftState();

    private DraftState() {
    }

    public static DraftState getInstance() {
        return instance;
    }
    /* ここを追加（ここまで） */

    @Override
    public void submit(ExpenseReport report) {
        report.changeState(ManagerReviewState.getInstance()); // ←ここを修正
        report.print("申請しました");
    }
}
```

※`approve`・`sendBack`・`getLabel` メソッドは変更がないため省略しています。

**`ManagerReviewState.java`（一部抜粋）**

```java
package example;

public class ManagerReviewState implements ReportState {
    /* ここを追加（ここから） */
    private static ManagerReviewState instance = new ManagerReviewState();

    private ManagerReviewState() {
    }

    public static ManagerReviewState getInstance() {
        return instance;
    }
    /* ここを追加（ここまで） */

    @Override
    public void approve(ExpenseReport report) {
        report.changeState(AccountingReviewState.getInstance()); // ←ここを修正
        report.print("承認しました");
    }

    @Override
    public void sendBack(ExpenseReport report) {
        report.changeState(DraftState.getInstance()); // ←ここを修正
        report.print("差し戻しました");
    }
}
```

※`submit`・`getLabel` メソッドは変更がないため省略しています。

**`AccountingReviewState.java`（一部抜粋）**

```java
package example;

public class AccountingReviewState implements ReportState {
    /* ここを追加（ここから） */
    private static AccountingReviewState instance = new AccountingReviewState();

    private AccountingReviewState() {
    }

    public static AccountingReviewState getInstance() {
        return instance;
    }
    /* ここを追加（ここまで） */

    @Override
    public void approve(ExpenseReport report) {
        report.changeState(ApprovedState.getInstance()); // ←ここを修正
        report.print("承認しました");
    }

    @Override
    public void sendBack(ExpenseReport report) {
        report.changeState(DraftState.getInstance()); // ←ここを修正
        report.print("差し戻しました");
    }
}
```

※`submit`・`getLabel` メソッドは変更がないため省略しています。

**`ApprovedState.java`（一部抜粋）**

```java
package example;

public class ApprovedState implements ReportState {
    /* ここを追加（ここから） */
    private static ApprovedState instance = new ApprovedState();

    private ApprovedState() {
    }

    public static ApprovedState getInstance() {
        return instance;
    }
    /* ここを追加（ここまで） */
}
```

※`submit`・`approve`・`sendBack`・`getLabel` メソッドは変更がないため省略しています。

`DraftState`・`ManagerReviewState`・`AccountingReviewState`・`ApprovedState` クラスを振り返ると、正しい実装から、自分のクラスのインスタンスを代入した `static` な `instance` フィールドと、アクセス修飾子を `private` にしたコンストラクター、`static` な `getInstance` メソッドを追加しています。<br>
また、状態を切り替えるメソッドでは、`new` で次の状態オブジェクトを生成する代わりに、次の状態クラスの `getInstance` メソッドが返すインスタンスを `changeState` メソッドに渡しています。

追加したコンストラクター・フィールド・メソッドに `private`・`static` を付けているのには、それぞれ理由があります。<br>
コンストラクターを `private` にしているのは、インスタンスを 2 つ以上生成させないためです。これにより、クラスの外から `new` でインスタンスを生成できなくなり、インスタンスは `getInstance` メソッドを通してのみ取得できるようになります。<br>
また、`instance` フィールドを `static` にしているのは、インスタンスごとではなくクラスに 1 つだけ持たせ、クラスが初めて使われたときに 1 回だけインスタンスを生成するためです。<br>
さらに、`getInstance` メソッドを `static` にしているのは、クラスの外ではインスタンスを生成できないため、インスタンスがなくても `DraftState.getInstance()` のようにクラス名から呼び出せるようにするためです。<br>
このように、そのクラスのインスタンスが 1 つしか存在しないことを保証する設計を Singleton パターンといいます。

**`ExpenseReport.java`（一部抜粋）**

```java
package example;

public class ExpenseReport {
    private String title;
    private ReportState state = DraftState.getInstance(); // ←ここを修正
}
```

※コンストラクターと各メソッドは変更がないため省略しています。

`ExpenseReport` クラスを振り返ると、`DraftState` クラスのコンストラクターを `private` にしたため、`state` フィールドの初期値を `new DraftState()` から `DraftState.getInstance()` に変えています。

`Main` クラスに変更はなく、実行結果も正しい実装とまったく同じになります。

この実装方法には、経費申請が何万件あっても、状態クラスのインスタンスは状態クラスの数だけで済むため、無駄なインスタンスの生成を避けられるというメリットがあります。<br>
一方で、1 つのインスタンスをすべての経費申請で共有するため、状態クラスに経費申請ごとの値（例えば、差し戻した理由）をフィールドとして持たせることができないというデメリットがあります。

例として、差し戻した理由を `DraftState` クラスの通常のフィールド（`static` を付けないフィールド）に持たせた場合を考えてみましょう。<br>
この場合でも、`DraftState` クラスのインスタンスは 1 つしかないため、下書きの状態にあるすべての経費申請が、そのフィールドを共有することになります。<br>
「大阪出張の交通費」を「領収書が添付されていない」という理由で差し戻した後に、「名古屋出張の宿泊費」を「金額が間違っている」という理由で差し戻したとします。このとき、「大阪出張の交通費」の差し戻した理由まで「金額が間違っている」に上書きされてしまいます。

Singleton パターンと組み合わせるかどうかは、状態クラスに、状態オブジェクトを保持するクラスのインスタンスごとに異なる値（以降、個別の値）を持たせるかどうかで判断するとよいでしょう。<br>
個別の値を持たず振る舞いだけを持つ場合は、Singleton パターンと組み合わせるのが向いています。<br>
反対に、個別の値を持たせる場合は、正しい実装のように、切り替えのたびに新しいインスタンスを生成する実装にするのが適切です。

<a id="深堀り3"></a>

## 【深堀り③】State パターンが苦手な変更

正しい実装を振り返ると、例えば上長の承認後に「部長確認中」の状態を追加する場合、`ReportState` インターフェースを実装した新しい状態クラスを作成し、既存の `ManagerReviewState` クラスの `approve` メソッドの切り替え先を、新しく作成した状態クラスに変えるだけで済みます。<br>
つまり、正しい実装のコードのうち、手を加える必要があるのは `ManagerReviewState` クラスだけとなります。

一方で、申請者が自分で申請を取り下げる `withdraw` という操作を追加する場合、`ReportState` インターフェースに抽象メソッド `withdraw` を追加し、4 つの状態クラスすべてに `withdraw` メソッドを実装することになります。また、`ExpenseReport` クラスにも、状態オブジェクトに処理を任せる `withdraw` メソッドを追加する必要があります。<br>
つまり、正しい実装のコードのうち、`Main` クラス以外のすべてに手を加える必要があります。

このように State パターンは、状態の追加には強い一方、操作の追加には弱いという特徴があります。

ただし、状態を追加する場合と同じく、状態クラスで `withdraw` メソッドを実装し忘れるとコンパイルエラーになります。<br>
そのため、既存コードの仕様や好ましくない実装の `switch` 文とは異なり、修正箇所が多くても追加漏れに気づけます。

State パターンを使うかどうかは、今後、状態と操作のどちらが増えやすいかで判断するとよいでしょう。<br>
本記事の経費申請では、承認の段階が増える可能性は高いですが、操作の種類（申請・承認・差し戻し）が増える可能性は低いため、State パターンが向いています。<br>
反対に、操作のほうが増えやすい場合は、既存コードの仕様のように `switch` 文のままにするか、Visitor パターンを検討するのが適切です。<br>
詳しくは「Visitor パターン」で検索してみてください。

<a id="深堀り4"></a>

## 【深堀り④】Strategy パターンとの違い

正しい実装の `ExpenseReport` クラスを振り返ると、`submit`・`approve`・`sendBack` メソッドでは、`state` フィールドに保持している状態オブジェクトに処理を任せています。<br>
この「共通のインターフェースを実装したクラスを保持し、処理を任せる」というクラス構成は、Strategy パターンとよく似ています。

ただし、2 つのパターンでは、保持しているオブジェクトを差し替えるのが誰かが異なります。

State パターンでは、状態クラス自身（または状態オブジェクトを保持するクラス）が、状態を 1 つのクラスとして表したオブジェクトを差し替えます（→ [【深堀り①】次の状態を誰が決めるか](#深堀り1)）。<br>
例えば、上長が承認したときは、`ManagerReviewState` クラスの `approve` メソッドが `changeState` メソッドを呼び出し、経理確認中の状態オブジェクトに差し替えています。

一方、Strategy パターンでは、利用する側（`Main` クラスなど）が、処理のやり方を 1 つのクラスとして表したオブジェクトを差し替えます。<br>
例えば、経路探索の「最短距離優先」と「最短時間優先」をそれぞれクラスとして用意した場合は、`Main` クラスがどちらを使うかを選び、そのオブジェクトに差し替えます。

このように 2 つのパターンは、クラス構成が似ていても、差し替えに関わるクラスが正反対です。<br>
State パターンでは、`Main` クラスは `approve` メソッドを呼び出すだけで、状態オブジェクトを差し替えるのは状態クラスです。<br>
反対に、Strategy パターンでは、探索方針のクラスは経路を探索するだけで、探索方針のオブジェクトを差し替えるのは `Main` クラスです。

どちらのパターンを使うかは、オブジェクトを差し替えるきっかけが、操作の結果なのか、利用する側の選択なのかで判断するとよいでしょう。<br>
本記事の経費申請では、承認や差し戻しの操作の結果として状態が切り替わるため、State パターンが向いています。<br>
反対に、利用する側が場面に応じて処理のやり方を選ぶ場合は、Strategy パターンを検討するのが適切です。<br>
詳しくは「Strategy パターン」で検索してみてください。

<a id="深堀り5"></a>

## 【深堀り⑤】GoF デザインパターンとの位置づけ

今回使った State パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
