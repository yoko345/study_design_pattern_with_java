# Template Methodパターン ― 処理の流れをスーパークラスに任せる

追加実装をする際に、同じような処理の流れを持つクラスを何度も書いていませんか？

実務では、このような場面は珍しくありません。<br>
最初は「コピーすれば早い」と感じるかもしれません。しかし、仕様変更のたびに全クラスを修正しなければならなくなります。

この記事では、このような問題を解決するTemplate Methodパターンを社内通知システムへの通知手段追加というシナリオを通して学びます。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
    - [問題点①（コードの重複）](#問題点コードの重複)
    - [問題点②（一貫性の欠如）](#問題点一貫性の欠如)
- [正しい実装](#正しい実装)
- [メリット](#メリット)
- [まとめ](#まとめ)
- [【深堀り①】抽象クラスとインターフェースの比較](#深堀り1)
- [【深堀り②】継承の危険性（脆弱な基底クラス問題）](#深堀り2)
- [【深堀り③】フック（hook）メソッドとは](#深堀り3)
- [【深堀り④】リスコフの置換原則（LSP）](#深堀り4)
- [【深堀り⑤】サブクラスの責任](#深堀り5)
- [【深堀り⑥】GoFデザインパターンとの位置づけ](#深堀り6)

## 【具体例】

### シナリオ

> あなたは社内システム開発チームに所属しています。<br>
> 障害発生時に担当者へ通知を送るシステムの機能追加を担当することになりました。<br>
> 現行では、メール通知のみ実装されている状態です。<br>
> 今回、Slack 通知と SMS 通知も追加することになりました。

### 既存コードの仕様

- EmailNotification（既存クラス）

メール通知を行う既存のクラスです。<br>
`send()` の中に、バリデーション・本文の組み立て・送信・ログ記録の4ステップが一体で書かれています。<br>
※バリデーションに関して、本記事の主題から脱線しないようにするために、入力チェックのみに絞っています。

| フィールド     | 型       | 説明                   |
| -------------- | -------- | ---------------------- |
| `emailAddress` | `String` | 送信先のメールアドレス |

| メソッド                    | 説明                                                |
| --------------------------- | --------------------------------------------------- |
| `void send(String message)` | バリデーション→本文組み立て→送信→ログを順に実行する |

```Java:EmailNotification.java
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

- Main（実行クラス）

```Java:Main.java
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

## 好ましくない実装

では、追加実装をしていきましょう。

既存のコードがあるので、 `EmailNotification` を参考に、以下のような実装をするのではないでしょうか？

Slackでは、障害発生時用のチャンネルに通知を飛ばすと考えられるので、以下の実装になると思います。

```Java:SlackNotification.java
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

SMSでは、電話番号が必要になるので、以下の実装になると思います。

```Java:SmsNotification.java
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

```Java:Main.java
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

実装・動作確認ともに問題ないことが上記からわかると思います。

### 問題点①（コードの重複）

しかし、この実装には以下の問題点があります。

- 仕様変更のたびに、全クラスへの修正が必要になる
    - その結果、追加実装のたびにクラスが増えると修正漏れのリスクが高くなる
- `EmailNotification`・`SlackNotification`・`SmsNotification` に共通の型がないため、一括で扱うことができない（例えば、下記のように`send`メソッドの呼び出し方が同じため、処理をまとめようとしても、まとめることができません）

    ```java
    // やりたいこと（書けない）
    List<???> senders = new ArrayList<>();
    senders.add(new EmailNotification("user@example.com"));
    senders.add(new SlackNotification("#アラート通知チャンネル"));
    senders.add(new SmsNotification("090-0000-0000"));

    for (??? sender : senders) {
        sender.send(message);  // 共通の型がないので型が決められない
    }
    ```

### 問題点②（一貫性の欠如）

問題点①ではコードの重複に伴う問題点を指摘しましたが、コードの重複とは別の問題もあります。<br>
次の実装を見てください。

```Java:SlackNotification.java
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

```Java:Main.java

public class Main {
    public static void main(String[] args) {
        EmailNotification email = new EmailNotification("user@example.com");
        SlackNotification slack = new SlackNotification(); // ←ここを追加

        String message = "DBサーバーの応答が停止しました";

        email.send(message);

        /* ここを追加（ここから） */
        System.out.println();

        slack.send(message);
        /* ここを追加（ここまで） */
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

上記では、ログの記録タイミングが変更されていると同時に、バリデーション処理が削除されていることがわかります。また、コンストラクタの定義も行っていません。<br>
こちらのコードはコンパイルエラーにならない点が厄介です。<br>
なぜかと言うと、出力結果からもわかるように `channel` が `null` の状態で送信が実行されるので、テスト時にバグとして報告されて初めて実装が間違っていたことに気づくことになります。

上記から、下記の問題があります。

- `send()` に実装すべきルールがないため、追加実装時は既存クラスを見て真似るしかなく、クラスごとに処理の流れがばらつく可能性が高まり、設計の一貫性が失われる
    - 実装者の判断で必須のステップが省略されると、不正な状態のまま実行される
    - 処理の順序を実装者が自由に変えられるため、設計意図と異なる順序になりやすい
- コンパイルエラーにならないが、処理の流れを強制する仕組みがないため、実装の誤りが実行時まで気づけない

## 正しい実装

では、コードの重複をなくし、設計の一貫性を保った追加実装をするにはどうすればよいのでしょうか？

これらの問題を解決するのがTemplate Methodパターンです。<br>
まずは次のコードを見てください。

```Java:NotificationSender.java
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

抽象クラス`NotificationSender`を見ると以下のことがわかります。

- `validate`・`buildMessage`・`sendNotification`・`logResult`の4つの抽象メソッドがある
- 「`validate()`・`buildMessage()`・`sendNotification()`・`logResult()`」の順で呼び出している`send`メソッドがある
- `send`メソッドに`final`がついている

上記のコードからは最終的にどんな処理を行うかはわかりませんが、処理の枠組みは定めていることがわかります。<br>
この処理の枠組みが定まった`NotificationSender`を継承し、実装が強制された4つの抽象メソッドを実装することで具体的な処理が確定することが読み取れます。

このような、スーパークラスで処理の枠組みを定め、サブクラスで具体的な内容を定めるようなデザインパターンをTemplate Methodパターンといいます。<br>
また、`send`メソッドに`final`がついているため、サブクラスは上書きできないので処理が固定化されます。このようなメソッドを「テンプレートメソッド」といいます。

次にサブクラスのコードを見ていきましょう。

```Java:EmailNotification.java
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

```Java:SlackNotification.java
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

```Java:SmsNotification.java
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

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        NotificationSender email = new EmailNotification("user@example.com");
        NotificationSender slack = new SlackNotification("#アラート通知チャンネル");
        NotificationSender sms = new SmsNotification("090-0000-0000");

        String message = "DBサーバーの応答が停止しました";

        email.send(message);
        slack.send(message);
        sms.send(message);
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

実装が強制された4つの抽象メソッドの具体的な実装はどのクラスも異なりますが、`send`メソッドを呼び出した時点で「バリデーション・本文の組み立て・送信・ログ記録」の4ステップの順番は変わっていないことがわかります。

## メリット

Template Methodパターンの実装を行うと以下のメリットがあります。

- 仕様変更があった際、修正箇所が明確で修正漏れのリスクが低い
    - もし根本的な処理を変更したい場合は、スーパークラスの修正のみを行えば良い
    - テスト時に発覚したバグなど具体的な実装の修正の場合は、対象のサブクラスのみ修正を行えば良い
- 具体的な実装クラスを共通の型で呼び出せる（[問題点①](#問題点コードの重複)で触れた「処理がまとめられなかった件」の解決ができる）

    ```java
    List<NotificationSender> senders = new ArrayList<>();
    senders.add(new EmailNotification("user@example.com"));
    senders.add(new SlackNotification("#アラート通知チャンネル"));
    senders.add(new SmsNotification("090-0000-0000"));

    for (NotificationSender sender : senders) {
        sender.send(message);
    }
    ```

- 追加実装の際、既存コードから`NotificationSender`を継承することが明らかであるため、設計者の意図通りの実装が行われるので、設計の一貫性が担保できる
    - 問題点②で触れた「実装者の判断で必須のステップが省略される」「設計意図と異なる順序になりやすい」ということが起こらない
- テンプレートメソッドが`final`で修飾されているため、処理の流れが強制され問題点②で触れた「実装の誤りが実行時まで気づけない」ということが起こらない

## まとめ

Template Methodパターンは、次の2つの問題を同時に解決します。

| 問題         | 解決策                                                                 |
| ------------ | ---------------------------------------------------------------------- |
| コードの重複 | 処理の流れをスーパークラス（`NotificationSender`）に一元化する         |
| 一貫性の欠如 | `send()` を `final` にして処理順を強制し、抽象メソッドで実装漏れを防ぐ |

追加実装の際は、`NotificationSender` を継承して4つの抽象メソッド（`validate`・`buildMessage`・`sendNotification`・`logResult`）を実装するだけで、処理の流れを意識することはなく、品質を均一に保つことができます。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】抽象クラスとインターフェースの比較

本記事の`send`メソッドに関して、インターフェースの `default` メソッド（Java 8 以降）を使えば、抽象クラスではなくインターフェースで実装できるのではないか？<br>
このように思った方がいるかもしれません。

この深堀りでは、Template Methodパターンはインターフェースではなく抽象クラスを用いる必要があることを学びます。

### 理由①（強制力）

冒頭の考えに従って、抽象クラス`NotificationSender`をインターフェースに変更すると以下のようになると思います。

```Java:NotificationSender.java
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

一見、Template Methodパターンと同じ実装ができているように見えます。<br>
実際、4つの抽象メソッド`validate`・`buildMessage`・`sendNotification`・`logResult`の実装は強制できています。

しかし、`send`メソッドにおいて下記のようにTemplate Methodパターンと異なり修飾子`final`をつけることができないため、`NotificationSender`を実現したクラスで上書きができてしまいます。<br>
そのため、[問題点②](#問題点一貫性の欠如)で触れた「`send()` に実装すべきルールがないため、…、設計の一貫性が失われる」が発生してしまいます。

```Java
// コンパイルエラーが発生
public final default void send(String message) {
    validate();
    String body = buildMessage(message);
    sendNotification(body);
    logResult();
}
```

Template Methodパターンの本質は「流れは固定し、中身だけを差し替える」という構造にあります。<br>
本記事のように`NotificationSender`を抽象クラスとすることで、`send`メソッドに`final`をつけることができ、「流れを守らせる強制力」を表現できるようになります。

### 理由②（状態（フィールド）を持てる）

インターフェースでは定数しか持てない一方、抽象クラスでは定数だけではなくインスタンス変数も持つことができます。

次のコードは`MAX_RETRY` がインターフェースの定数に当たるのですが、参照するだけで更新ができません。そのため、全クラス・全インスタンスで同じ値を共有するだけとなります。

```Java:NotificationSender.java
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

一方、次のコードは`retryCount` が抽象クラスのインスタンス変数に当たるのですが、各インスタンスで独立して送信回数を管理することができます。

```Java:NotificationSender.java
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
一方、抽象クラスは修飾子を自由に組み合わせることができます。Template Methodパターンでは下記の修飾子をよく用います。

| 修飾子      | 意味               | 使いどころ                                                   |
| ----------- | ------------------ | ------------------------------------------------------------ |
| `final`     | 触るな             | テンプレートメソッド（上書きを禁止する）                     |
| `abstract`  | 必ず実装しろ       | ステップメソッド（サブクラスに実装を強制する）               |
| `protected` | サブクラスだけ触れ | ステップメソッド（無関係な他クラスからの直接呼び出しを防ぐ） |

このように、抽象クラスは修飾子を通じて「どこを触って良いか」「どこを触ってはいけないか」という設計の意図をコードで伝えることができます。

### まとめ

インターフェースは「自由を与える」設計である一方、抽象クラスは「自由を制限する」設計です。

| 観点               | インターフェース                                                 | 抽象クラス                                 |
| ------------------ | ---------------------------------------------------------------- | ------------------------------------------ |
| 状態（フィールド） | 持てない（定数のみ）                                             | 持てる                                     |
| 上書きを禁止する   | 不可（`default` は常に上書き可）                                 | `final` で実現できる                       |
| 実装を強制する     | `abstract` メソッドのみ可<br>※`default` の上書きを防ぐ手段がない | `abstract` で強制できる                    |
| 設計の意図         | 「何ができるか（契約）」を表現する                               | 「どう動くべきか（骨格＋制御）」を表現する |

上記の表を見ても分かるように、Template Methodパターンは「制限された自由」を提供するパターンとも言えるため、抽象クラスを用いる必要があるのです。

<a id="深堀り2"></a>

## 【深堀り②】継承の危険性（脆弱な基底クラス問題）

継承には「脆弱な基底クラス問題（Fragile Base Class Problem）」と呼ばれるリスクがあります。<br>
これは、スーパークラスの内部実装を変更したとき、サブクラスが意図しない動作をしてしまう問題です。<br>
そのため、サブクラスの実装者がスーパークラスの内部を深く理解していないと、バグの原因を特定するのが困難になります。

Template Methodパターンでは、本記事のように適切な修飾子をつけることで上記のリスクを軽減する設計となっています。

<a id="深堀り3"></a>

## 【深堀り③】フック（hook）メソッドとは

本記事の抽象クラス`NotificationSender`の抽象メソッド`logResult`に関して次のように思った方もいるのではないでしょうか？

通知先のアプリから媒体が分かるため、通知が完了した旨さえ分かれば十分だと思う。<br>
`logResult`は共通のメソッドにすれば良いのではないか。

上記のように考えた場合、次の実装になると思います。<br>
※説明の都合上、メール通知の実装のみ

```Java:NotificationSender.java
public abstract class NotificationSender {
    public abstract void validate();

    public abstract String buildMessage(String message);

    public abstract void sendNotification(String body);

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

```Java:EmailNotification.java
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
}
```

```Java:Main.java
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

もしログの文言を変更したい場合は、本記事と同様にオーバーライドすれば良いだけとなります。<br>
このように、`abstract` ではなく、デフォルトの実装を持ちながらサブクラスで上書き可能なメソッドのことを「フックメソッド」といいます。

### 抽象メソッドとフックメソッドの使い分け

使い分けは下記の表の通り、「サブクラスに実装を強制したいか」を基準に判断すれば良いです。

| 種別           | 修飾子               | サブクラスの実装                       |
| -------------- | -------------------- | -------------------------------------- |
| 抽象メソッド   | `abstract`           | 必須（実装しないとコンパイルエラー）   |
| フックメソッド | なし（具象メソッド） | 任意（デフォルト動作をそのまま使える） |

<a id="深堀り4"></a>

## 【深堀り④】リスコフの置換原則（LSP）

本記事の実装を振り返ると、実行クラスではそれぞれの具体的な実装クラスを`NotificationSender` 型で扱っており、どのサブクラスを使っても `send()` の動作は変わりません。

このような「スーパークラスの代わりにサブクラスを使っても、プログラムの動作が変わらないべきである」という設計は、**LSP（Liskov Substitution Principle：リスコフの置換原則）**と呼ばれる設計原則の実践です。<br>
Template Methodパターンは LSP を実現するための設計手段の一つと言えます。

詳しくは「LSP」や「リスコフの置換原則」で検索してみてください。

<a id="深堀り5"></a>

## 【深堀り⑤】サブクラスの責任

正しい実装のサブクラスを見ると`validate`・`buildMessage`・`sendNotification`・`logResult` の各メソッドをオーバーライドしていることがわかります。<br>
何当たり前のことを言っているのかと思うかもしれません。また、先程の4つのメソッドを抽象メソッドとして定めている`NotificationSender` を継承しているのだから、サブクラスでオーバーライドすることは当然だと感じると思います。

ここで気がついて欲しいことは、上記は全てサブクラスの視点で考えているということです。<br>
ここで視点を変えてスーパークラスの気持ちになって考えてみましょう。<br>
スーパークラスである`NotificationSender` が`validate`・`buildMessage`・`sendNotification`・`logResult` を `abstract` で宣言しているということは、次のことをプログラムを通じて主張しています。

- サブクラスが4つの抽象メソッドを実装することを**期待している**
- サブクラスに対して、4つの抽象メソッドの実装を**要請している**

このように、サブクラスには、スーパークラスで宣言されている抽象メソッドを実装する責任が生じていると言えます。このような責任のことを「**サブクラスの責任（subclass responsibility）**」といいます。

<a id="深堀り6"></a>

## 【深堀り⑥】GoFデザインパターンとの位置づけ

今回使ったTemplate Methodパターンは、GoF（Gang of Four）の23のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
