# Template Methodパターン ― 処理の流れを親クラスに任せる

通知機能をメール・Slack・LINEで追加するたびに、同じような処理を何度も書いていませんか？

実務では、同じ処理の流れを持つクラスを複数作る場面は珍しくありません。<br>
最初は「コピーすれば早い」と感じるかもしれませんが、仕様変更のたびに全クラスを修正しなければならなくなります。

さらに厄介なのは、クラスが増えるにつれて「処理の順序」がバラバラになっていく問題です。<br>
誰かがバリデーションを省いたり、ログの位置がズレたりしても、コンパイルエラーにはならないため、気づいたときには本番で障害が発生しているかもしれません。

そのための設計が「Template Methodパターン」です。<br>
処理の流れ（テンプレート）を親クラスに定義することで、重複をなくし、順序の一貫性を強制します。

この記事では、社内通知システムへの通知手段追加というシナリオを通して、Template Methodパターンの実装と、なぜこの設計が選ばれるのかを学んでいきます。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装①（コードの重複）](#好ましくない実装コードの重複)
- [好ましくない実装②（一貫性の欠如）](#好ましくない実装一貫性の欠如)
- [正しい実装（Template Methodパターン）](#正しい実装template-methodパターン)
- [まとめ](#まとめ)
- [【深堀り①】抽象クラスとインターフェースの比較](#深堀り1)
- [【深堀り②】継承の危険性（脆弱な基底クラス問題）](#深堀り2)
- [【深堀り③】フック（hook）メソッドとは](#深堀り3)
- [【深堀り④】リスコフの置換原則（LSP）とサブクラスの責任](#深堀り4)
- [【深堀り⑤】GoFデザインパターンとの位置づけ](#深堀り5)

## 【具体例】

### シナリオ

> あなたは社内システム開発チームに所属しています。<br>
> 障害発生時に担当者へ通知を送るシステムを開発することになりました。<br>
> 最初はメール通知のみで対応しましたが、その後 Slack・SMS も追加することになり、3つの通知クラスをそれぞれ実装しました。<br>
> さらに新たに LINE 通知の追加を別のメンバーに依頼したところ、テストで通知が不正な状態で送られるバグが発覚しました。

### 既存コードの仕様

- NotificationService（インターフェース）

全ての通知クラスが実現すべき共通契約です。<br>
新しい通知手段を追加する際には、このインターフェースを実現することで、既存の処理と統一した形で扱えるようになります。

| メソッド | 説明 |
| --- | --- |
| `void send(String message)` | 指定したメッセージを通知する |

```Java:NotificationService.java
public interface NotificationService {
    void send(String message);
}
```

## 好ましくない実装①（コードの重複）

メール・Slack・SMS の3クラスを、それぞれ独立して実装しました。<br>
どのクラスも「バリデーション → 本文組み立て → 送信 → ログ記録」という同じ流れを持っています。

```Java:EmailNotification.java
public class EmailNotification implements NotificationService {
    private String emailAddress;

    public EmailNotification(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    @Override
    public void send(String message) {
        // ① バリデーション
        if (emailAddress == null || emailAddress.isEmpty()) {
            throw new IllegalArgumentException("メールアドレスが設定されていません");
        }
        // ② 本文の組み立て
        String body = "[障害通知] " + message;
        // ③ 送信処理
        System.out.println(emailAddress + " にメールを送信しました：" + body);
        // ④ ログ記録
        System.out.println("[LOG] メール送信完了");
    }
}
```

```Java:SlackNotification.java
public class SlackNotification implements NotificationService {
    private String channel;

    public SlackNotification(String channel) {
        this.channel = channel;
    }

    @Override
    public void send(String message) {
        // ① バリデーション
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("チャンネルが設定されていません");
        }
        // ② 本文の組み立て
        String body = ":warning: " + message;
        // ③ 送信処理
        System.out.println(channel + " にSlack通知を送信しました：" + body);
        // ④ ログ記録
        System.out.println("[LOG] Slack送信完了");
    }
}
```

```Java:SmsNotification.java
public class SmsNotification implements NotificationService {
    private String phoneNumber;

    public SmsNotification(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    public void send(String message) {
        // ① バリデーション
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            throw new IllegalArgumentException("電話番号が設定されていません");
        }
        // ② 本文の組み立て
        String body = "【障害】" + message;
        // ③ 送信処理
        System.out.println(phoneNumber + " にSMSを送信しました：" + body);
        // ④ ログ記録
        System.out.println("[LOG] SMS送信完了");
    }
}
```

3クラスとも、コメント①〜④の処理の流れが完全に同じです。<br>
たとえばログのフォーマットを変更したい場合、3クラス全てを修正しなければなりません。<br>
クラスが増えるほど、この修正漏れのリスクは大きくなっていきます。

## 好ましくない実装②（一貫性の欠如）

コードの重複以上に危険な問題があります。<br>
新たに LINE 通知を追加する際、別のメンバーが以下のように実装しました。

```Java:LineNotification.java
public class LineNotification implements NotificationService {
    private String userId;

    public LineNotification(String userId) {
        this.userId = userId;
    }

    @Override
    public void send(String message) {
        // ④ ログ記録（送信前に実行されている）
        System.out.println("[LOG] LINE送信開始");
        // ② 本文の組み立て
        String body = "【通知】" + message;
        // ③ 送信処理
        System.out.println(userId + " にLINE通知を送信しました：" + body);
        // ※ バリデーションが省略されている
    }
}
```

このコードはコンパイルエラーになりません。<br>
しかし、バリデーションが省略されているため `userId` が `null` のまま送信が実行され、さらにログが送信前に記録されるという問題があります。

`NotificationService` インターフェースは `send()` の**存在**しか保証しません。<br>
処理の順序や必須ステップを強制する仕組みがないため、実装者によって内容がバラバラになってしまいます。

ここに Template Method パターンが必要な理由があります。

## 正しい実装（Template Methodパターン）

`NotificationSender` という抽象クラスを導入し、処理の流れをここに定義します。<br>
各通知クラスは `NotificationSender` を継承し、差分となる処理だけを実装します。

```Java:NotificationSender.java
public abstract class NotificationSender implements NotificationService {

    @Override
    public final void send(String message) {
        validate();
        String body = buildMessage(message);
        sendNotification(body);
        logResult();
    }

    protected abstract void validate();

    protected abstract String buildMessage(String message);

    protected abstract void sendNotification(String message);

    protected void logResult() {
        System.out.println("[LOG] 通知送信完了");
    }
}
```

`send()` に `final` を付けることで、サブクラスからの上書きを禁止し、処理の流れを固定しています。<br>
`validate()`・`buildMessage()`・`sendNotification()` は `abstract` として宣言されており、サブクラスでの実装が強制されます。<br>
`logResult()` は共通処理として親クラスに実装済みです。必要であればサブクラスで上書きすることもできます。

各通知クラスは、差分となる処理のみを実装します。

```Java:EmailNotification.java
public class EmailNotification extends NotificationSender {
    private String emailAddress;

    public EmailNotification(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    @Override
    protected void validate() {
        if (emailAddress == null || emailAddress.isEmpty()) {
            throw new IllegalArgumentException("メールアドレスが設定されていません");
        }
    }

    @Override
    protected String buildMessage(String message) {
        return "[障害通知] " + message;
    }

    @Override
    protected void sendNotification(String message) {
        System.out.println(emailAddress + " にメールを送信しました：" + message);
    }
}
```

```Java:SlackNotification.java
public class SlackNotification extends NotificationSender {
    private String channel;

    public SlackNotification(String channel) {
        this.channel = channel;
    }

    @Override
    protected void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("チャンネルが設定されていません");
        }
    }

    @Override
    protected String buildMessage(String message) {
        return ":warning: " + message;
    }

    @Override
    protected void sendNotification(String message) {
        System.out.println(channel + " にSlack通知を送信しました：" + message);
    }
}
```

```Java:SmsNotification.java
public class SmsNotification extends NotificationSender {
    private String phoneNumber;

    public SmsNotification(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    protected void validate() {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            throw new IllegalArgumentException("電話番号が設定されていません");
        }
    }

    @Override
    protected String buildMessage(String message) {
        return "【障害】" + message;
    }

    @Override
    protected void sendNotification(String message) {
        System.out.println(phoneNumber + " にSMSを送信しました：" + message);
    }
}
```

```Java:LineNotification.java
public class LineNotification extends NotificationSender {
    private String userId;

    public LineNotification(String userId) {
        this.userId = userId;
    }

    @Override
    protected void validate() {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("ユーザーIDが設定されていません");
        }
    }

    @Override
    protected String buildMessage(String message) {
        return "【通知】" + message;
    }

    @Override
    protected void sendNotification(String message) {
        System.out.println(userId + " にLINE通知を送信しました：" + message);
    }
}
```

`LineNotification` は `validate()`・`buildMessage()`・`sendNotification()` を実装するだけで、処理の順序は `NotificationSender.send()` が保証してくれます。<br>
バリデーションの省略や順序の崩れは、もはや起こりえません。

実行クラスは以下の通りです。

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        NotificationService email = new EmailNotification("user@example.com");
        NotificationService slack = new SlackNotification("#alerts");
        NotificationService sms   = new SmsNotification("090-0000-0000");
        NotificationService line  = new LineNotification("U1234567890");

        String message = "DBサーバーの応答が停止しました";

        email.send(message);
        slack.send(message);
        sms.send(message);
        line.send(message);
    }
}
```

実行結果：

```
user@example.com にメールを送信しました：[障害通知] DBサーバーの応答が停止しました
[LOG] 通知送信完了
#alerts にSlack通知を送信しました：:warning: DBサーバーの応答が停止しました
[LOG] 通知送信完了
090-0000-0000 にSMSを送信しました：【障害】DBサーバーの応答が停止しました
[LOG] 通知送信完了
U1234567890 にLINE通知を送信しました：【通知】DBサーバーの応答が停止しました
[LOG] 通知送信完了
```

## まとめ

Template Method パターンは、次の2つの問題を同時に解決します。

| 問題 | 解決策 |
| --- | --- |
| コードの重複 | 処理の流れを親クラス（`NotificationSender`）に一元化する |
| 一貫性の欠如 | `send()` を `final` にして処理順を強制し、抽象メソッドで実装漏れを防ぐ |

インターフェースは「何ができるか」を定義しますが、「どういう順序でやるか」は定義できません。<br>
抽象クラスがあることで、共通の処理の流れを持ちながら、差分だけをサブクラスに委ねることができます。

新しい通知手段を追加する際は、`NotificationSender` を継承して3つの抽象メソッドを実装するだけです。<br>
処理の流れを意識する必要はなく、追加のたびに品質が均一に保たれます。

<a id="深堀り1"></a>

## 【深堀り①】抽象クラスとインターフェースの比較

Template Method パターンが「なぜインターフェースではなく抽象クラスを使うのか」という疑問は自然です。

| 観点 | インターフェース | 抽象クラス |
| --- | --- | --- |
| 実装の共有 | できない（`default` メソッドで限定的に可） | できる |
| 多重実装/継承 | 複数実装できる | 1つしか継承できない |
| 状態（フィールド） | 持てない | 持てる |
| 目的 | 「何ができるか」を定義する | 「共通の処理」を提供しつつ差分を強制する |

Template Method パターンの核心は「処理の流れを親クラスに書く」ことです。<br>
インターフェースは処理の実装を持てないため、テンプレートメソッド（`send()` の中身）を定義できません。

Java 8 以降は `default` メソッドによってインターフェースに実装を書けるようになりましたが、`abstract` メソッドの強制と `final` による上書き禁止を組み合わせる Template Method の意図を表現するには抽象クラスが適しています。

`NotificationService`（インターフェース）と `NotificationSender`（抽象クラス）を併用しているのは、外部からは `NotificationService` 型で扱えるようにしつつ、内部の実装の流れは抽象クラスで管理するためです。

<a id="深堀り2"></a>

## 【深堀り②】継承の危険性（脆弱な基底クラス問題）

継承には「脆弱な基底クラス問題（Fragile Base Class Problem）」と呼ばれるリスクがあります。<br>
親クラスの内部実装を変更したとき、サブクラスが意図しない動作をしてしまう問題です。

たとえば `NotificationSender.send()` の処理順を変更した場合、全サブクラスの動作に影響します。<br>
サブクラスの実装者が親クラスの内部を深く理解していないと、バグの原因を特定するのが困難になります。

Template Method パターンはこのリスクを軽減する設計を含んでいます。

- `send()` に `final` を付けることで、サブクラスがテンプレートメソッドを上書きできないようにしている
- サブクラスが実装するのは `abstract` メソッドのみであり、処理の流れに干渉できない

継承を使う際は「親クラスの何を変えてはいけないか」を明示することが重要です。<br>
`final` はその意図をコードで表現する手段です。

<a id="深堀り3"></a>

## 【深堀り③】フック（hook）メソッドとは

Template Method パターンには「フックメソッド」という概念があります。<br>
`abstract` ではなく、デフォルトの実装を持ちながらサブクラスで上書き可能なメソッドです。

今回の `logResult()` はフックに近い存在です。

```Java
protected void logResult() {
    System.out.println("[LOG] 通知送信完了");
}
```

このメソッドは `abstract` ではないため、サブクラスは上書きしなくても構いません。<br>
しかし必要であれば上書きして、通知手段ごとに異なるログ出力にすることもできます。

```Java
// SlackNotification での上書き例
@Override
protected void logResult() {
    System.out.println("[LOG] Slack通知完了（チャンネル：" + channel + "）");
}
```

`abstract` メソッドとフックメソッドの使い分けは、「サブクラスに実装を強制するか」で決まります。

| 種別 | キーワード | サブクラスの実装 |
| --- | --- | --- |
| 抽象メソッド | `abstract` | 必須（実装しないとコンパイルエラー） |
| フックメソッド | なし（具象メソッド） | 任意（デフォルト動作をそのまま使える） |

<a id="深堀り4"></a>

## 【深堀り④】リスコフの置換原則（LSP）とサブクラスの責任

リスコフの置換原則（Liskov Substitution Principle / LSP）とは、「親クラスの代わりにサブクラスを使っても、プログラムの動作が変わらないべきである」という原則です。

悪い実装②の `LineNotification` はこの原則に違反していました。<br>
`NotificationService.send()` を呼び出す側は「バリデーション → 本文組み立て → 送信 → ログ記録」という動作を期待しています。<br>
しかし `LineNotification` はバリデーションを省略し、ログの順序も異なっていたため、期待と異なる動作をしていました。

Template Method パターンはこの LSP 違反を構造的に防ぎます。<br>
`send()` の中身は `NotificationSender` が管理しており、サブクラスは決められた抽象メソッドを実装するだけです。<br>
どのサブクラスに差し替えても、呼び出し側が期待する動作の流れは変わりません。

LSP は「サブクラスは親クラスの契約を守らなければならない」という責任を定義しています。<br>
Template Method パターンはその契約をコードで強制する設計です。

<a id="深堀り5"></a>

## 【深堀り⑤】GoFデザインパターンとの位置づけ

Template Method パターンは GoF（Gang of Four）の23のデザインパターンのうち、「振る舞いパターン」に分類されます。<br>
振る舞いパターンとは、オブジェクト間の責任の分担と通信の仕方を定義するパターン群です。

Template Method パターンとよく比較されるのが **Strategy パターン**です。<br>
どちらも「アルゴリズムの差分を切り出す」という目的を持ちますが、アプローチが異なります。

| 観点 | Template Method | Strategy |
| --- | --- | --- |
| 差分の切り出し方 | 継承（サブクラスで実装） | 委譲（別クラスに委ねる） |
| 切り替えのタイミング | コンパイル時（静的） | 実行時（動的） |
| 柔軟性 | 低い（クラス変更が必要） | 高い（オブジェクトの差し替えで変更可能） |
| コードの見通し | 処理の流れが親クラスに集約される | 戦略クラスが増えると管理が増える |

通知手段を実行時に動的に切り替えたい場合は Strategy パターンが適しています。<br>
一方、処理の流れを固定して差分のみサブクラスに委ねたい場合は Template Method パターンが適しています。

どちらが正解というわけではなく、「アルゴリズムの流れを固定したいか」「実行時に切り替えたいか」という要件によって使い分けます。
