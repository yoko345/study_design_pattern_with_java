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
- [【深堀り①】状態の切り替えを誰が決めるか](#深堀り1)
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
> そこであなたは、上長の承認後に経理部の確認を挟む「経理確認中」の状態を追加することになりました。

※実際の経費申請では、申請内容や状態を DB に保存したり、申請者・承認者へ通知したりする実装が必要ですが、本記事では State パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

- `ReportStatus`（既存クラス）

経費申請の状態を表す列挙型です。<br>
状態ごとに、コンソールに出力する表示名を保持します。

| 定数        | 表示名      |
| ----------- | ----------- |
| `DRAFT`     | ` 下書き`   |
| `SUBMITTED` | ` 申請中`   |
| `APPROVED`  | ` 承認済み` |

| メソッド   | 引数 | 戻り値の型 | 説明             |
| ---------- | ---- | ---------- | ---------------- |
| `getLabel` | なし | `String`   | 表示名を取得する |

**`ReportStatus.java`**

```java
package example;

public enum ReportStatus {
    DRAFT("下書き"),
    SUBMITTED("申請中"),
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

※実務では、次の `ExpenseReport` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `ExpenseReport`（既存クラス）

経費申請 1 件を表すクラスです。<br>
申請・承認・差し戻しの各操作で、現在の状態に応じて状態を切り替えるか、操作を受け付けられない旨を出力します。

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
                status = ReportStatus.SUBMITTED;
                print("申請しました");
            }
            case SUBMITTED -> print("すでに申請中です");
            case APPROVED -> print("承認済みのため申請できません");
        }
    }

    public void approve() {
        switch (status) {
            case DRAFT -> print("申請されていないため承認できません");
            case SUBMITTED -> {
                status = ReportStatus.APPROVED;
                print("承認しました");
            }
            case APPROVED -> print("すでに承認済みです");
        }
    }

    public void sendBack() {
        switch (status) {
            case DRAFT -> print("申請されていないため差し戻せません");
            case SUBMITTED -> {
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

真っ先に思いつくのは、`ReportStatus` に「経理確認中」の定数を追加し、`ExpenseReport` クラスの各メソッドの `switch` 文に、既存の状態と同じように分岐を書き足す、という実装ではないでしょうか？

**`ReportStatus.java`**

```java
package example;

public enum ReportStatus {
    DRAFT("下書き"),
    SUBMITTED("申請中"),
    /* ここを追加（ここから） */
    ACCOUNTING_REVIEW("経理確認中"),
    /* ここを追加（ここまで） */
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
                status = ReportStatus.SUBMITTED;
                print("申請しました");
            }
            case SUBMITTED -> print("すでに申請中です");
            /* ここを追加（ここから） */
            case ACCOUNTING_REVIEW -> print("経理確認中のため申請できません");
            /* ここを追加（ここまで） */
            case APPROVED -> print("承認済みのため申請できません");
        }
    }

    public void approve() {
        switch (status) {
            case DRAFT -> print("申請されていないため承認できません");
            case SUBMITTED -> {
                /* ここを修正（ここから） */
                status = ReportStatus.ACCOUNTING_REVIEW;
                /* ここを修正（ここまで） */
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
            case SUBMITTED -> {
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
[経費申請] 大阪出張の交通費：承認しました（状態：承認済み）
[経費申請] 大阪出張の交通費：承認済みのため差し戻せません（状態：承認済み）
```

上長の承認後に「経理確認中」を経由し、経理部の承認で「承認済み」になっています。

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 状態を 1 つ追加するたびに、`submit`・`approve`・`sendBack` のすべてのメソッドの `switch` 文に分岐を書き足さなければならない。
    - しかも、どれか 1 つのメソッドで分岐を追加し忘れても、コンパイルエラーにならない。例えば `submit` メソッドへの分岐の追加を忘れると、経理確認中の申請を再度申請しようとしたときに、何も出力されないまま処理が終わってしまい、不具合に気づきにくい。
- 「経理確認中のときに、どの操作を受け付け、次にどの状態へ切り替えるか」というルールが、3 つのメソッドに分散している。
    - その結果、経理部から「経理確認中のときは何ができるのか」と問い合わせを受けた場合や、経理確認中のルールを変更する場合に、すべてのメソッドを読んで経理確認中の分岐を探さなければならない。
- 経理確認中の分岐を書き足すために、下書き・申請中・承認済みの分岐も並んでいるメソッドに手を入れることになる。
    - その結果、既存の状態の分岐を誤って書き換えてしまうリスクがあり、すでにテストが完了している既存の状態の振る舞いまで再テストが必要になってしまう。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **State パターン**です。<br>
State パターンでは、「状態」そのものを 1 つのクラスとして表し、その状態のときに各操作で何をするかを、状態ごとのクラスに書きます。状態を持つ側のクラスは、現在の状態を表すオブジェクトを 1 つ保持し、操作を受けたらその状態オブジェクトに処理を任せるだけにします。状態が変わるときは、保持している状態オブジェクトを別の状態クラスのインスタンスに差し替えます。

これにより、操作ごとに状態を条件分岐で判定する必要がなくなります。また、すべての状態クラスに共通のインターフェースを実装させることで、状態クラスを追加する際に、どれかの操作の実装を忘れるとコンパイルエラーで気づけるようになります。

今回の実装は、次のクラスで構成します。

- 状態クラスに共通する操作を定義するインターフェース（`ReportState`）
- 状態ごとのクラス（`DraftState`・`SubmittedState`・`AccountingReviewState`・`ApprovedState`）
- 現在の状態オブジェクトを保持し、操作を任せるクラス（`ExpenseReport`）

まず、状態クラスに共通する振る舞いを定義するインターフェースから見ていきましょう。

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

`ReportState` は新たに追加したインターフェースで、申請・承認・差し戻しの各操作に対応する抽象メソッド `submit`・`approve`・`sendBack` と、状態の表示名を返す抽象メソッド `getLabel` を持ちます。<br>
各操作の抽象メソッドの引数で `ExpenseReport` クラスのインスタンスを受け取ることにより、状態クラス側から状態の切り替えや結果の出力を依頼できるようにしています。

次に、`ReportState` インターフェースを実装した状態ごとのクラスを見ていきましょう。

**`DraftState.java`**

```java
package example;

public class DraftState implements ReportState {
    @Override
    public void submit(ExpenseReport report) {
        report.changeState(new SubmittedState());
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

**`SubmittedState.java`**

```java
package example;

public class SubmittedState implements ReportState {
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

4 つの状態クラスは、既存の `ExpenseReport` クラスの `switch` 文に書かれていた分岐を、状態ごとに 1 つのクラスへまとめ直したものです。<br>
状態を切り替える操作では、`ExpenseReport` クラスの `changeState` メソッドに次の状態クラスのインスタンスを渡すことにより、状態の切り替えを依頼しています。操作を受け付けられない場合は、状態を切り替えずに `print` メソッドで理由を出力しています。

このうち `AccountingReviewState` は、今回の要件のために新たに追加したクラスです。経理確認中のときに受け付ける操作と、次に切り替える状態が、このクラス 1 つにまとまっています。<br>
また、`SubmittedState` クラスの `approve` メソッドでは、上長の承認後の切り替え先を `AccountingReviewState` クラスにしています。

次に、状態オブジェクトを保持するクラスを見ていきましょう。

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

`ExpenseReport` クラスを振り返ると、既存の仕様から次の点が変わっています。

- `ReportStatus` 型の `status` フィールドがなくなり、代わりに `ReportState` インターフェース型の `state` フィールドで現在の状態オブジェクトを保持している。
- `submit`・`approve`・`sendBack` メソッドから `switch` 文がなくなり、`state` フィールドの同名のメソッドに自分自身（`this`）を渡して呼び出すことにより、処理を現在の状態オブジェクトに任せている。
- 状態クラスから状態の切り替えを依頼するための `changeState` メソッドが新たに追加されている。
- 状態クラスから結果の出力を依頼できるよう、`print` メソッドを `public` にしている。

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
        report.submit();
        // 上長が内容に不備を見つけたため、差し戻している
        report.sendBack();
        // 申請者が不備を直して、再度申請している
        report.submit();
        // 上長が承認している
        report.approve();
        // 経理確認中の経費申請を、申請者が誤って再度申請しようとしている
        report.submit();
        // 経理部が領収書の金額と照合したうえで承認している
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
[経費申請] 大阪出張の交通費：承認しました（状態：経理確認中）
[経費申請] 大阪出張の交通費：経理確認中のため申請できません（状態：経理確認中）
[経費申請] 大阪出張の交通費：承認しました（状態：承認済み）
[経費申請] 大阪出張の交通費：承認済みのため差し戻せません（状態：承認済み）
```

`Main` クラスは好ましくない実装から変更がなく、実行結果も好ましくない実装とまったく同じになっています。

以上のような実装を行うと、以下のメリットがあります。

- 状態を追加する場合、`ReportState` インターフェースを実装した状態クラスを新たに 1 つ作成し、その状態へ切り替える側の状態クラスの切り替え先を変更するだけでよい。
    - しかも、新しい状態クラスで `submit`・`approve`・`sendBack` メソッドのどれかを実装し忘れると、コンパイルエラーになる。その結果、特定の状態のときだけ何も出力されないまま処理が終わる不具合を、実行する前に防げる。
- 「経理確認中のときに、どの操作を受け付け、次にどの状態へ切り替えるか」というルールが、`AccountingReviewState` クラス 1 つにまとまっている。
    - その結果、経理確認中のルールを確認・変更する場合は、`AccountingReviewState` クラスだけを読めばよい。
- 経理確認中の状態を追加しても、`DraftState`・`ApprovedState` クラスには手が入らず、`SubmittedState` クラスも `approve` メソッドの切り替え先を変更するだけで済む。
    - その結果、既存の状態の振る舞いを誤って書き換えてしまうリスクが減り、再テストの範囲も、切り替え先を変更した `SubmittedState` クラスの `approve` メソッドに絞り込める。

## まとめ

正しい実装を振り返ると、`ExpenseReport` クラスは現在の状態を条件分岐で判定せず、保持している状態オブジェクトに処理を任せるだけでよくなっています。<br>
このように State パターンは、状態ごとの振る舞いをそれぞれのクラスに分け、状態オブジェクトを差し替えることで、同じ操作でも状態に応じて振る舞いを切り替えられるようにする設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】状態の切り替えを誰が決めるか

正しい実装では、状態クラス自身が `changeState` メソッドを呼び出し、次の状態を決めています。例えば `SubmittedState` クラスは、承認されたら `AccountingReviewState` クラスに切り替えることを知っています。

この方式は、ある状態から次にどの状態へ進むかが、その状態クラスを読めば分かるという利点があります。一方で、状態クラス同士が互いのクラス名を知っているため、今回の `SubmittedState` クラスのように、状態の追加に伴って切り替え元の状態クラスを修正する必要が出てきます。

また、状態クラスから切り替えを依頼できるよう `changeState` メソッドを `public` にしているため、`Main` クラスなどの外部のコードからも呼び出せます。その結果、`report.changeState(new ApprovedState())` のように、`submit`・`approve`・`sendBack` メソッドを経由せずに、経理確認を飛ばして承認済みに切り替えられてしまうリスクがあります。

これに対して、状態クラスは「その状態のときに操作を受け付けるかどうか」の判断だけを担当し、次の状態の決定は `ExpenseReport` クラス側で行う方式もあります。

本記事のコードをこの方式で実装すると、次のようになります。

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

抽象メソッド `submit`・`approve`・`sendBack` の戻り値を `boolean` に変え、操作を受け付けるかどうかを `ExpenseReport` クラスに返すようにしています。

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

**`SubmittedState.java`**

```java
package example;

public class SubmittedState implements ReportState {
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

4 つの状態クラスは、操作を受け付けない場合は `print` メソッドで理由を出力して `false` を返し、受け付ける場合は `true` を返すだけになっています。`changeState` メソッドを呼び出さないため、どの状態クラスも他の状態クラスを知りません。

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
        state = new SubmittedState();
        print("申請しました");
    }

    public void approve() {
        if (!state.approve(this)) {
            return;
        }
        if (state instanceof SubmittedState) {
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

`ExpenseReport` クラスでは、状態オブジェクトから `true` が返ってきた場合にだけ、次の状態を決めて切り替えたうえで結果を出力しています。状態を切り替えてから出力することにより、切り替え後の状態名を出力しています。<br>
なお、状態クラスから切り替えを依頼されることがなくなったため、`changeState` メソッドは不要になっています。その結果、外部のコードが操作を経由せずに状態を切り替えることもできなくなります。

`Main` クラスに変更はなく、実行結果も正しい実装とまったく同じになります。

この方式では状態クラス同士は互いを知らずに済みますが、`approve` メソッドのように、状態の切り替えのルールが `ExpenseReport` クラスに条件分岐として集まるため、状態が増えるたびにこの分岐を書き足すことになります。

どちらの方式が優れているというわけではありません。状態の数が少なく切り替えのルールを一覧で見渡したい場合は `ExpenseReport` クラス側で決める方式、状態ごとの振る舞いと切り替え先をまとめて管理したい場合は状態クラス側で決める方式、というように使い分けます。

<a id="深堀り2"></a>

## 【深堀り②】状態クラスを Singleton パターンで使い回す

正しい実装では、状態を切り替えるたびに `new SubmittedState()` のように新しいインスタンスを生成しています。<br>
しかし、今回の状態クラスはフィールドを持たず、どのインスタンスでも振る舞いが同じです。そのため、State パターンは Singleton パターンと組み合わせ、状態ごとにインスタンスを 1 つだけ用意して使い回す形で実装されることもよくあります。

**`SubmittedState.java`（一部抜粋）**

```java
public class SubmittedState implements ReportState {
    private static SubmittedState instance = new SubmittedState();

    private SubmittedState() {
    }

    public static SubmittedState getInstance() {
        return instance;
    }

    @Override
    public void sendBack(ExpenseReport report) {
        report.changeState(DraftState.getInstance());
        report.print("差し戻しました");
    }
}
```

コンストラクターを `private` にして外部からの生成を禁止し、`getInstance` メソッドを通して唯一のインスタンスを使わせることで、Singleton パターンを適用しています。経費申請が何万件あっても状態クラスのインスタンスは状態の数だけで済むため、無駄なインスタンスの生成を避けられます。

ただし、状態クラスに経費申請ごとの値（例えば、差し戻した理由）をフィールドとして持たせる場合は、インスタンスを使い回すと別の経費申請と値を共有してしまいます。その場合は、正しい実装のように切り替えのたびに新しいインスタンスを生成します。

<a id="深堀り3"></a>

## 【深堀り③】State パターンが苦手な変更

State パターンは状態の追加には強い一方、操作の追加には弱いという特徴があります。

例えば、申請者が自分で申請を取り下げる `withdraw` という操作を追加する場合、`ReportState` インターフェースに抽象メソッド `withdraw` を追加し、4 つの状態クラスすべてに `withdraw` メソッドを実装することになります。好ましくない実装で状態を追加したときに全メソッドの `switch` 文を修正したのと同じく、変更が全クラスに広がります。

ただし、`switch` 文とは異なり、`withdraw` メソッドを実装し忘れた状態クラスはコンパイルエラーになります。そのため、修正箇所が多くても追加漏れには気づけます。

状態と操作のどちらが増えやすいかを見極め、状態が増えやすい場合に State パターンを選ぶのが適切です。

<a id="深堀り4"></a>

## 【深堀り④】Strategy パターンとの違い

State パターンのクラス構成は、「共通のインターフェースを実装したクラスを保持し、処理を任せる」という点で Strategy パターンとよく似ています。<br>
2 つのパターンの違いは、保持しているオブジェクトを差し替えるきっかけと、差し替えるのが誰かにあります。

| 観点                 | State パターン                               | Strategy パターン                    |
| -------------------- | -------------------------------------------- | ------------------------------------ |
| 保持するオブジェクト | 現在の状態                                   | 処理のやり方（アルゴリズム）         |
| 差し替えるきっかけ   | 操作の結果、状態が変わったとき               | 利用する側が別のやり方を使いたいとき |
| 差し替えるのは誰か   | 状態クラス自身（または状態を保持するクラス） | 利用する側（`Main` クラスなど）      |
| 実装クラス同士の関係 | 次の状態を知っていることが多い               | 互いを知らない                       |

今回の `ExpenseReport` クラスでも、`Main` クラスは `submit`・`approve`・`sendBack` メソッドを呼び出すだけで、どの状態クラスのインスタンスが使われているかを一切指定していません。状態オブジェクトの差し替えは、操作の結果として状態クラスの中で行われています。

<a id="深堀り5"></a>

## 【深堀り⑤】GoF デザインパターンとの位置づけ

今回使った State パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
