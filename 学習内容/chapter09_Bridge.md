# Bridge（ブリッジ）パターン ― 抽象化と実装を分離し、独立して拡張する

次のような経験をしたことはありませんか？

> 既存の複数の機能に対して、後から具体的な処理を行う実装を追加しようとしたら、機能の種類と実装の組み合わせの数だけクラスを用意する羽目になった。<br>
> その結果、実装を 1 つ追加するだけなのに、既存の機能の数だけクラスを複製しなければならなくなった。

この記事では、社内向け通知システムに Slack 通知と緊急時の繰り返し送信機能を追加するシナリオを通して、Bridge パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Template Method パターンとの複合](#深堀り1)
- [【深堀り②】Adapter パターンとの関係](#深堀り2)
- [【深堀り③】OCP（オープン・クローズドの原則）](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは社内向け通知システムの開発チームに所属しています。<br>
> 現在、障害発生時の通知はメールでの送信のみに対応しています。<br>
> ある日、大規模なシステム障害が発生した際、Slack を主要な連絡手段とする部署がメールの受信に気づくのが遅れ、対応が大幅に遅れるという事態が起きました。<br>
> この反省を踏まえ、経営陣から「メールに加えて Slack でも通知できるようにすること」「緊急時には同じ内容を複数回連続送信できるようにすること」を満たす通知システムへの改善を求められました。

※実際のメール送信・Slack 送信では、それぞれ SMTP サーバーや Slack API との通信を行う実装が必要ですが、本記事では Bridge パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `EmailNotification` のような外部サービス（メール送信）と連携するクラスは `infrastructure` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `EmailNotification`（既存クラス）

通知内容をメールとして送信するクラスです。<br>
宛先メールアドレス・件名・本文を保持し、SMTP サーバーへの接続から切断までの一連の処理を行います。

| フィールド       | 型       | 説明               |
| ---------------- | -------- | ------------------ |
| `recipientEmail` | `String` | 宛先メールアドレス |
| `subject`        | `String` | 件名               |
| `body`           | `String` | 本文               |

| メソッド | 戻り値の型 | 説明                                             |
| -------- | ---------- | ------------------------------------------------ |
| `send`   | `void`     | 通知を送信する（接続 → 送信 → 切断の一連の処理） |

**`EmailNotification.java`**

```java
package example;

public class EmailNotification {
    private String recipientEmail;
    private String subject;
    private String body;

    public EmailNotification(String recipientEmail, String subject, String body) {
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.body = body;
    }

    private void connect() {
        System.out.println("[Email] SMTPサーバーに接続：" + recipientEmail);
    }

    private void transmit() {
        System.out.println("[Email] 件名：" + subject + " / 本文：" + body);
    }

    private void disconnect() {
        System.out.println("[Email] 接続を切断しました");
    }

    public void send() {
        connect();
        transmit();
        disconnect();
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
        EmailNotification notification = new EmailNotification("suzuki@example.com", "定期ヘルスチェック結果のお知らせ", "全システム正常に稼働しています。");
        notification.send();
    }
}
```

**実行結果**

```
[Email] SMTPサーバーに接続：suzuki@example.com
[Email] 件名：定期ヘルスチェック結果のお知らせ / 本文：全システム正常に稼働しています。
[Email] 接続を切断しました
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、Slack 用に `SlackNotification` クラスを複製し、緊急通知用にそれぞれの継承クラス（`UrgentEmailNotification`・`UrgentSlackNotification`）を追加する、という実装ではないでしょうか？

**`SlackNotification.java`**

```java
package example;

public class SlackNotification {
    private String channelName;
    private String message;

    public SlackNotification(String channelName, String message) {
        this.channelName = channelName;
        this.message = message;
    }

    private void connect() {
        System.out.println("[Slack] Webhookへ接続：#" + channelName);
    }

    private void transmit() {
        System.out.println("[Slack] メッセージ：" + message);
    }

    private void disconnect() {
        System.out.println("[Slack] 接続を切断しました");
    }

    public void send() {
        connect();
        transmit();
        disconnect();
    }
}
```

**`UrgentEmailNotification.java`**

```java
package example;

public class UrgentEmailNotification extends EmailNotification {
    public UrgentEmailNotification(String recipientEmail, String subject, String body) {
        super(recipientEmail, subject, body);
    }

    public void sendRepeatedly(int times) {
        for (int i = 0; i < times; i++) {
            send();
        }
    }
}
```

**`UrgentSlackNotification.java`**

```java
package example;

public class UrgentSlackNotification extends SlackNotification {
    public UrgentSlackNotification(String channelName, String message) {
        super(channelName, message);
    }

    public void sendRepeatedly(int times) {
        for (int i = 0; i < times; i++) {
            send();
        }
    }
}
```

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        EmailNotification notification = new EmailNotification("suzuki@example.com", "定期ヘルスチェック結果のお知らせ", "全システム正常に稼働しています。");
        notification.send();

        /* ここを追加（ここから） */
        System.out.println("");

        SlackNotification slackNotification = new SlackNotification("general", "全システム正常に稼働しています。");
        slackNotification.send();

        System.out.println("");

        UrgentEmailNotification urgentEmail = new UrgentEmailNotification("suzuki@example.com", "【緊急】本番サーバー障害", "本番サーバーで障害が発生しました。至急対応してください。");
        urgentEmail.sendRepeatedly(3);

        System.out.println("");

        UrgentSlackNotification urgentSlack = new UrgentSlackNotification("infra-alert", "本番サーバーで障害が発生しました。至急対応してください。");
        urgentSlack.sendRepeatedly(3);
        /* ここを追加（ここまで） */
    }
}
```

**実行結果**

```
[Email] SMTPサーバーに接続：suzuki@example.com
[Email] 件名：定期ヘルスチェック結果のお知らせ / 本文：全システム正常に稼働しています。
[Email] 接続を切断しました

[Slack] Webhookへ接続：#general
[Slack] メッセージ：全システム正常に稼働しています。
[Slack] 接続を切断しました

[Email] SMTPサーバーに接続：suzuki@example.com
[Email] 件名：【緊急】本番サーバー障害 / 本文：本番サーバーで障害が発生しました。至急対応してください。
[Email] 接続を切断しました
[Email] SMTPサーバーに接続：suzuki@example.com
[Email] 件名：【緊急】本番サーバー障害 / 本文：本番サーバーで障害が発生しました。至急対応してください。
[Email] 接続を切断しました
[Email] SMTPサーバーに接続：suzuki@example.com
[Email] 件名：【緊急】本番サーバー障害 / 本文：本番サーバーで障害が発生しました。至急対応してください。
[Email] 接続を切断しました

[Slack] Webhookへ接続：#infra-alert
[Slack] メッセージ：本番サーバーで障害が発生しました。至急対応してください。
[Slack] 接続を切断しました
[Slack] Webhookへ接続：#infra-alert
[Slack] メッセージ：本番サーバーで障害が発生しました。至急対応してください。
[Slack] 接続を切断しました
[Slack] Webhookへ接続：#infra-alert
[Slack] メッセージ：本番サーバーで障害が発生しました。至急対応してください。
[Slack] 接続を切断しました
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 通知の手段（メール・Slack）と通知の機能（通常・緊急）の組み合わせごとにクラスが必要になり、クラス数が「手段の数 × 機能の数」で増えていく。
    - **通知の手段の追加**：例えば、SMS 通知を追加する場合を考えてみましょう。手段の数が 2 から 3 に増えるため、`SmsNotification` と `UrgentSmsNotification` の 2 クラスを新たに追加しなければなりません。これにより、クラス数は 2 手段 × 2 機能 = 4 から、3 手段 × 2 機能 = 6 に増加してしまう。
    - **通知の機能の追加**：例えば、定期リマインド通知を追加する場合を考えてみましょう。機能の数が 2 から 3 に増えるため、`ReminderEmailNotification` と `ReminderSlackNotification` の 2 クラスを新たに追加しなければなりません。これにより、クラス数は 2 手段 × 2 機能 = 4 から、2 手段 × 3 機能 = 6 に増加してしまう。
- `UrgentEmailNotification` と `UrgentSlackNotification` の `sendRepeatedly` メソッドはまったく同じ処理であるにもかかわらず、継承元のクラスが異なるため共通化できず、コードが重複している。
    - 繰り返し送信のロジックを変更したい場合、`Urgent` が付くクラスすべてを個別に修正する必要があり、修正漏れが起きやすい。
- `sendRepeatedly` メソッドは、継承元の `send` メソッドをそのまま繰り返し呼び出しているだけのため、送信のたびに接続・切断を繰り返してしまう。
    - 実務では、接続・切断を繰り返すとサーバーに負荷がかかるため、1 セッションにつき接続・切断は 1 回だけ行い、そのセッションの中で送信だけを繰り返すのが望ましい。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Bridge パターン**です。<br>
「通知の手段（どうやって送るか）」と「通知の機能（何を行うか）」を、それぞれ独立した継承階層に分離し、両者を委譲でつなぐことで、手段と機能を自由に組み合わせられるようにします。

まず、通知の手段側から見ていきましょう。

**`NotificationChannel.java`**

```java
package example;

public abstract class NotificationChannel {
    public abstract void rawConnect();

    public abstract void rawSend();

    public abstract void rawDisconnect();
}
```

`NotificationChannel` は新たに追加した抽象クラスで、通知手段の共通の抽象メソッドを定義しています。<br>
また、接続・送信・切断を担う `rawConnect`・`rawSend`・`rawDisconnect` メソッドを抽象メソッドとすることで、具体的な通信処理をサブクラスに委ねています。

次に、通知の手段側の具体的な実装を見ていきましょう。

**`EmailNotificationChannel.java`**

```java
package example;

public class EmailNotificationChannel extends NotificationChannel {
    private String recipientEmail;
    private String subject;
    private String body;

    public EmailNotificationChannel(String recipientEmail, String subject, String body) {
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.body = body;
    }

    @Override
    public void rawConnect() {
        System.out.println("[Email] SMTPサーバーに接続：" + recipientEmail);
    }

    @Override
    public void rawSend() {
        System.out.println("[Email] 件名：" + subject + " / 本文：" + body);
    }

    @Override
    public void rawDisconnect() {
        System.out.println("[Email] 接続を切断しました");
    }
}
```

**`SlackNotificationChannel.java`**

```java
package example;

public class SlackNotificationChannel extends NotificationChannel {
    private String channelName;
    private String message;

    public SlackNotificationChannel(String channelName, String message) {
        this.channelName = channelName;
        this.message = message;
    }

    @Override
    public void rawConnect() {
        System.out.println("[Slack] Webhookへ接続：#" + channelName);
    }

    @Override
    public void rawSend() {
        System.out.println("[Slack] メッセージ：" + message);
    }

    @Override
    public void rawDisconnect() {
        System.out.println("[Slack] 接続を切断しました");
    }
}
```

`EmailNotificationChannel`・`SlackNotificationChannel` クラスを振り返ると、抽象クラス `NotificationChannel` を継承しています。<br>
これに伴い、メソッド名やアクセス修飾子が `NotificationChannel` の抽象メソッドに合わせて変更されています。<br>
しかし、接続・送信・切断それぞれの処理内容自体は、既存の仕様（`EmailNotification`・`SlackNotification`）から変更されていません。

次に、通知の機能側を見ていきましょう。

**`Notification.java`**

```java
package example;

public class Notification {
    private NotificationChannel channel;

    public Notification(NotificationChannel channel) {
        this.channel = channel;
    }

    protected void connect() {
        channel.rawConnect();
    }

    protected void send() {
        channel.rawSend();
    }

    protected void disconnect() {
        channel.rawDisconnect();
    }

    public final void deliver() {
        connect();
        send();
        disconnect();
    }
}
```

`Notification` クラスを振り返ると、フィールドとして `NotificationChannel` 型のインスタンスを保持し、`connect`・`send`・`disconnect` メソッドの中でその処理を委譲しています。また、`deliver` メソッドを `final` にすることで、この手順の順序をサブクラスが変更できないようにしています。<br>
つまり、`Notification` は「通知を送る」という手順（接続 → 送信 → 切断）だけを知っており、実際にどう接続し、どう送信するかという具体的な実装は通知の手段側に委ねています。<br>
なお、この「手順を固定する」という設計は Template Method パターンの考え方と重なります（→ [【深堀り①】Template Method パターンとの複合](#深堀り1)）。

次に、通知の機能側の具体的な実装を見ていきましょう。

**`UrgentNotification.java`**

```java
package example;

public class UrgentNotification extends Notification {
    public UrgentNotification(NotificationChannel channel) {
        super(channel);
    }

    public void deliverRepeatedly(int times) {
        connect();
        for (int i = 0; i < times; i++) {
            send();
        }
        disconnect();
    }
}
```

`UrgentNotification` クラスは `Notification` を継承したうえで、`deliverRepeatedly` という新しいメソッドを追加しています。<br>
`Notification` を継承しているため、`deliverRepeatedly` メソッドの中でスーパークラスの `connect`・`send`・`disconnect` メソッドをそのまま呼び出すことができます。<br>
処理の流れを見てみると、接続を 1 回行った後、送信だけを指定回数繰り返し、最後に切断を 1 回行っており、`NotificationChannel` の具体的な実装は一切意識していません。

最後に、実行クラスの実装を見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        Notification emailNotification = new Notification(
            new EmailNotificationChannel("suzuki@example.com", "定期ヘルスチェック結果のお知らせ", "全システム正常に稼働しています。")
        );
        emailNotification.deliver();

        System.out.println("");

        Notification slackNotification = new Notification(
            new SlackNotificationChannel("general", "全システム正常に稼働しています。")
        );
        slackNotification.deliver();

        System.out.println("");

        UrgentNotification urgentEmailNotification = new UrgentNotification(
            new EmailNotificationChannel("suzuki@example.com", "【緊急】本番サーバー障害", "本番サーバーで障害が発生しました。至急対応してください。")
        );
        urgentEmailNotification.deliverRepeatedly(3);

        System.out.println("");

        UrgentNotification urgentSlackNotification = new UrgentNotification(
            new SlackNotificationChannel("infra-alert", "本番サーバーで障害が発生しました。至急対応してください。")
        );
        urgentSlackNotification.deliverRepeatedly(3);
    }
}
```

**実行結果**

```
[Email] SMTPサーバーに接続：suzuki@example.com
[Email] 件名：定期ヘルスチェック結果のお知らせ / 本文：全システム正常に稼働しています。
[Email] 接続を切断しました

[Slack] Webhookへ接続：#general
[Slack] メッセージ：全システム正常に稼働しています。
[Slack] 接続を切断しました

[Email] SMTPサーバーに接続：suzuki@example.com
[Email] 件名：【緊急】本番サーバー障害 / 本文：本番サーバーで障害が発生しました。至急対応してください。
[Email] 件名：【緊急】本番サーバー障害 / 本文：本番サーバーで障害が発生しました。至急対応してください。
[Email] 件名：【緊急】本番サーバー障害 / 本文：本番サーバーで障害が発生しました。至急対応してください。
[Email] 接続を切断しました

[Slack] Webhookへ接続：#infra-alert
[Slack] メッセージ：本番サーバーで障害が発生しました。至急対応してください。
[Slack] メッセージ：本番サーバーで障害が発生しました。至急対応してください。
[Slack] メッセージ：本番サーバーで障害が発生しました。至急対応してください。
[Slack] 接続を切断しました
```

`Main` クラスを振り返ると、通知の機能側（`Notification` または `UrgentNotification`）のコンストラクタに、通知の手段側（`NotificationChannel` の実装クラス）のインスタンスを渡すだけで、通知の手段と機能を自由に組み合わせられることがわかります。

以上のような実装を行うと、以下のメリットがあります。

- 通知の手段（メール・Slack）と通知の機能（通常・緊急）が別々の継承階層に分かれたことで、クラス数は「手段の数 + 機能の数」で済むようになり、好ましくない実装のような「手段の数 × 機能の数」の掛け算にならない。
    - **通知の手段の追加**：例えば、SMS 通知を追加する場合を考えてみましょう。手段の数が 2 から 3 に増えても、通知の機能（`Notification`・`UrgentNotification`）は抽象クラス `NotificationChannel` にのみ依存しているため、この抽象クラスを実装した `SmsNotificationChannel` を 1 つ追加するだけで済みます。そのため、クラス数は 2 手段 + 2 機能 = 4 から 3 手段 + 2 機能 = 5 に留まります（好ましくない実装だと 6 に増加していた）。
    - **通知の機能の追加**：例えば、定期リマインド通知を追加する場合を考えてみましょう。機能の数が 2 から 3 に増えても、`Notification` を継承すれば `NotificationChannel` への委譲処理（接続・送信・切断）をそのまま利用できるため、`ReminderNotification` を 1 つ追加するだけで済みます。そのため、クラス数は 2 手段 + 2 機能 = 4 から 2 手段 + 3 機能 = 5 に留まります（好ましくない実装だと 6 に増加していた）。
- 緊急通知の繰り返しロジック（`deliverRepeatedly`）は `UrgentNotification` に集約すれば良く、通知手段に依存しない。
    - つまり、共通化できているため、好ましくない実装の問題点で挙げた「修正漏れが起きやすい」という心配がない。
- `UrgentNotification` クラスの `deliverRepeatedly` メソッドは接続・切断をそれぞれ 1 回だけ行い、送信だけを複数回行う実装になっており、好ましくない実装で見られた「送信のたびに接続し直す」という無駄を解消できている。

## まとめ

正しい実装を振り返ると、通知の手段側（`NotificationChannel`）と通知の機能側（`Notification`）が別々の継承階層に分かれ、コンストラクタでの委譲によって結びついています。<br>
このように、Bridge パターンは、「何を行うか（抽象化）」と「どう行うか（実装）」という 2 つの軸を分離し、それぞれを独立して拡張できるようにするパターンです。

また、通知手段や通知機能を追加する際も、新しいクラスを 1 つ追加するだけで済み、既存のクラスに変更を加える必要はありません。<br>
そのため、手段と機能の組み合わせが増えるほど、Bridge パターンの効果を実感しやすくなります。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Template Method パターンとの複合

本記事の `Notification` クラスの `deliver` メソッドを振り返ると、`connect → send → disconnect` という決まった手順を `final` メソッドとして固定し、継承したクラス（`UrgentNotification`）はこの手順自体を変更せず、そのまま利用する実装になっていました。

このように、処理の手順（骨組み）をスーパークラスで固定し、手順の中身をサブクラスや委譲先に委ねる設計は、「**Template Method パターン**」の考え方です。

ただし、Template Method パターンでは、手順の中身（本記事の `connect` などに相当する処理）を抽象メソッドとして定義し、**継承**したサブクラスにオーバーライドさせるのが基本形です。<br>
一方、本記事の `connect`・`send`・`disconnect` は具体メソッドで、`NotificationChannel` 型のフィールドへの**委譲**（本記事の `channel.rawConnect()` などに相当）によって処理を切り替えています。そのため、`connect`・`send`・`disconnect` のコード自体を書き換えることなく、コンストラクタに渡す通知の手段を変えるだけで、通知機能の中身が切り替わります。

つまり本記事の `Notification` クラスは、「手順を固定する」という点では Template Method パターンと同じ考え方を使いながら、「コンストラクタに渡す通知の手段を変えることで通知機能の中身を切り替える」という点では Bridge パターンの委譲の仕組みを使っている、という 2 つのパターンを組み合わせた構造になっています。

<a id="深堀り2"></a>

## 【深堀り②】Adapter パターンとの関係

`Notification` が `NotificationChannel` 型のフィールドを持ち、`connect`・`send`・`disconnect` の処理を委譲している構造は、「**Adapter パターン**」の「委譲を使った Adapter パターン」とも似ています。Adapter パターンにも、既存クラスを継承する版と委譲する版があり、【深堀り①】（→ [Template Method パターンとの複合](#深堀り1)）で触れた「継承か委譲か」という選択は、Adapter パターンにも共通する論点です。

ただし、両者の目的は異なります。Adapter パターンは、呼び出し側がすでに期待しているインターフェースに対して、それとは異なるインターフェースを持つ既存クラスを後から「適合（adapt）」させることが目的です。一方、Bridge パターンは、抽象化側（`Notification`）と実装側（`NotificationChannel`）のインターフェースを最初から分離した状態で設計し、両方を独立して拡張できるようにすることが目的です。`NotificationChannel` は、既存の非互換なクラスを後から合わせたものではなく、`Notification` 側とセットで最初から設計されたインターフェースです。

つまり、Adapter パターンと Bridge パターンは「委譲によって処理を切り替える」という構造は共通していますが、Adapter パターンが既存の非互換インターフェースを後から適合させるために委譲を使うのに対し、Bridge パターンは設計段階から抽象化と実装を分離するために委譲を使う、という目的の違いがあります。

<a id="深堀り3"></a>

## 【深堀り③】OCP（オープン・クローズドの原則）

正しい実装を振り返ると、通知手段を追加する際に必要だったのは、新しい `NotificationChannel` の実装クラスを追加することだけで、既存の `Notification`・`UrgentNotification`・`NotificationChannel` には一切手を加えていません。<br>
`Notification`・`UrgentNotification` が依存しているのは抽象クラス `NotificationChannel` だけであるため、具体的な通知手段が何であっても対応できます。<br>
同様に、通知機能を追加する場合も、新しい `Notification` のサブクラスを追加するだけで済み、既存の `NotificationChannel` の実装クラスや `Notification` の他のサブクラスには手を加える必要はありません。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。Bridge パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Bridge パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「構造パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
