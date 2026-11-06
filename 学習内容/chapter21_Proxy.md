# Proxy（プロキシ）パターン ― 代理オブジェクトを介して、元のオブジェクトへのアクセスを制御する

次のような経験をしたことはありませんか？

> インスタンス作成に時間のかかるオブジェクトを、必要になったときに初めて作成するようにしたら、そのオブジェクトを使うすべての箇所に、作成済みかどうかを確認する処理を書く羽目になった。おまけに、1 か所でも確認を書き忘れると、その箇所だけ作成前のオブジェクトを使ってしまい、エラーになってしまうこともあった。

この記事では、社内チャットで添付ファイルの読み込みを遅らせるシナリオを通して、Proxy パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Adapter・Decorator パターンとの違い](#深堀り1)
- [【深堀り②】Proxy の種類](#深堀り2)
    - [仮想 Proxy](#仮想-proxy)
    - [キャッシュ Proxy](#キャッシュ-proxy)
    - [アクセス Proxy](#アクセス-proxy)
    - [リモート Proxy](#リモート-proxy)
    - [代理オブジェクトのクラスを自動で作成する仕組み](#代理オブジェクトのクラスを自動で作成する仕組み)
- [【深堀り③】作成を遅らせることのデメリット](#深堀り3)
- [【深堀り④】`StorageAttachment` クラス自身に読み込みを遅らせる処理を持たせればよいのでは？](#深堀り4)
- [【深堀り⑤】複数のスレッドから同時に開く場合](#深堀り5)
- [【深堀り⑥】代理オブジェクトが作成するクラスを固定しない方法](#深堀り6)
    - [リフレクションでクラス名から作成する](#リフレクションでクラス名から作成する)
    - [`Supplier` で作り方を渡す](#supplier-で作り方を渡す)
- [【深堀り⑦】GoF デザインパターンとの位置づけ](#深堀り7)

---

## 【具体例】

### シナリオ

> あなたは社内チャットの開発チームに所属しています。<br>
> 社内チャットでは、メッセージにファイルを添付して共有できます。メッセージと、添付ファイルのファイル名・サイズはデータベース（DB）に保存されており、ファイルの中身は別のサーバー（ストレージ）に保存されています。添付ファイルは、チャンネルのメッセージ一覧と、そのチャンネルで共有されたファイルだけを並べた「ファイル」タブの一覧（以降、ファイル一覧）の 2 か所に表示され、どちらからでも開けます。<br>
> 現状の社内チャットでは、メッセージ一覧や「ファイル」タブを開くと、DB からメッセージやファイル名・サイズを読み込むだけでなく、対応するすべての添付ファイルの中身もストレージから読み込んだ後に一覧を表示しています。一覧に表示するのはファイル名とサイズだけで、実際に開かれる添付ファイルはそのうちのごく一部です。これまで社内チャットは一部の部署でしか使われず、共有されるファイルが少なかったため、問題ありませんでした。<br>
> ところが、今年に入って社内チャットを全社で使うようになると、やり取りが増えるのに伴って共有されるファイルも増え、社員から「チャンネルを開いてからメッセージが表示されるまで、何十秒も待たされる」という問い合わせが相次ぎました。<br>
> そこであなたは、これまでの仕様を見直し、一覧を表示するときには中身を読み込まず、添付ファイルが開かれたときに初めて中身を読み込むように修正することになりました。

※シナリオの社内チャットでは、DB からメッセージを読み込んだり、ストレージから添付ファイルの中身を読み込んで画面に表示したりしていますが、本記事のコードでは Proxy パターンの解説に集中するため、これらの処理をコンソールへの文字列出力で代用します。

### 既存コードの仕様

※実務では、メッセージを表す `Message` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `StorageAttachment`（既存クラス）

ストレージに保存された添付ファイルを表すクラスです。<br>
コンストラクターで、ファイル名に対応する添付ファイルの中身をストレージから読み込みます。

| フィールド | 型       | 説明                   |
| ---------- | -------- | ---------------------- |
| `fileName` | `String` | ファイル名             |
| `size`     | `int`    | ファイルのサイズ（KB） |
| `content`  | `byte[]` | 添付ファイルの中身     |

| メソッド      | 引数 | 戻り値の型 | 説明                       |
| ------------- | ---- | ---------- | -------------------------- |
| `getFileName` | なし | `String`   | ファイル名を取得する       |
| `getSize`     | なし | `int`      | ファイルのサイズを取得する |
| `open`        | なし | `void`     | 添付ファイルを開く         |

**`StorageAttachment.java`**

```java
package example;

public class StorageAttachment {
    private String fileName;
    private int size;
    private byte[] content;

    public StorageAttachment(String fileName, int size) {
        this.fileName = fileName;
        this.size = size;
        this.content = new byte[size * 1024];
        System.out.println("（ストレージから「" + fileName + "」の中身を読み込みました）");
    }

    public String getFileName() {
        return fileName;
    }

    public int getSize() {
        return size;
    }

    public void open() {
        System.out.println("「" + fileName + "」を開きました");
    }
}
```

※ストレージからの中身の読み込みは、ファイルのサイズ分の空のバイト配列を作成し、読み込んだことを表す文字列を出力することで代用しています。

<br>

- `Message`（既存クラス）

チャンネルのメッセージ 1 件を表すクラスです。

| フィールド   | 型                  | 説明         |
| ------------ | ------------------- | ------------ |
| `sender`     | `String`            | 送信者の名前 |
| `text`       | `String`            | 本文         |
| `attachment` | `StorageAttachment` | 添付ファイル |

| メソッド         | 引数 | 戻り値の型 | 説明                                                             |
| ---------------- | ---- | ---------- | ---------------------------------------------------------------- |
| `show`           | なし | `void`     | 送信者の名前・本文と、添付ファイルのファイル名・サイズを出力する |
| `openAttachment` | なし | `void`     | 添付ファイルを開く                                               |

**`Message.java`**

```java
package example;

public class Message {
    private String sender;
    private String text;
    private StorageAttachment attachment;

    public Message(String sender, String text, StorageAttachment attachment) {
        this.sender = sender;
        this.text = text;
        this.attachment = attachment;
    }

    public void show() {
        System.out.println(sender + "：" + text + " [" + attachment.getFileName() + "（" + attachment.getSize() + "KB）]");
    }

    public void openAttachment() {
        attachment.open();
    }
}
```

※実際のメッセージには投稿先のチャンネルや送信日時なども必要ですが、本記事では使わないため省略しています。

<br>

- `FileListItem`（既存クラス）

ファイル一覧の 1 行を表すクラスです。

| フィールド   | 型                  | 説明                       |
| ------------ | ------------------- | -------------------------- |
| `sharedBy`   | `String`            | ファイルを共有した人の名前 |
| `attachment` | `StorageAttachment` | 添付ファイル               |

| メソッド         | 引数 | 戻り値の型 | 説明                                                           |
| ---------------- | ---- | ---------- | -------------------------------------------------------------- |
| `show`           | なし | `void`     | 添付ファイルのファイル名・サイズと、共有した人の名前を出力する |
| `openAttachment` | なし | `void`     | 添付ファイルを開く                                             |

**`FileListItem.java`**

```java
package example;

public class FileListItem {
    private String sharedBy;
    private StorageAttachment attachment;

    public FileListItem(String sharedBy, StorageAttachment attachment) {
        this.sharedBy = sharedBy;
        this.attachment = attachment;
    }

    public void show() {
        System.out.println(attachment.getFileName() + "（" + attachment.getSize() + "KB） 共有者：" + sharedBy);
    }

    public void openAttachment() {
        attachment.open();
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
        // メッセージ一覧を開く
        System.out.println("=== メッセージ一覧 ===");
        // DB から読み込んだメッセージ一覧
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("山田", "見積書を送ります", new StorageAttachment("見積書.pdf", 2048)));
        messages.add(new Message("鈴木", "昨日の議事録です", new StorageAttachment("議事録.docx", 512)));
        messages.add(new Message("高橋", "懇親会の写真です", new StorageAttachment("集合写真.jpg", 4096)));

        // メッセージ一覧に表示する
        for (Message message: messages) {
            message.show();
        }

        // 鈴木さんのメッセージの添付ファイルを開く
        messages.get(1).openAttachment();

        // 「ファイル」タブを開く
        System.out.println("=== ファイル一覧 ===");
        // DB から読み込んだファイル一覧
        List<FileListItem> fileListItems = new ArrayList<>();
        fileListItems.add(new FileListItem("山田", new StorageAttachment("見積書.pdf", 2048)));
        fileListItems.add(new FileListItem("鈴木", new StorageAttachment("議事録.docx", 512)));
        fileListItems.add(new FileListItem("高橋", new StorageAttachment("集合写真.jpg", 4096)));

        // ファイル一覧に表示する
        for (FileListItem item: fileListItems) {
            item.show();
        }

        // 山田さんが共有したファイルを開く
        fileListItems.get(0).openAttachment();
    }
}
```

※DB からのメッセージ一覧・ファイル一覧の読み込みは、`Main` クラスでのオブジェクトの作成で代用しています。また、添付ファイルのないメッセージは省略しています。

**実行結果**

```
=== メッセージ一覧 ===
（ストレージから「見積書.pdf」の中身を読み込みました）
（ストレージから「議事録.docx」の中身を読み込みました）
（ストレージから「集合写真.jpg」の中身を読み込みました）
山田：見積書を送ります [見積書.pdf（2048KB）]
鈴木：昨日の議事録です [議事録.docx（512KB）]
高橋：懇親会の写真です [集合写真.jpg（4096KB）]
「議事録.docx」を開きました
=== ファイル一覧 ===
（ストレージから「見積書.pdf」の中身を読み込みました）
（ストレージから「議事録.docx」の中身を読み込みました）
（ストレージから「集合写真.jpg」の中身を読み込みました）
見積書.pdf（2048KB） 共有者：山田
議事録.docx（512KB） 共有者：鈴木
集合写真.jpg（4096KB） 共有者：高橋
「見積書.pdf」を開きました
```

メッセージ一覧・ファイル一覧のどちらも、一覧を表示する前に、3 件すべての添付ファイルの中身を読み込んでいます。一方、実際に開いたのは、それぞれの一覧で 1 件だけです。

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、`Message`・`FileListItem` クラスには `StorageAttachment` オブジェクトの代わりにファイル名とサイズを渡しておき、添付ファイルが開かれたときに初めて `StorageAttachment` オブジェクトを作成する、という実装ではないでしょうか？

**`Message.java`**

```java
package example;

public class Message {
    private String sender;
    private String text;
    /* ここを追加（ここから） */
    private String fileName;
    private int size;
    /* ここを追加（ここまで） */
    private StorageAttachment attachment;

    public Message(String sender, String text, String fileName, int size) { // ←ここを修正
        this.sender = sender;
        this.text = text;
        /* ここを修正（ここから） */
        this.fileName = fileName;
        this.size = size;
        /* ここを修正（ここまで） */
    }

    public void show() {
        System.out.println(sender + "：" + text + " [" + fileName + "（" + size + "KB）]"); // ←ここを修正
    }

    public void openAttachment() {
        /* ここを追加（ここから） */
        if (attachment == null) {
            attachment = new StorageAttachment(fileName, size);
        }

        /* ここを追加（ここまで） */
        attachment.open();
    }
}
```

**`FileListItem.java`**

```java
package example;

public class FileListItem {
    private String sharedBy;
    /* ここを追加（ここから） */
    private String fileName;
    private int size;
    /* ここを追加（ここまで） */
    private StorageAttachment attachment;

    public FileListItem(String sharedBy, String fileName, int size) { // ←ここを修正
        this.sharedBy = sharedBy;
        /* ここを修正（ここから） */
        this.fileName = fileName;
        this.size = size;
        /* ここを修正（ここまで） */
    }

    public void show() {
        System.out.println(fileName + "（" + size + "KB） 共有者：" + sharedBy); // ←ここを修正
    }

    public void openAttachment() {
        /* ここを追加（ここから） */
        if (attachment == null) {
            attachment = new StorageAttachment(fileName, size);
        }

        /* ここを追加（ここまで） */
        attachment.open();
    }
}
```

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        // メッセージ一覧を開く
        System.out.println("=== メッセージ一覧 ===");
        // DB から読み込んだメッセージ一覧
        List<Message> messages = new ArrayList<>();
        /* ここを修正（ここから） */
        messages.add(new Message("山田", "見積書を送ります", "見積書.pdf", 2048));
        messages.add(new Message("鈴木", "昨日の議事録です", "議事録.docx", 512));
        messages.add(new Message("高橋", "懇親会の写真です", "集合写真.jpg", 4096));
        /* ここを修正（ここまで） */

        // メッセージ一覧に表示する
        for (Message message: messages) {
            message.show();
        }

        // 鈴木さんのメッセージの添付ファイルを開く
        messages.get(1).openAttachment();

        // 「ファイル」タブを開く
        System.out.println("=== ファイル一覧 ===");
        // DB から読み込んだファイル一覧
        List<FileListItem> fileListItems = new ArrayList<>();
        /* ここを修正（ここから） */
        fileListItems.add(new FileListItem("山田", "見積書.pdf", 2048));
        fileListItems.add(new FileListItem("鈴木", "議事録.docx", 512));
        fileListItems.add(new FileListItem("高橋", "集合写真.jpg", 4096));
        /* ここを修正（ここまで） */

        // ファイル一覧に表示する
        for (FileListItem item: fileListItems) {
            item.show();
        }

        // 山田さんが共有したファイルを開く
        fileListItems.get(0).openAttachment();
    }
}
```

**実行結果**

```
=== メッセージ一覧 ===
山田：見積書を送ります [見積書.pdf（2048KB）]
鈴木：昨日の議事録です [議事録.docx（512KB）]
高橋：懇親会の写真です [集合写真.jpg（4096KB）]
（ストレージから「議事録.docx」の中身を読み込みました）
「議事録.docx」を開きました
=== ファイル一覧 ===
見積書.pdf（2048KB） 共有者：山田
議事録.docx（512KB） 共有者：鈴木
集合写真.jpg（4096KB） 共有者：高橋
（ストレージから「見積書.pdf」の中身を読み込みました）
「見積書.pdf」を開きました
```

一覧を表示するときには中身を読み込まず、開かれた添付ファイルだけを、開かれたときに読み込むようになっています。

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 「`StorageAttachment` オブジェクトが未作成の場合は作成する」という処理を、`Message`・`FileListItem` クラスの `openAttachment` メソッドの両方に書いている。そのため、添付ファイルを開ける社内チャットの画面を追加するたびに、同じ処理を書き写す必要がある。
    - その結果、この処理を書き写し忘れた画面で添付ファイルを開くと、必ず `NullPointerException` が発生する。
- 小さいファイルは一覧を表示するときに読み込んでおくなど、ファイルの中身の読み込みをカスタマイズしたい場合、同じ処理を書いているすべてのクラス（本記事では `Message`・`FileListItem` クラス）を修正する必要がある。
    - その結果、一部のクラスだけ修正し忘れると、画面によって添付ファイルを開いたときの挙動が異なるリスクがある。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Proxy パターン**です。<br>
Proxy パターンは、元のオブジェクトと同じインターフェースを持つ代理オブジェクト（Proxy）を用意し、使う側には元のオブジェクトの代わりに代理オブジェクトを渡すパターンです。本記事では、添付ファイルの中身を読み込む `StorageAttachment` オブジェクトを元のオブジェクトとし、`Message`・`FileListItem` クラスには、その代理オブジェクトを渡します。

まず、元のオブジェクトと代理オブジェクトで使用する共通のインターフェースから見ていきましょう。

**`Attachment.java`**

```java
package example;

public interface Attachment {
    String getFileName();

    int getSize();

    void open();
}
```

`Attachment` は新たに追加したインターフェースで、ファイル名・サイズを取得する抽象メソッド `getFileName`・`getSize` と、添付ファイルを開く抽象メソッド `open` を持ちます。

次に、共通のインターフェースを実装したクラスを見ていきましょう。

**`StorageAttachment.java`**

```java
package example;

public class StorageAttachment implements Attachment { // ←ここを修正
    private String fileName;
    private int size;
    private byte[] content;

    public StorageAttachment(String fileName, int size) {
        this.fileName = fileName;
        this.size = size;
        this.content = new byte[size * 1024];
        System.out.println("（ストレージから「" + fileName + "」の中身を読み込みました）");
    }

    @Override // ←ここを追加
    public String getFileName() {
        return fileName;
    }

    @Override // ←ここを追加
    public int getSize() {
        return size;
    }

    @Override // ←ここを追加
    public void open() {
        System.out.println("「" + fileName + "」を開きました");
    }
}
```

**`AttachmentProxy.java`**

```java
package example;

public class AttachmentProxy implements Attachment {
    private String fileName;
    private int size;
    private StorageAttachment realAttachment;

    public AttachmentProxy(String fileName, int size) {
        this.fileName = fileName;
        this.size = size;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public void open() {
        realize();
        realAttachment.open();
    }

    private void realize() {
        if (realAttachment == null) {
            realAttachment = new StorageAttachment(fileName, size);
        }
    }
}
```

`StorageAttachment` クラスを振り返ると、`Attachment` インターフェースを実装して全メソッドに `@Override` を付けただけです。<br>
コンストラクターや各メソッドの中身の処理は変更していません（→ [【深堀り④】`StorageAttachment` クラス自身に読み込みを遅らせる処理を持たせればよいのでは？](#深堀り4)）。

`AttachmentProxy` は新たに追加したクラスで、`StorageAttachment` オブジェクトの代わりに使う代理オブジェクトのクラスです。`fileName`・`size` フィールドに DB に保存されているファイル名とサイズを保持し、`realAttachment` フィールドに元のオブジェクトである `StorageAttachment` オブジェクトを保持します。

ファイル名とサイズを代理オブジェクトにも保持させているのは、一覧の表示に必要なのがファイル名とサイズだけで、添付ファイルを開くときに初めて `StorageAttachment` オブジェクトが必要になるからです。<br>
そこで、`getFileName`・`getSize` メソッドでは、自分が保持しているファイル名とサイズを返すことにより、代理オブジェクトだけで一覧を表示できるようにしています。<br>
また、`open` メソッドでは、`realize` メソッドで用意した `StorageAttachment` オブジェクトの `open` メソッドを呼び出すことにより、添付ファイルを開く処理を `StorageAttachment` オブジェクトに任せて（委譲して）います（→ [【深堀り①】Adapter・Decorator パターンとの違い](#深堀り1)）。<br>
`realize` メソッドでは、`realAttachment` フィールドが `null` の場合だけ `StorageAttachment` オブジェクトを作成することにより、同じ添付ファイルが何度開かれても、中身の読み込みは最初の 1 回だけで済むようにしています。2 回目以降は、作成済みの `StorageAttachment` オブジェクトをそのまま使います。

このように、Proxy パターンでは、代理オブジェクトが自分で返せる値（ファイル名とサイズ）は代理オブジェクトが返し、元のオブジェクトにしかできない処理（添付ファイルを開くこと）だけを元のオブジェクトに任せます。

次に、共通のインターフェースを実装したオブジェクトを使う側のクラスを見ていきましょう。

**`Message.java`**

```java
package example;

public class Message {
    private String sender;
    private String text;
    private Attachment attachment; // ←ここを修正

    public Message(String sender, String text, Attachment attachment) { // ←ここを修正
        this.sender = sender;
        this.text = text;
        this.attachment = attachment;
    }

    public void show() {
        System.out.println(sender + "：" + text + " [" + attachment.getFileName() + "（" + attachment.getSize() + "KB）]");
    }

    public void openAttachment() {
        attachment.open();
    }
}
```

**`FileListItem.java`**

```java
package example;

public class FileListItem {
    private String sharedBy;
    private Attachment attachment; // ←ここを修正

    public FileListItem(String sharedBy, Attachment attachment) { // ←ここを修正
        this.sharedBy = sharedBy;
        this.attachment = attachment;
    }

    public void show() {
        System.out.println(attachment.getFileName() + "（" + attachment.getSize() + "KB） 共有者：" + sharedBy);
    }

    public void openAttachment() {
        attachment.open();
    }
}
```

`Message`・`FileListItem` クラスを振り返ると、元のオブジェクトと代理オブジェクトのどちらも受け取れるよう、`attachment` フィールドとコンストラクターの引数の型を、`StorageAttachment` から `Attachment` に変更しています。<br>
コンストラクターや各メソッドの中身の処理は変更していません。

最後に、実行クラスを見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        // メッセージ一覧を開く
        System.out.println("=== メッセージ一覧 ===");
        // DB から読み込んだメッセージ一覧
        List<Message> messages = new ArrayList<>();
        /* ここを修正（ここから） */
        messages.add(new Message("山田", "見積書を送ります", new AttachmentProxy("見積書.pdf", 2048)));
        messages.add(new Message("鈴木", "昨日の議事録です", new AttachmentProxy("議事録.docx", 512)));
        messages.add(new Message("高橋", "懇親会の写真です", new AttachmentProxy("集合写真.jpg", 4096)));
        /* ここを修正（ここまで） */

        // メッセージ一覧に表示する
        for (Message message: messages) {
            message.show();
        }

        // 鈴木さんのメッセージの添付ファイルを開く
        messages.get(1).openAttachment();

        // 「ファイル」タブを開く
        System.out.println("=== ファイル一覧 ===");
        // DB から読み込んだファイル一覧
        List<FileListItem> fileListItems = new ArrayList<>();
        /* ここを修正（ここから） */
        fileListItems.add(new FileListItem("山田", new AttachmentProxy("見積書.pdf", 2048)));
        fileListItems.add(new FileListItem("鈴木", new AttachmentProxy("議事録.docx", 512)));
        fileListItems.add(new FileListItem("高橋", new AttachmentProxy("集合写真.jpg", 4096)));
        /* ここを修正（ここまで） */

        // ファイル一覧に表示する
        for (FileListItem item: fileListItems) {
            item.show();
        }

        // 山田さんが共有したファイルを開く
        fileListItems.get(0).openAttachment();
    }
}
```

**実行結果**

```
=== メッセージ一覧 ===
山田：見積書を送ります [見積書.pdf（2048KB）]
鈴木：昨日の議事録です [議事録.docx（512KB）]
高橋：懇親会の写真です [集合写真.jpg（4096KB）]
（ストレージから「議事録.docx」の中身を読み込みました）
「議事録.docx」を開きました
=== ファイル一覧 ===
見積書.pdf（2048KB） 共有者：山田
議事録.docx（512KB） 共有者：鈴木
集合写真.jpg（4096KB） 共有者：高橋
（ストレージから「見積書.pdf」の中身を読み込みました）
「見積書.pdf」を開きました
```

`Main` クラスを振り返ると、`Message`・`FileListItem` クラスが `Attachment` インターフェースを実装したオブジェクトを受け取れるようになったため、`StorageAttachment` オブジェクトの代わりに `AttachmentProxy` オブジェクトを渡しています。

実行結果は、好ましくない実装とまったく同じになっています。

以上のような実装を行うと、以下のメリットがあります。

- 「`StorageAttachment` オブジェクトが未作成の場合は作成する」という処理が、`AttachmentProxy` クラスの `realize` メソッドのみにまとまっている。また、代理オブジェクトと元のオブジェクトの両方が `Attachment` インターフェースを実装しているため、使う側の `Message`・`FileListItem` クラスは、具体的なクラスを意識することなく `Attachment` 型として扱える。そのため、添付ファイルを開ける社内チャットの画面を追加しても、`open` メソッドを呼び出すだけで済み、同じ処理を書き写す必要がない。
    - その結果、処理の書き写し忘れがなくなり、`NullPointerException` が発生しなくなる。
- 小さいファイルは一覧を表示するときに読み込んでおくなど、ファイルの中身の読み込みをカスタマイズしたい場合、`AttachmentProxy` クラスだけを修正すればよくなる。
    - その結果、一部のクラスだけ修正し忘れて、画面によって添付ファイルを開いたときの挙動が異なるリスクがなくなる。

## まとめ

正しい実装を振り返ると、`Message`・`FileListItem` クラスには、`Attachment` インターフェースを実装した代理オブジェクト（`AttachmentProxy` オブジェクト）を渡しています。また、元のオブジェクトである `StorageAttachment` オブジェクトは、添付ファイルが開かれたときに初めて `AttachmentProxy` クラスの `realize` メソッドで作成され、代理オブジェクトを介して `open` メソッドが呼び出されるようになっています。<br>
このように Proxy パターンは、元のオブジェクトの代わりに、同じインターフェースを持つ代理オブジェクトを使う側に渡し、代理オブジェクトを介して元のオブジェクトを使わせることで、必要になるまで元のオブジェクトの作成を遅らせたり、元のオブジェクトへのアクセスを制御したりする設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Adapter・Decorator パターンとの違い

正しい実装の `AttachmentProxy` クラスを振り返ると、`StorageAttachment` クラスと同じ `Attachment` インターフェースを実装し、`open` メソッドの処理を `StorageAttachment` オブジェクトに委譲していました。<br>
このように「別のオブジェクトを包んで、処理を委譲する」構造は、Adapter パターンや Decorator パターンにも共通しています。3 つのパターンの違いは、包む目的にあります。

| パターン  | 包む相手とのインターフェース | 包む目的                           |
| --------- | ---------------------------- | ---------------------------------- |
| Adapter   | 異なる                       | 合わないインターフェースを合わせる |
| Decorator | 同じ                         | 包む相手に機能を上乗せする         |
| Proxy     | 同じ                         | 包む相手の作成やアクセスを制御する |

例えば、ストレージを提供する社外のライブラリが、`Attachment` インターフェースとは異なる `download(String path)` のようなメソッドしか持っていない場合、そのライブラリを包んで `Attachment` インターフェースに合わせるのが Adapter パターンです。<br>
また、画像の添付ファイルを開くときに「社外秘」の透かしを重ねて表示する処理を、`StorageAttachment` オブジェクトを包んで上乗せするのが Decorator パターンです。

Decorator パターンと Proxy パターンは、どちらも包む相手と同じインターフェースを持つため、コードの形はよく似ています。違いは、包む相手との関係にあります。<br>
Decorator パターンでは、包む相手を外から受け取り、何重にも重ねて機能を上乗せしていきます。一方、Proxy パターンでは、`AttachmentProxy` クラスの `realize` メソッドのように、代理オブジェクトが包む相手を自分で作成することが多く、使う側は包む相手の存在を意識しません。<br>
詳しくは「Adapter パターン」「Decorator パターン」で検索してみてください。

<a id="深堀り2"></a>

## 【深堀り②】Proxy の種類

本記事の `AttachmentProxy` クラスは、`StorageAttachment` オブジェクトの作成を必要になるまで遅らせる代理オブジェクトでした。<br>
Proxy パターンは、代理オブジェクトが何を引き受けるかによって、いくつかの種類に分けられます。ここでは、実務で触れる機会が多いものから順に紹介します。

### 仮想 Proxy

インスタンス作成に時間のかかる元のオブジェクトについて、作成を必要になるまで遅らせる代理オブジェクトです。本記事の `AttachmentProxy` クラスがこれにあたります。

実務では、JPA（Java で DB を扱うための標準仕様）の実装である Hibernate の遅延読み込みで使われています。例えば、注文を DB から取得したときに、関連する顧客の情報はすぐには読み込まず、代わりに代理オブジェクトを入れておきます。顧客の名前などが使われたときに、代理オブジェクトが初めて DB から顧客の情報を読み込みます。顧客の ID のように注文の情報から分かるものは、`AttachmentProxy` クラスがファイル名を自分で返したのと同じく、代理オブジェクトが DB を読まずに返します。

### キャッシュ Proxy

同じ呼び出しに対する元のオブジェクトの処理結果を代理オブジェクトが保持しておき、2 回目以降は元のオブジェクトに処理させずに、保持しておいた結果を返す代理オブジェクトです。<br>
例えば、画像の添付ファイルからサムネイル（縮小画像）を作る処理に時間がかかる場合、一度作ったサムネイルを代理オブジェクトが保持しておき、2 回目以降はそれを返します。

仮想 Proxy も、一度作成した元のオブジェクトを保持し続けるため、キャッシュ Proxy と似ています。しかし、仮想 Proxy の目的は元のオブジェクトの作成を遅らせることで、キャッシュ Proxy の目的は元のオブジェクトの処理結果を使い回すことです。

実務では、Spring の `@Cacheable` で使われています。メソッドに `@Cacheable` を付けると、Spring がそのクラスの代理オブジェクトを作り、同じ引数で 2 回目以降に呼び出されたときは、元のメソッドを実行せずに保持しておいた結果を返します。

### アクセス Proxy

元のオブジェクトに処理を任せる前に、呼び出したユーザーにその権限があるかを確認する代理オブジェクトです（保護 Proxy とも呼ばれます）。<br>
例えば、メンバーだけが参加できる鍵付きチャンネルの添付ファイルは、メンバー以外の社員が開こうとした場合、代理オブジェクトは元のオブジェクトに処理を任せず、開けないようにします。

実務では、Spring Security の `@PreAuthorize` で使われています。メソッドに `@PreAuthorize` を付けると、代理オブジェクトが呼び出したユーザーの権限を確認し、権限がなければ元のメソッドを実行せずに例外を投げます。

### リモート Proxy

元のオブジェクトが別のサーバーにあるのに、手元のオブジェクトと同じようにメソッドを呼び出せるようにする代理オブジェクトです。サーバーとの通信は代理オブジェクトが引き受けるため、使う側は通信を意識せずに済みます。<br>
例えば、本記事のストレージとの通信を代理オブジェクトが引き受ければ、使う側は手元の `Attachment` 型のオブジェクトと同じように `open` メソッドを呼び出すだけで済みます。

実務では、別のサーバーの Web API を、インターフェースのメソッドを呼び出すだけで使えるようにする OpenFeign などのライブラリで使われています。古くは、Java 標準の Java RMI でも使われています。

### 代理オブジェクトのクラスを自動で作成する仕組み

ここまでで紹介したフレームワークやライブラリは、代理オブジェクトのクラスを開発者に書かせず、Java 標準ライブラリの `java.lang.reflect.Proxy`（または同様の仕組みを持つライブラリ）を使って、実行時に自動で作成しています。そのため、普段は意識していなくても、実務では Proxy パターンを使っていることが多いです。<br>
詳しくは「Java 動的プロキシ」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】作成を遅らせることのデメリット

正しい実装を振り返ると、添付ファイルの中身は、開かれたときに初めて読み込まれるようになりました。<br>
ただし、一覧が表示されるまでの待ち時間がなくなっただけで、中身の読み込みにかかる時間そのものがなくなったわけではありません。そのため、作成を遅らせることには次のようなデメリットがあります。

- 添付ファイルを開いたときに待たされる。既存コードでは一覧を表示する前に読み込みが終わっていたため、開いたときにはすぐに表示されていた。正しい実装では、最初に開いたときに、中身の読み込みを待つことになる。2 回目以降は、作成済みの `StorageAttachment` オブジェクトを使うため待たされない。
- 中身を読み込めないことに気づくのが、開いたときまで遅れる。既存コードでは、ストレージの障害などで中身を読み込めない場合、一覧を表示する時点で分かっていた。正しい実装では、一覧は問題なく表示されるため、その添付ファイルを開くまで気づけない。
    - その結果、一覧が表示されたのに添付ファイルを開くと失敗する、という形で問題が表れるため、利用者が混乱したり、障害の発見が遅れたりするリスクがある。

本記事では、実際に開かれる添付ファイルがごく一部なので、デメリットよりも、一覧が表示されるまでの待ち時間がなくなるメリットのほうが大きくなります。<br>
一方、すべての添付ファイルをまとめて ZIP ファイルでダウンロードする処理のように、作成したものを必ずすべて使う場合は、作成を遅らせても待ち時間の合計は変わらず、デメリットだけが残ります。<br>
作成を遅らせるかどうかは、作成したものが実際に使われる割合を考えて判断しましょう。

<a id="深堀り4"></a>

## 【深堀り④】`StorageAttachment` クラス自身に読み込みを遅らせる処理を持たせればよいのでは？

正しい実装の `StorageAttachment` クラスを振り返ると、コンストラクターや各メソッドの中身の処理は変更せず、読み込みを遅らせる処理は `AttachmentProxy` クラスに持たせていました。

では、代理オブジェクトを用意せずに、`StorageAttachment` クラス自身に読み込みを遅らせる処理を持たせた場合はどうなるでしょうか？

既存コードの `StorageAttachment` クラスを、次のように書き換えてみます。

**`StorageAttachment.java`**

```java
package example;

public class StorageAttachment {
    private String fileName;
    private int size;
    private byte[] content;

    public StorageAttachment(String fileName, int size) {
        this.fileName = fileName;
        this.size = size;
    }

    public String getFileName() {
        return fileName;
    }

    public int getSize() {
        return size;
    }

    public void open() {
        load(); // ←ここを追加
        System.out.println("「" + fileName + "」を開きました");
    }

    /* ここを追加（ここから） */
    private void load() {
        if (content == null) {
            content = new byte[size * 1024];
            System.out.println("（ストレージから「" + fileName + "」の中身を読み込みました）");
        }
    }
    /* ここを追加（ここまで） */
}
```

`StorageAttachment` クラスを振り返ると、コンストラクターで行っていた中身の読み込みを、新たに追加した `load` メソッドに移し、`open` メソッドから呼び出すようにしています。<br>
この実装でも、一覧を表示するときには中身を読み込まず、同じ添付ファイルが何度開かれても、中身の読み込みは最初の 1 回だけで済みます。また、使う側の `Message`・`FileListItem` クラスは既存コードのままでよく、`null` の確認を書き写す必要もありません。

そのため、「それなら、代理オブジェクトを用意しなくてもよいのでは？」と思うかもしれません。

しかし、この実装では、`StorageAttachment` オブジェクトを使うすべての処理で、中身の読み込みが遅れるようになります。<br>
例えば、深堀り③で挙げた、すべての添付ファイルをまとめて ZIP ファイルでダウンロードする処理は、作成を遅らせてもデメリットだけが残る処理です。この処理でも `StorageAttachment` オブジェクトを使う場合、読み込みを遅らせたくなくても、作成した時点では中身を読み込めません。<br>
一方、元のオブジェクトと代理オブジェクトを分けた正しい実装では、`StorageAttachment` オブジェクトは作成された時点で必ず中身を持っています。そのため、ZIP ファイルでダウンロードする処理では、代理オブジェクトを通さずに `StorageAttachment` オブジェクトを直接作成すれば、作成した時点で中身を読み込めます。

このように、元のオブジェクトと代理オブジェクトを分けておけば、元のオブジェクトの処理を変えずに、読み込みを遅らせたい場面では代理オブジェクトを、遅らせたくない場面では元のオブジェクトを直接使う、という使い分けができます。

<a id="深堀り5"></a>

## 【深堀り⑤】複数のスレッドから同時に開く場合

正しい実装の `AttachmentProxy` クラスの `realize` メソッドを振り返ると、`realAttachment` フィールドが `null` かどうかを確認し、`null` であれば `StorageAttachment` オブジェクトを作成していました。<br>
本記事の `Main` クラスは 1 つの処理の流れで実行していますが、実際のチャットのサーバーは、複数の社員の操作を同時に処理するため、操作ごとに別々の処理の流れで進めます。このように同時に進める処理の流れの 1 つ 1 つを「スレッド」と呼びます。

また、実務では、チャンネルを開くたびに DB からメッセージを読み込み直さないよう、読み込んだメッセージをサーバーのメモリに保持しておき、そのチャンネルを開いた社員全員で使い回すことがあります。この場合、同じ `AttachmentProxy` オブジェクトを複数のスレッドが使うことになります。<br>
例えば、山田さんと鈴木さんが、同じ添付ファイルをほぼ同時に初めて開いた場合を考えてみましょう。

1. 山田さんの操作を処理するスレッド 1 が `realize` メソッドで `realAttachment` フィールドを確認する
2. `StorageAttachment` オブジェクトはまだ作成されていないため、スレッド 1 は `null` だと判定する
3. スレッド 1 が `StorageAttachment` オブジェクトを作成して `realAttachment` フィールドに代入する前に、鈴木さんの操作を処理するスレッド 2 が `realAttachment` フィールドを確認する
4. `realAttachment` フィールドにはまだ代入されていないため、スレッド 2 も `null` だと判定する
5. 2 つのスレッドが、それぞれ `StorageAttachment` オブジェクトを作成し、`realAttachment` フィールドに代入する

このように、あるスレッドが `realAttachment` フィールドを確認してから代入するまでの間に他のスレッドが割り込むと、同じ添付ファイルの中身をストレージから 2 回読み込むことになります。先に代入された `StorageAttachment` オブジェクトは後から代入されたもので上書きされるため、1 回分の読み込みは無駄になります。

これを防ぐ案として、`realize` メソッドに `synchronized` を付ける方法があります。

**`AttachmentProxy.java`（一部抜粋）**

```java
package example;

public class AttachmentProxy implements Attachment {
    private synchronized void realize() { // ←ここを修正
        if (realAttachment == null) {
            realAttachment = new StorageAttachment(fileName, size);
        }
    }
}
```

※フィールド・コンストラクター・`getFileName`・`getSize`・`open` メソッドは変更がないため省略しています。

`synchronized` を付けたメソッドには、同時に 1 つのスレッドしか入れません。つまり、スレッド 1 が `realize` メソッドを実行している間は、他のスレッドが入れないよう鍵がかかった状態（ロック）になり、スレッド 2 はスレッド 1 が `realize` メソッドを抜けるまで待たされます。<br>
これにより、スレッド 2 は、スレッド 1 が `StorageAttachment` オブジェクトを `realAttachment` フィールドに代入し終えてから確認するため、作成済みのオブジェクトをそのまま使えます。

> ※`synchronized` は直訳すると「同期」ですが、「同期処理・非同期処理」の同期とは意味が異なります。

ここで、「`StorageAttachment` オブジェクトを保持している `realAttachment` フィールドに `synchronized` を付ければよいのでは？」と考えるかもしれません。しかし、`synchronized` はメソッドかブロック（`synchronized (オブジェクト) { ... }`）にしか付けられず、フィールドに付けるとコンパイルエラーになります。<br>
また、ブロックを使って `synchronized (realAttachment) { ... }` と書いた場合も、うまくいきません。ブロックの `synchronized` は、丸括弧の中に指定したオブジェクトを鍵として使います。ところが、`realAttachment` フィールドは `StorageAttachment` オブジェクトを作成するまで `null` なので鍵として使えず、ブロックに入ろうとした時点で `NullPointerException` が発生します。<br>
そのため、本記事のように `realize` メソッドに `synchronized` を付けて、`AttachmentProxy` オブジェクト自身を鍵として使うようにします。

なお、本記事の `Main` クラスのように 1 つのスレッドで実行する場合は、他のスレッドが割り込むことがないため、`synchronized` を付けても付けなくても結果は変わりません。

複数のスレッドから同じ代理オブジェクトを使う場合は、元のオブジェクトが作成済みかを確認してから作成するまでの間に、他のスレッドが割り込めない設計にするようにしましょう。<br>
詳しくは「Java スレッドセーフ」で検索してみてください。

<a id="深堀り6"></a>

## 【深堀り⑥】代理オブジェクトが作成するクラスを固定しない方法

正しい実装の `AttachmentProxy` クラスを振り返ると、`realize` メソッドで `new StorageAttachment(fileName, size)` と書いており、作成するクラスが `StorageAttachment` クラスに固定されていました。

例えば、ストレージを社外のクラウドサービスに移行することになり、その中身を読み込む `CloudAttachment` クラスを新しく作った場合を考えてみましょう。移行期間中は、古い添付ファイルは `StorageAttachment` オブジェクトで、新しい添付ファイルは `CloudAttachment` オブジェクトで読み込む必要があります。<br>
ところが、`AttachmentProxy` クラスは `StorageAttachment` オブジェクトを直接作成しているため、このままでは `CloudAttachment` オブジェクトを作成できません。元のオブジェクトのクラスごとに代理オブジェクトのクラスを作ると、`realize` メソッド以外はまったく同じクラスが増えていきます。

そこで、`AttachmentProxy` クラスが作成するクラスを固定せずに済む方法を 2 つ紹介します。

### リフレクションでクラス名から作成する

1 つ目は、作成するクラスの名前を文字列で受け取り、リフレクション（実行時にクラスの情報を調べて操作する仕組み）で元のオブジェクトを作成する方法です。

**`AttachmentProxy.java`（一部抜粋）**

```java
package example;

public class AttachmentProxy implements Attachment {
    private String fileName;
    private int size;
    /* ここを修正（ここから） */
    private Attachment realAttachment;
    private String className;
    /* ここを修正（ここまで） */

    public AttachmentProxy(String fileName, int size, String className) { // ←ここを修正
        this.fileName = fileName;
        this.size = size;
        this.className = className; // ←ここを追加
    }

    private void realize() {
        if (realAttachment == null) {
            /* ここを修正（ここから） */
            try {
                realAttachment = (Attachment) Class.forName(className)
                        .getDeclaredConstructor(String.class, int.class)
                        .newInstance(fileName, size);
            } catch (ClassNotFoundException e) {
                System.out.println("クラス " + className + " が見つかりません");
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
            /* ここを修正（ここまで） */
        }
    }
}
```

※`getFileName`・`getSize`・`open` メソッドは変更がないため省略しています。

`Class.forName` メソッドで文字列のクラス名からクラスを探して、`getDeclaredConstructor` メソッドで引数の型が `String`・`int` のコンストラクターを取り出し、`newInstance` メソッドでそのコンストラクターを実行しています。<br>
`realAttachment` フィールドの型も `Attachment` に変えたため、`AttachmentProxy` クラスのコードからは `StorageAttachment` クラスの名前が消えています。使う側は、次のように作成するクラスの名前を渡します。

```java
new AttachmentProxy("見積書.pdf", 2048, "example.StorageAttachment")
```

ここで注意が必要なのは、クラス名はパッケージ名を含めた名前（完全修飾名）で渡す必要があることです。例えば、`"StorageAttachment"` とクラス名だけを渡して添付ファイルを開くと、次のようになります。

```
クラス StorageAttachment が見つかりません
Exception in thread "main" java.lang.NullPointerException: Cannot invoke "example.Attachment.open()" because "this.realAttachment" is null
```

クラスが見つからなくても、`catch` ブロックで文字列を出力した後に処理が続くため、`realAttachment` フィールドは `null` のままです。その状態で `open` メソッドが `realAttachment` フィールドの `open` メソッドを呼び出すため、`NullPointerException` が発生します。<br>
このように、エラーの原因（クラス名の誤り）とエラーが発生した場所（`open` メソッド）が離れているため、原因を突き止めにくくなります。また、クラス名は文字列なので、綴りを誤ってもコンパイルエラーにならず、実行して初めて誤りに気づきます。IDE でクラス名を変更しても、文字列のクラス名は書き換わりません。

### `Supplier` で作り方を渡す

2 つ目は、元のオブジェクトの作り方そのものを `Supplier` で受け取る方法です。`Supplier` は Java 標準ライブラリのインターフェースで、「引数を受け取らず、値を 1 つ返す処理」を表します。<br>
正しい実装の `AttachmentProxy` クラスを、次のように修正します。

**`AttachmentProxy.java`（一部抜粋）**

```java
package example;

public class AttachmentProxy implements Attachment {
    private String fileName;
    private int size;
    /* ここを修正（ここから） */
    private Attachment realAttachment;
    private Supplier<Attachment> factory;
    /* ここを修正（ここまで） */

    public AttachmentProxy(String fileName, int size, Supplier<Attachment> factory) { // ←ここを修正
        this.fileName = fileName;
        this.size = size;
        this.factory = factory; // ←ここを追加
    }

    private void realize() {
        if (realAttachment == null) {
            realAttachment = factory.get(); // ←ここを修正
        }
    }
}
```

※`getFileName`・`getSize`・`open` メソッドは変更がないため省略しています。

使う側は、次のようにラムダ式で元のオブジェクトの作り方を渡します。

```java
new AttachmentProxy("見積書.pdf", 2048, () -> new StorageAttachment("見積書.pdf", 2048))
```

ラムダ式で渡した処理は、渡した時点では実行されず、`realize` メソッドで `factory.get()` を呼び出したときに初めて実行されます。そのため、元のオブジェクトの作成は、これまでどおり最初に開かれたときまで遅らせられます。<br>
`AttachmentProxy` クラスは `Supplier<Attachment>` 型の `factory` フィールドを呼び出すだけなので、作成するクラスを知らずに済みます。`CloudAttachment` オブジェクトを作成させたい場合は、使う側が `() -> new CloudAttachment(...)` を渡すだけで済みます。<br>
また、リフレクションと違い、クラス名の綴りやコンストラクターの引数を誤るとコンパイルエラーになるため、実行する前に誤りに気づけます。

代理オブジェクトが作成するクラスを固定したくない場合は、まず `Supplier` で作り方を渡す方法を検討しましょう。リフレクションは、設定ファイルから読み込んだクラス名で作成するクラスを切り替えたい場合のように、コンパイルする時点では作成するクラスが決まらない場面に限って使うのがよいでしょう。<br>
詳しくは「Java Supplier ラムダ式」や「Java リフレクション」で検索してみてください。

<a id="深堀り7"></a>

## 【深堀り⑦】GoF デザインパターンとの位置づけ

今回使った Proxy パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「構造パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
