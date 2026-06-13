# Template Method（テンプレートメソッド）パターン ― 処理の流れをスーパークラスに任せる

次のような経験をしたことはありませんか？

> 追加実装をする際に、同じような処理の流れを持つクラスを何度も書いてしまった。

実務では、このような場面は珍しくありません。確かに最初はクラスの数も少なく、コピーで素早く対応できます。しかし、機能追加を重ねるたびにクラスは増え、仕様変更が起きたときには全クラスを修正しなければなりません。

この記事では、社内通知システムへの通知手段追加というシナリオを通して、Template Method パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
    - [問題点①（コードの重複）](#問題点コードの重複)
    - [問題点②（一貫性の欠如）](#問題点一貫性の欠如)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】抽象クラスとインターフェースの比較](#深堀り1)
- [【深堀り②】継承の危険性（脆弱な基底クラス問題）](#深堀り2)
- [【深堀り③】フック（hook）メソッドとは](#深堀り3)
- [【深堀り④】LSP（リスコフの置換原則）](#深堀り4)
- [【深堀り⑤】subclass responsibility（サブクラスの責任）](#深堀り5)
- [【深堀り⑥】GoF デザインパターンとの位置づけ](#深堀り6)

---

## 【具体例】

### シナリオ

> あなたは社内システム開発チームに所属しています。<br>
> 障害発生時に担当者へ通知を送るシステムに、機能を追加する開発タスクが割り振られました。<br>
> 現行では、メール通知機能のみ実装されている状態です。<br>
> 今回、Slack 通知と SMS 通知も追加することになりました。

### 既存コードの仕様

- `EmailNotification`（既存クラス）

メール通知を行う既存のクラスです。<br>
`send` メソッドの中に、「バリデーション・本文の組み立て・送信・ログ記録」の 4 ステップがまとめて書かれています。<br>
※本記事の主題から脱線しないようにするために、バリデーション確認は入力チェックのみになっています。

| フィールド     | 型       | 説明                   |
| -------------- | -------- | ---------------------- |
| `emailAddress` | `String` | 送信先のメールアドレス |

| メソッド                    | 説明                                                          |
| --------------------------- | ------------------------------------------------------------- |
| `void send(String message)` | 「バリデーション→本文の組み立て→送信→ログ記録」を順に実行する |

**`EmailNotification.java`**
```java
package example;

public class EmailNotification {
    private String emailAddress;

    public EmailNotification(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public void send(String message) {
        // バリデーション
        if (emailAddress == null || emailAddress.isEmpty()) {
            throw new IllegalArgumentException("メールアドレスが設定されていません");
        }
        // 本文の組み立て
        String body = "[障害通知] " + message;
        // 送信処理
        System.out.println(emailAddress + " にメールを送信しました：" + body);
        // ログ記録
        System.out.println("[LOG] メール送信完了");
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
        EmailNotification email = new EmailNotification("user@example.com");

        String message = "DBサーバーの応答が停止しました";

        email.send(message);
    }
}
```

**出力結果**

```
user@example.com にメールを送信しました：[障害通知] DBサーバーの応答が停止しました
[LOG] メール送信完了
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

既存のコードがあるので、`EmailNotification` を参考に、以下のような実装をするのではないでしょうか？

Slack では、障害発生時用のチャンネルに通知を飛ばすと考えられるので、次のコードになると思います。

**`SlackNotification.java`**
```java
package example;

public class SlackNotification {
    private String channel;

    public SlackNotification(String channel) {
        this.channel = channel;
    }

    public void send(String message) {
        // バリデーション
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("チャンネルが設定されていません");
        }
        // 本文の組み立て
        String body = ":warning: " + message;
        // 送信処理
        System.out.println(channel + " にSlack通知を送信しました：" + body);
        // ログ記録
        System.out.println("[LOG] Slack送信完了");
    }
}
```

SMS では、電話番号が必要になるので、次のコードになると思います。

**`SmsNotification.java`**
```java
package example;

public class SmsNotification {
    private String phoneNumber;

    public SmsNotification(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void send(String message) {
        // バリデーション
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            throw new IllegalArgumentException("電話番号が設定されていません");
        }
        // 本文の組み立て
        String body = "【障害】" + message;
        // 送信処理
        System.out.println(phoneNumber + " にSMSを送信しました：" + body);
        // ログ記録
        System.out.println("[LOG] SMS送信完了");
    }
}
```

**`Main.java`**
```java
package example;

public class Main {
    public static void main(String[] args) {
        EmailNotification email = new EmailNotification("user@example.com");

        /* ここを追加（ここから） */
        SlackNotification slack = new SlackNotification("#アラート通知チャンネル");
        SmsNotification sms = new SmsNotification("090-0000-0000");
        /* ここを追加（ここまで） */

        String message = "DBサーバーの応答が停止しました";

        email.send(message);

        /* ここを追加（ここから） */
        System.out.println();

        slack.send(message);
        sms.send(message);
        /* ここを追加（ここまで） */
    }
}
```

**出力結果**

```
user@example.com にメールを送信しました：[障害通知] DBサーバーの応答が停止しました
[LOG] メール送信完了

#アラート通知チャンネル にSlack通知を送信しました：:warning: DBサーバーの応答が停止しました
[LOG] Slack送信完了
090-0000-0000 にSMSを送信しました：【障害】DBサーバーの応答が停止しました
[LOG] SMS送信完了
```

コンパイルエラーがなく結果が出力されていることから、実装・動作確認ともに問題ないことがわかります。

### 問題点①（コードの重複）

しかし、この実装には以下の問題点があります。

- 仕様変更のたびに、全クラスへの修正が必要になる
    - その結果、追加実装時はクラスが増えるので、仕様変更に伴う修正漏れのリスクが高くなる
- `EmailNotification`・`SlackNotification`・`SmsNotification` に共通の型がないため、一括で扱うことができない（例えば、`send` メソッドの呼び出し方が同じため、下記のように処理をまとめようとしても、まとめることができない）

    ```java
    // 共通の型がないので型が決められない
    List<???> senders = new ArrayList<>();
    senders.add(new EmailNotification("user@example.com"));
    senders.add(new SlackNotification("#アラート通知チャンネル"));
    senders.add(new SmsNotification("090-0000-0000"));

    for (??? sender : senders) {
        sender.send(message);
    }
    ```

### 問題点②（一貫性の欠如）

問題点①ではコードの重複に伴う問題点を指摘しました。<br>
ここでは、コードの重複とは別の問題もあることを見ていきます。<br>
次の実装を見てください。

**`SlackNotification.java`**
```java
package example;

public class SlackNotification {
    private String channel;

    public void send(String message) {
        // ログ記録
        System.out.println("[LOG] Slack送信開始");
        // 本文の組み立て
        String body = ":warning: " + message;
        // 送信処理
        System.out.println(channel + " にSlack通知を送信しました：" + body);
    }
}
```

**`Main.java`**
```java
package example;

public class Main {
    public static void main(String[] args) {
        EmailNotification email = new EmailNotification("user@example.com");
        SlackNotification slack = new SlackNotification(); // ←ここを追加

        String message = "DBサーバーの応答が停止しました";

        email.send(message);

        System.out.println(); // ←ここを追加

        slack.send(message); // ←ここを追加
    }
}
```

**出力結果**

```
user@example.com にメールを送信しました：[障害通知] DBサーバーの応答が停止しました
[LOG] メール送信完了

[LOG] Slack送信開始
null にSlack通知を送信しました：:warning: DBサーバーの応答が停止しました
```

上記の `SlackNotification` クラスを見ると、ログ記録のタイミングが変更されていると同時に、バリデーション処理が削除されていることがわかります。また、コンストラクタの定義も行っていません。

こちらのコードはコンパイルエラーにならない点が厄介です。その理由は、出力結果からもわかるように `channel` が `null` の状態で送信が実行されるので、テスト時にバグとして報告された際に、初めて実装が間違っていたことに気づくことになるからです。

このようなことから、下記の問題があります。

- `send` メソッドに実装ルールがないことから、機能追加時に既存クラスを見て真似るしかないため、処理の流れが各クラスでばらつく可能性が高まり、設計の一貫性が失われる
    - 実装者の判断で必須のステップが省略されると、不正な状態のまま実行される
    - 処理の順序を実装者が自由に変えられるため、設計意図と異なる順序になりやすい
- 処理の流れを強制する仕組みがなく、コンパイルエラーにならないため、実装の誤りが実行時まで気づけない

## 正しい実装

では、コードの重複をなくすと同時に、設計の一貫性を保った追加実装をするにはどうすればよいのでしょうか？

これらの問題を解決するのが **Template Method パターン**です。<br>
まずは次のコードを見てください。

**`NotificationSender.java`**
```java
package example;

public abstract class NotificationSender {
    public abstract void validate();

    public abstract String buildMessage(String message);

    public abstract void sendNotification(String body);

    public abstract void logResult();

    public final void send(String message) {
        validate();
        String body = buildMessage(message);
        sendNotification(body);
        logResult();
    }
}
```

抽象クラス `NotificationSender` を見ると以下のことがわかります。

- `validate`・`buildMessage`・`sendNotification`・`logResult` の 4 つの抽象メソッドがある
- 「`validate()`・`buildMessage()`・`sendNotification()`・`logResult()`」の順で呼び出している `send` メソッドがある
- `send` メソッドに `final` がついている

上記のコードからは最終的にどんな処理を行うかはわかりませんが、処理の枠組みは定められていることがわかります。<br>
このことから、処理の枠組みが定まった `NotificationSender` を継承し、実装が強制された 4 つの抽象メソッドを実装することで具体的な処理が確定することが読み取れます。

このように、スーパークラスで処理の枠組みを定め、サブクラスで具体的な処理内容を定めるようなデザインパターンを **Template Method パターン**と言います。<br>
また、`send` メソッドに `final` がついているため、サブクラスはこのメソッドを上書きできず、処理が固定化されます。このようなメソッドを「**テンプレートメソッド**」と言います。

次に、サブクラスのコードを見ていきましょう。

**`EmailNotification.java`**
```java
package example;

public class EmailNotification extends NotificationSender {
    private String emailAddress;

    public EmailNotification(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    @Override
    public void validate() {
        if (emailAddress == null || emailAddress.isEmpty()) {
            throw new IllegalArgumentException("メールアドレスが設定されていません");
        }
    }

    @Override
    public String buildMessage(String message) {
        return "[障害通知] " + message;
    }

    @Override
    public void sendNotification(String body) {
        System.out.println(emailAddress + " にメールを送信しました：" + body);
    }

    @Override
    public void logResult() {
        System.out.println("[LOG] メール送信完了");
    }
}
```

**`SlackNotification.java`**
```java
package example;

public class SlackNotification extends NotificationSender {
    private String channel;

    public SlackNotification(String channel) {
        this.channel = channel;
    }

    @Override
    public void validate() {
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("チャンネルが設定されていません");
        }
    }

    @Override
    public String buildMessage(String message) {
        return ":warning: " + message;
    }

    @Override
    public void sendNotification(String body) {
        System.out.println(channel + " にSlack通知を送信しました：" + body);
    }

    @Override
    public void logResult() {
        System.out.println("[LOG] Slack送信完了");
    }
}
```

**`SmsNotification.java`**
```java
package example;

public class SmsNotification extends NotificationSender {
    private String phoneNumber;

    public SmsNotification(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    public void validate() {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            throw new IllegalArgumentException("電話番号が設定されていません");
        }
    }

    @Override
    public String buildMessage(String message) {
        return "【障害】" + message;
    }

    @Override
    public void sendNotification(String body) {
        System.out.println(phoneNumber + " にSMSを送信しました：" + body);
    }

    @Override
    public void logResult() {
        System.out.println("[LOG] SMS送信完了");
    }
}
```

実行クラスでは次のようなコードとなり、出力結果は下記となります。

**`Main.java`**
```java
package example;

public class Main {
    public static void main(String[] args) {
        NotificationSender email = new EmailNotification("user@example.com");
        /* ここを追加（ここから） */
        NotificationSender slack = new SlackNotification("#アラート通知チャンネル");
        NotificationSender sms = new SmsNotification("090-0000-0000");
        /* ここを追加（ここまで） */

        String message = "DBサーバーの応答が停止しました";

        email.send(message);
        /* ここを追加（ここから） */
        slack.send(message);
        sms.send(message);
        /* ここを追加（ここまで） */
    }
}
```

**出力結果**

```
user@example.com にメールを送信しました：[障害通知] DBサーバーの応答が停止しました
[LOG] メール送信完了
#アラート通知チャンネル にSlack通知を送信しました：:warning: DBサーバーの応答が停止しました
[LOG] Slack送信完了
090-0000-0000 にSMSを送信しました：【障害】DBサーバーの応答が停止しました
[LOG] SMS送信完了
```

実装が強制された 4 つの抽象メソッドの具体的な実装はどのクラスも異なりますが、`send` メソッドを呼び出した時点で「バリデーション・本文の組み立て・送信・ログ記録」の 4 ステップの順番は変わっていないことがわかります。

このような Template Method パターンの実装を行うと以下のメリットがあります。

- 仕様変更があった際、修正箇所が明確で修正漏れのリスクが低い
    - もし根本的な処理を変更したい場合は、スーパークラスの修正のみ行えば良い
    - テスト時に発覚したバグなど、具体的な実装の修正の場合は、対象のサブクラスのみ修正を行えば良い
- サブクラスを共通の型で呼び出せる（[問題点①](#問題点コードの重複)で触れた「処理がまとめられなかった件」の解決ができる）

    ```java
    // 共通の型で定めることができる
    List<NotificationSender> senders = new ArrayList<>();
    senders.add(new EmailNotification("user@example.com"));
    senders.add(new SlackNotification("#アラート通知チャンネル"));
    senders.add(new SmsNotification("090-0000-0000"));

    for (NotificationSender sender : senders) {
        sender.send(message);
    }
    ```

- 追加実装の際、既存コードから `NotificationSender` を継承することが明らかなため、設計者の意図通りの実装が行われる（設計の一貫性が担保できる）
    - [問題点②](#問題点一貫性の欠如)で触れた「実装者の判断で必須のステップが省略される」「設計意図と異なる順序になりやすい」ということが起こらない
- テンプレートメソッドが `final` で修飾されているため、処理の流れが強制され[問題点②](#問題点一貫性の欠如)で触れた「実装の誤りが実行時まで気づけない」ということが起こらない

## まとめ

Template Method パターンは、次の 2 つの大きな問題を同時に解決します。

| 問題         | 解決策                                                                       |
| ------------ | ---------------------------------------------------------------------------- |
| コードの重複 | 処理の流れをスーパークラス（`NotificationSender`）に一元化する               |
| 一貫性の欠如 | `send` メソッドを `final` にして処理順を強制し、抽象メソッドで実装漏れを防ぐ |

また、追加実装の際は、`NotificationSender` を継承して 4 つの抽象メソッド（`validate`・`buildMessage`・`sendNotification`・`logResult`）を実装するだけで、処理の流れを意識することなく、設計者の意図通りの実装が行えるため、品質を均一に保つことができます。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】抽象クラスとインターフェースの比較

本記事の `send` メソッドに関して、インターフェースの `default` メソッド（Java 8 以降）を使えば、抽象クラスではなくインターフェースで実装できるのではないか？<br>
このように思った方がいるかもしれません。

この深堀りでは、Template Method パターンはインターフェースではなく抽象クラスを用いる必要があることを学びます。

### 理由①（強制力）

抽象クラス `NotificationSender` をインターフェースに変更すると次のようになると思います。

**`NotificationSender.java`**
```java
package example;

public interface NotificationSender {
    public abstract void validate();

    public abstract String buildMessage(String message);

    public abstract void sendNotification(String body);

    public abstract void logResult();

    public default void send(String message) {
        validate();
        String body = buildMessage(message);
        sendNotification(body);
        logResult();
    }
}
```

※説明の都合上、実務では省略されがちな修飾子を全てつけている。

一見、Template Method パターンと同じ実装ができているように見えます。<br>
実際、4 つの抽象メソッド `validate`・`buildMessage`・`sendNotification`・`logResult` の実装は強制できています。

しかし、`send` メソッドでは Template Method パターンとは異なり、下記のように修飾子 `final` をつけることができないため、`NotificationSender` を実現したクラスでメソッドの上書きができてしまいます。<br>
そのため、[問題点②](#問題点一貫性の欠如)で触れた「`send` メソッドに実装すべきルールがないため、… 設計の一貫性が失われる」が発生してしまいます。

```Java
// コンパイルエラーが発生
public final default void send(String message) {
    validate();
    String body = buildMessage(message);
    sendNotification(body);
    logResult();
}
```

Template Method パターンの本質は「流れは固定し、中身だけを差し替える」という構造にあります。<br>
本記事のように `NotificationSender` を抽象クラスとすることで、`send` メソッドに `final` をつけることができ、「流れを守らせる強制力」を表現できるようになります。

### 理由②（状態（フィールド）を持てる）

インターフェースでは定数しか持てない一方、抽象クラスでは定数だけではなくインスタンス変数も持つことができます。

次のコードは `MAX_RETRY` がインターフェースの定数に当たるのですが、参照するだけで更新ができません。そのため、全クラス・全インスタンスで同じ値を共有するだけとなります。

**`NotificationSender.java`**
```java
package example;

public interface NotificationSender {
    public static final int MAX_RETRY = 3;  // 参照するだけで更新できない（全インスタンス共通）

    public abstract void validate();

    public abstract String buildMessage(String message);

    public abstract void sendNotification(String body);

    public abstract void logResult();

    public default void send(String message) {
        validate();
        String body = buildMessage(message);
        sendNotification(body);
        logResult();
    }
}
```

一方、次のコードは `retryCount` が抽象クラスのインスタンス変数に当たるのですが、各インスタンスで独立して送信回数を管理することができます。

**`NotificationSender.java`**
```java
package example;

public abstract class NotificationSender {
    protected int retryCount = 0;  // インスタンスごとに状態を管理できる

    public abstract void validate();

    public abstract String buildMessage(String message);

    public abstract void sendNotification(String body);

    public abstract void logResult();

    public final void send(String message) {
        retryCount++;  // 送信のたびにカウントアップ
        validate();
        String body = buildMessage(message);
        sendNotification(body);
        logResult();
    }
}
```

このように、抽象クラスにすることで状態（フィールド）を持つことができるため、アルゴリズムと状態を一体で管理できるようになります。

### 理由③（設計の意図を伝えられる）

インターフェースは公開を前提としているため、メンバーが強制的に `public` になります。そのため、「このメソッドは公開範囲に制限を設けよう」「外部から直接呼ばせたくない」といった設計の意図を修飾子で表現することができません。<br>
一方、抽象クラスは修飾子を自由に組み合わせることができます。Template Method パターンでは下記の修飾子をよく用います。

| 修飾子      | 意味                    | 使いどころ                                                   |
| ----------- | ----------------------- | ------------------------------------------------------------ |
| `final`     | 触るの NG               | テンプレートメソッド（上書きを禁止する）                     |
| `abstract`  | 必ず実装して            | ステップメソッド（サブクラスに実装を強制する）               |
| `protected` | サブクラスだけ触って OK | ステップメソッド（無関係な他クラスからの直接呼び出しを防ぐ） |

このように、抽象クラスは修飾子を通じて「どこを触って良く、どこを触ってはいけないか」という設計の意図をコードで伝えることができます。

### まとめ

インターフェースは「自由を与える」設計である一方、抽象クラスは「自由を制限する」設計です。

| 観点                       | インターフェース                                                 | 抽象クラス                                 |
| -------------------------- | ---------------------------------------------------------------- | ------------------------------------------ |
| 実装を強制する             | `abstract` メソッドのみ可<br>※`default` の上書きを防ぐ手段がない | `abstract` で強制できる                    |
| メソッドの上書きを禁止する | 不可（`default` は常に上書き可）                                 | `final` で実現できる                       |
| 状態（フィールド）         | 持てない（定数のみ）                                             | 持てる                                     |
| 設計の意図                 | 「何ができるか（契約）」を表現する                               | 「どう動くべきか（骨格＋制御）」を表現する |

この表を見ても分かるように、Template Method パターンは「制限された自由」を提供するパターンとも言えるため、抽象クラスを用いる必要があるのです。

<a id="深堀り2"></a>

## 【深堀り②】継承の危険性（脆弱な基底クラス問題）

継承には「脆弱な基底クラス問題（Fragile Base Class Problem）」と呼ばれるリスクがあります。<br>
これは、スーパークラスの内部実装を変更したとき、サブクラスが意図しない動作をしてしまう問題です。<br>
そのため、サブクラスの実装者がスーパークラスの内部を深く理解していないと、バグの原因を特定することが困難になります。

Template Method パターンでは、本記事のように適切な修飾子をつけることで、上記のリスクを軽減する設計となっています。

<a id="深堀り3"></a>

## 【深堀り③】フック（hook）メソッドとは

本記事の抽象クラス `NotificationSender` の抽象メソッド `logResult` に関して次のように思った方もいるのではないでしょうか？

> 通知先のアプリから媒体が分かるため、通知が完了した旨さえ分かれば十分だと思う。<br>
> `logResult` は共通のメソッドにすれば良いのではないか。

上記のように考えた場合、次の実装になると思います。（※説明の都合上、メール通知の実装のみ）

**`NotificationSender.java`**
```java
package example;

public abstract class NotificationSender {
    public abstract void validate();

    public abstract String buildMessage(String message);

    public abstract void sendNotification(String body);

    // デフォルトの実装にしている
    public void logResult() {
        System.out.println("[LOG] 通知送信完了");
    }

    public final void send(String message) {
        validate();
        String body = buildMessage(message);
        sendNotification(body);
        logResult();
    }
}
```

**`EmailNotification.java`**
```java
package example;

public class EmailNotification extends NotificationSender {
    private String emailAddress;

    public EmailNotification(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    @Override
    public void validate() {
        if (emailAddress == null || emailAddress.isEmpty()) {
            throw new IllegalArgumentException("メールアドレスが設定されていません");
        }
    }

    @Override
    public String buildMessage(String message) {
        return "[障害通知] " + message;
    }

    @Override
    public void sendNotification(String body) {
        System.out.println(emailAddress + " にメールを送信しました：" + body);
    }

    // logResult メソッドのオーバーライドは不要
}
```

**`Main.java`**
```java
package example;

public class Main {
    public static void main(String[] args) {
        NotificationSender email = new EmailNotification("user@example.com");

        String message = "DBサーバーの応答が停止しました";

        email.send(message);
    }
}
```

**出力結果**

```
user@example.com にメールを送信しました：[障害通知] DBサーバーの応答が停止しました
[LOG] 通知送信完了
```

出力結果から分かるように正しく動きます。

もしログ記録の文言を変更したい場合は、本記事と同様にオーバーライドすれば良いだけとなります。<br>
このように、`abstract` ではなく、デフォルトの実装を持ちながらサブクラスで上書き可能なメソッドのことを「フック（hook）メソッド」といいます。

### 抽象メソッドとフックメソッドの使い分け

使い分けは下記の表の通り、「サブクラスに実装を強制したいか」を基準に判断すれば良いです。

| 種別           | 修飾子               | サブクラスの実装                       |
| -------------- | -------------------- | -------------------------------------- |
| 抽象メソッド   | `abstract`           | 必須（実装しないとコンパイルエラー）   |
| フックメソッド | なし（具象メソッド） | 任意（デフォルト動作をそのまま使える） |

<a id="深堀り4"></a>

## 【深堀り④】LSP（リスコフの置換原則）

本記事の実装を振り返ると、実行クラスではそれぞれの具体的な実装クラスを `NotificationSender` 型で扱っており、どのサブクラスを使っても `send` メソッドの処理の流れは変わりません。

このような「スーパークラスの代わりにサブクラスを使っても、プログラムの動作が変わらないべきである」という設計は、「**LSP（Liskov Substitution Principle：リスコフの置換原則）**」と呼ばれる設計原則の実践です。Template Method パターンは LSP を実現するための設計手段の一つと言えます。

詳しくは「LSP」や「リスコフの置換原則」で検索してみてください。

<a id="深堀り5"></a>

## 【深堀り⑤】subclass responsibility（サブクラスの責任）

[正しい実装](#正しい実装)のサブクラスを見ると `validate`・`buildMessage`・`sendNotification`・`logResult` の各メソッドをオーバーライドしていることがわかります。

何を当たり前のことを言っているのかと思うかもしれません。また、先程の 4 つのメソッドを抽象メソッドとして定めている `NotificationSender` を継承しているのだから、サブクラスでオーバーライドすることは当然だと感じると思います。

ここで気がついて欲しいことは、上記は全てサブクラスの視点で考えているということです。

ここで視点を変えて、スーパークラス側から考えてみましょう。<br>
スーパークラスである `NotificationSender` が `validate`・`buildMessage`・`sendNotification`・`logResult` を `abstract` で宣言しているということは、プログラムを通じて次のことを主張しています。

- サブクラスが 4 つの抽象メソッドを実装することを「**期待している**」
- サブクラスに対して、4 つの抽象メソッドの実装を「**要請している**」

このように、サブクラスには、スーパークラスで宣言されている抽象メソッドを実装する責任が生じていると言えます。このような責任のことを「**subclass responsibility（サブクラスの責任）**」といいます。

<a id="深堀り6"></a>

## 【深堀り⑥】GoF デザインパターンとの位置づけ

今回使った Template Method パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
