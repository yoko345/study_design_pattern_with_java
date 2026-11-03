# Proxy（プロキシ）パターン ― 代理人を立てて、本人へのアクセスを管理する

次のような経験をしたことはありませんか？

> 作るのに時間のかかるオブジェクトを、実際に使うかどうかに関係なく最初にすべて作っていたら、数が増えるにつれて、画面が表示されるまで長く待たされるようになった。そこで、使う直前に作るように書き換えたところ、今度は「まだ作っていなければ作る」という同じ処理を、そのオブジェクトを使うクラスのそれぞれに書き写す羽目になった。

この記事では、社内チャットで添付ファイルの中身を開くときまで読み込まないようにするシナリオを通して、Proxy パターンがこの問題をどのように解決するかを紹介します。

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
    - [代理人のクラスを自動で作成する仕組み](#代理人のクラスを自動で作成する仕組み)
- [【深堀り③】作成を遅らせることのデメリット](#深堀り3)
- [【深堀り④】複数のスレッドから同時に開く場合](#深堀り4)
- [【深堀り⑤】代理人に本人のクラスを知らせない方法](#深堀り5)
    - [リフレクションでクラス名から作成する](#リフレクションでクラス名から作成する)
    - [Supplier で作り方を渡す](#supplier-で作り方を渡す)
- [【深堀り⑥】GoF デザインパターンとの位置づけ](#深堀り6)

---

## 【具体例】

### シナリオ

> あなたは社内チャットの開発チームに所属しています。<br>
> 社内チャットでは、メッセージにファイルを添付して共有できます。添付ファイルのファイル名とサイズはメッセージと一緒にデータベース（DB）に保存されており、ファイルの中身は別のサーバー（ストレージ）に保存されています。添付ファイルは、チャンネルのメッセージ一覧と、そのチャンネルで共有されたファイルだけを並べた「ファイル」タブの一覧の 2 か所に表示され、どちらからでも開けます。<br>
> 現状の社内チャットでは、メッセージ一覧や「ファイル」タブを開くと、表示する添付ファイルすべての中身をストレージから読み込んでから、一覧を表示しています。ストレージからの読み込みには、1 件あたり 1 秒ほどかかります。社内チャットを導入した当初は、ファイルはメールに添付して送ることが多く、チャットで共有されることはまれだったため、これで問題になっていませんでした。<br>
> ところが、今年に入って社内のファイル共有をメールからチャットに切り替えたところ、社員から「チャンネルを開いてからメッセージが表示されるまで、何十秒も待たされる」という問い合わせが相次ぎました。調べてみると、一覧に表示しているのはファイル名とサイズだけなのに、表示する添付ファイルすべての中身を、一覧を表示する前に読み込んでいることが原因でした。実際に開かれる添付ファイルは、そのうちのごく一部です。<br>
> そこであなたは、一覧を表示するときには中身を読み込まず、添付ファイルが開かれたときに初めて中身を読み込むように修正することになりました。

※実際の社内チャットでは、DB からメッセージを読み込んだり、ストレージから添付ファイルの中身を読み込んで画面に表示したりする実装が必要ですが、本記事では Proxy パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、メッセージを表す `Message` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `StoredAttachment`（既存クラス）

ストレージに保存された添付ファイルを表すクラスです。<br>
コンストラクターで、ファイル名に対応する添付ファイルの中身をストレージから読み込みます。

| フィールド | 型       | 説明                       |
| ---------- | -------- | -------------------------- |
| `fileName` | `String` | ファイル名                 |
| `size`     | `int`    | ファイルのサイズ（KB）     |
| `content`  | `byte[]` | 添付ファイルの中身         |

| メソッド      | 引数 | 戻り値の型 | 説明                       |
| ------------- | ---- | ---------- | -------------------------- |
| `getFileName` | なし | `String`   | ファイル名を取得する       |
| `getSize`     | なし | `int`      | ファイルのサイズを取得する |
| `open`        | なし | `void`     | 添付ファイルを開く         |

**`StoredAttachment.java`**

```java
package example;

public class StoredAttachment {
    private String fileName;
    private int size;
    private byte[] content;

    public StoredAttachment(String fileName, int size) {
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

| フィールド   | 型                 | 説明             |
| ------------ | ------------------ | ---------------- |
| `sender`     | `String`           | 送信者の名前     |
| `text`       | `String`           | 本文             |
| `attachment` | `StoredAttachment` | 添付ファイル     |

| メソッド         | 引数 | 戻り値の型 | 説明                                                                 |
| ---------------- | ---- | ---------- | -------------------------------------------------------------------- |
| `show`           | なし | `void`     | 送信者の名前・本文と、添付ファイルのファイル名・サイズを出力する     |
| `openAttachment` | なし | `void`     | 添付ファイルを開く                                                   |

**`Message.java`**

```java
package example;

public class Message {
    private String sender;
    private String text;
    private StoredAttachment attachment;

    public Message(String sender, String text, StoredAttachment attachment) {
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

<br>

- `FileListItem`（既存クラス）

「ファイル」タブの一覧の 1 行を表すクラスです。

| フィールド   | 型                 | 説明                       |
| ------------ | ------------------ | -------------------------- |
| `sharedBy`   | `String`           | ファイルを共有した人の名前 |
| `attachment` | `StoredAttachment` | 添付ファイル               |

| メソッド | 引数 | 戻り値の型 | 説明                                                             |
| -------- | ---- | ---------- | ---------------------------------------------------------------- |
| `show`   | なし | `void`     | 添付ファイルのファイル名・サイズと、共有した人の名前を出力する   |
| `open`   | なし | `void`     | 添付ファイルを開く                                               |

**`FileListItem.java`**

```java
package example;

public class FileListItem {
    private String sharedBy;
    private StoredAttachment attachment;

    public FileListItem(String sharedBy, StoredAttachment attachment) {
        this.sharedBy = sharedBy;
        this.attachment = attachment;
    }

    public void show() {
        System.out.println(attachment.getFileName() + "（" + attachment.getSize() + "KB） 共有者：" + sharedBy);
    }

    public void open() {
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
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("山田", "見積書を送ります", new StoredAttachment("見積書.pdf", 2048)));
        messages.add(new Message("鈴木", "昨日の議事録です", new StoredAttachment("議事録.docx", 512)));
        messages.add(new Message("高橋", "懇親会の写真です", new StoredAttachment("集合写真.jpg", 4096)));

        for (Message message: messages) {
            message.show();
        }

        // 鈴木さんのメッセージの添付ファイルを開く
        messages.get(1).openAttachment();

        // 「ファイル」タブを開く
        System.out.println("=== ファイル一覧 ===");
        List<FileListItem> fileList = new ArrayList<>();
        fileList.add(new FileListItem("山田", new StoredAttachment("見積書.pdf", 2048)));
        fileList.add(new FileListItem("鈴木", new StoredAttachment("議事録.docx", 512)));
        fileList.add(new FileListItem("高橋", new StoredAttachment("集合写真.jpg", 4096)));

        for (FileListItem item: fileList) {
            item.show();
        }

        // 山田さんが共有したファイルを開く
        fileList.get(0).open();
    }
}
```

※DB からのメッセージの読み込みは、`Main` クラスでのオブジェクトの作成で代用しています。また、添付ファイルのないメッセージは省略しています。

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

メッセージ一覧・ファイル一覧のどちらも、一覧を表示する前に、3 件すべての添付ファイルの中身を読み込んでいます。一方、実際に開いたのは、それぞれの一覧で 1 件だけです。<br>
ストレージからの読み込みは 1 件あたり 1 秒ほどかかるため、一覧に表示する添付ファイルが 30 件あれば、一覧が表示されるまで 30 秒ほど待たされることになります。

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

真っ先に思いつくのは、`Message`・`FileListItem` クラスには `StoredAttachment` オブジェクトの代わりにファイル名とサイズを渡しておき、添付ファイルが開かれたときに初めて `StoredAttachment` オブジェクトを作成する、という実装ではないでしょうか？

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
    private StoredAttachment attachment;

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
            attachment = new StoredAttachment(fileName, size);
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
    private StoredAttachment attachment;

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

    public void open() {
        /* ここを追加（ここから） */
        if (attachment == null) {
            attachment = new StoredAttachment(fileName, size);
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
        List<Message> messages = new ArrayList<>();
        /* ここを修正（ここから） */
        messages.add(new Message("山田", "見積書を送ります", "見積書.pdf", 2048));
        messages.add(new Message("鈴木", "昨日の議事録です", "議事録.docx", 512));
        messages.add(new Message("高橋", "懇親会の写真です", "集合写真.jpg", 4096));
        /* ここを修正（ここまで） */

        for (Message message: messages) {
            message.show();
        }

        // 鈴木さんのメッセージの添付ファイルを開く
        messages.get(1).openAttachment();

        // 「ファイル」タブを開く
        System.out.println("=== ファイル一覧 ===");
        List<FileListItem> fileList = new ArrayList<>();
        /* ここを修正（ここから） */
        fileList.add(new FileListItem("山田", "見積書.pdf", 2048));
        fileList.add(new FileListItem("鈴木", "議事録.docx", 512));
        fileList.add(new FileListItem("高橋", "集合写真.jpg", 4096));
        /* ここを修正（ここまで） */

        for (FileListItem item: fileList) {
            item.show();
        }

        // 山田さんが共有したファイルを開く
        fileList.get(0).open();
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

一覧を表示するときには中身を読み込まず、開かれた添付ファイルだけを、開かれたときに読み込むようになりました。

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 「まだ作成していなければ `StoredAttachment` オブジェクトを作成する」という同じ処理を、`Message` クラスの `openAttachment` メソッドと `FileListItem` クラスの `open` メソッドの両方に書いている。そのため、添付ファイルを開ける画面を追加するたびに同じ処理を書き写す必要があるうえ、中身の読み込み方を変えるときも、書き写したすべての箇所を修正する必要がある。
    - その結果、書き写すときに `null` かどうかの確認を書き忘れたり、一部の箇所だけ修正し忘れたりすると、画面によって添付ファイルを開いたときの動きが異なったり、`NullPointerException` が発生したりするリスクがある。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

この問題を解決するのが **Proxy パターン**です。<br>
Proxy パターンは、あるオブジェクト（本人）の代わりに、本人と同じように使える代理人（Proxy）を立てるパターンです。本記事では、添付ファイルの中身を読み込む `StoredAttachment` オブジェクトを本人とし、`Message`・`FileListItem` クラスには、本人の代わりに代理人を渡します。

まず、本人と代理人に共通のインターフェースから見ていきましょう。

**`Attachment.java`**

```java
package example;

public interface Attachment {
    String getFileName();

    int getSize();

    void open();
}
```

`Attachment` は新たに追加したインターフェースで、ファイル名・サイズを取得する抽象メソッド `getFileName`・`getSize` と、添付ファイルを開く抽象メソッド `open` を持ちます。<br>
本人と代理人の両方がこのインターフェースを実装することで、使う側は、本人と代理人を同じ `Attachment` 型として扱えるようになります。

次に、本人となるクラスを見ていきましょう。

**`StoredAttachment.java`**

```java
package example;

public class StoredAttachment implements Attachment { // ←ここを修正
    private String fileName;
    private int size;
    private byte[] content;

    public StoredAttachment(String fileName, int size) {
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

`StoredAttachment` クラスは、`Attachment` インターフェースを実装して `@Override` を付けただけで、コンストラクターで中身を読み込む処理を含め、それ以外は変更していません。

続いて、代理人となるクラスを見ていきましょう。

**`AttachmentProxy.java`**

```java
package example;

public class AttachmentProxy implements Attachment {
    private String fileName;
    private int size;
    private StoredAttachment realAttachment;

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
            realAttachment = new StoredAttachment(fileName, size);
        }
    }
}
```

`AttachmentProxy` は新たに追加したクラスで、`StoredAttachment` クラスの代理人です。`fileName`・`size` フィールドに DB に保存されているファイル名とサイズを保持し、`realAttachment` フィールドに本人である `StoredAttachment` オブジェクトを保持します（最初は `null` です）。<br>
`getFileName`・`getSize` メソッドでは、自分が保持しているファイル名とサイズを返すことにより、本人を作成せずに答えています。これにより、一覧を表示するときに中身が読み込まれることはありません。<br>
`open` メソッドでは、`realize` メソッドで本人を用意したうえで、本人の `open` メソッドに処理を任せる（委譲する）ことにより、中身が必要な処理だけを本人に行わせています（→ [【深堀り①】Adapter・Decorator パターンとの違い](#深堀り1)）。<br>
`realize` メソッドでは、`realAttachment` フィールドが `null` の場合だけ `StoredAttachment` オブジェクトを作成することにより、中身を読み込むのを最初に開かれたときの 1 回だけにしています。2 回目以降に開かれたときは、作成済みの本人をそのまま使います。

このように、Proxy パターンでは、代理人が自分で答えられること（ファイル名とサイズ）は代理人が答え、本人にしかできないこと（中身を開くこと）だけを本人に任せます。

なお、`StoredAttachment` クラス自身に「まだ読み込んでいなければ読み込む」処理を持たせる方法も考えられます。しかし、本人と代理人を分けておけば、`StoredAttachment` オブジェクトは作成された時点で必ず中身を持っている、という状態のまま保てます。また、読み込みを遅らせる必要がない場面では、代理人を通さずに `StoredAttachment` オブジェクトを直接使うこともできます。

次に、添付ファイルを使う側のクラスを見ていきましょう。

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

    public void open() {
        attachment.open();
    }
}
```

`Message`・`FileListItem` クラスは、`attachment` フィールドとコンストラクターの引数の型を、`StoredAttachment` から `Attachment` に修正しただけです。<br>
`show` メソッドや、添付ファイルを開く `openAttachment`・`open` メソッドは既存コードから変更しておらず、相手が代理人か本人かを気にせずに `Attachment` 型のメソッドを呼び出しています。

最後に、実行クラスを見ていきましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        // メッセージ一覧を開く
        System.out.println("=== メッセージ一覧 ===");
        List<Message> messages = new ArrayList<>();
        /* ここを修正（ここから） */
        messages.add(new Message("山田", "見積書を送ります", new AttachmentProxy("見積書.pdf", 2048)));
        messages.add(new Message("鈴木", "昨日の議事録です", new AttachmentProxy("議事録.docx", 512)));
        messages.add(new Message("高橋", "懇親会の写真です", new AttachmentProxy("集合写真.jpg", 4096)));
        /* ここを修正（ここまで） */

        for (Message message: messages) {
            message.show();
        }

        // 鈴木さんのメッセージの添付ファイルを開く
        messages.get(1).openAttachment();

        // 「ファイル」タブを開く
        System.out.println("=== ファイル一覧 ===");
        List<FileListItem> fileList = new ArrayList<>();
        /* ここを修正（ここから） */
        fileList.add(new FileListItem("山田", new AttachmentProxy("見積書.pdf", 2048)));
        fileList.add(new FileListItem("鈴木", new AttachmentProxy("議事録.docx", 512)));
        fileList.add(new FileListItem("高橋", new AttachmentProxy("集合写真.jpg", 4096)));
        /* ここを修正（ここまで） */

        for (FileListItem item: fileList) {
            item.show();
        }

        // 山田さんが共有したファイルを開く
        fileList.get(0).open();
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

実行結果は、好ましくない実装とまったく同じになっています。<br>
一覧を表示するときには中身を読み込まないため、一覧は中身の読み込みを待たずに表示されます。また、一度も開かれない添付ファイル（メッセージ一覧の「見積書.pdf」「集合写真.jpg」など）は、最後まで中身が読み込まれません。

以上のような実装を行うと、以下のメリットがあります。

- 「まだ作成していなければ本人を作成する」処理を `AttachmentProxy` クラスの `realize` メソッドの 1 か所にまとめたため、使う側の `Message`・`FileListItem` クラスは、相手が代理人か本人かを気にせず、`Attachment` 型のメソッドを呼び出すだけで済む。そのため、添付ファイルを開ける画面を追加しても同じ処理を書き写す必要はなく、中身の読み込み方を変えるときも `AttachmentProxy` クラスだけを修正すればよい。
    - その結果、`null` かどうかの確認の書き忘れや修正漏れにより、画面によって添付ファイルを開いたときの動きが異なったり、`NullPointerException` が発生したりするリスクを減らせる。

## まとめ

正しい実装を振り返ると、`Message`・`FileListItem` クラスには、本人（`StoredAttachment` オブジェクト）の代わりに代理人（`AttachmentProxy` オブジェクト）を渡しています。代理人はファイル名とサイズを自分で答え、添付ファイルが開かれたときに初めて本人を作成して、処理を任せています。<br>
このように Proxy パターンは、本人と同じように使える代理人を立て、代理人で答えられることは代理人が答え、本人にしかできないことだけを本人に任せることで、使う側を変えずに、本人の作成を必要になるまで遅らせたり、本人へのアクセスを管理したりする設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】Adapter・Decorator パターンとの違い

正しい実装の `AttachmentProxy` クラスを振り返ると、本人と同じ `Attachment` インターフェースを実装し、`open` メソッドの処理を本人に委譲していました。<br>
このように「別のオブジェクトを包んで、処理を委譲する」構造は、Adapter パターンや Decorator パターンにも共通しています。3 つのパターンの違いは、包む目的にあります。

| パターン  | 包む相手とのインターフェース | 包む目的                                       |
| --------- | ---------------------------- | ---------------------------------------------- |
| Adapter   | 異なる                       | 合わないインターフェースを合わせる             |
| Decorator | 同じ                         | 包む相手に機能を上乗せする                     |
| Proxy     | 同じ                         | 本人の作成や、本人へのアクセスを管理する       |

例えば、ストレージを提供する社外のライブラリが、`Attachment` インターフェースとは異なる `download(String path)` のようなメソッドしか持っていない場合、そのライブラリを包んで `Attachment` インターフェースに合わせるのが Adapter パターンです。<br>
また、画像の添付ファイルを開くときに「社外秘」の透かしを重ねて表示する処理を、本人を包んで上乗せするのが Decorator パターンです。

Decorator パターンと Proxy パターンは、どちらも包む相手と同じインターフェースを持つため、コードの形はよく似ています。違いは、包む相手との関係にあります。<br>
Decorator パターンでは、包む相手を外から受け取り、何重にも重ねて機能を上乗せしていきます。一方、Proxy パターンでは、`AttachmentProxy` クラスの `realize` メソッドのように、代理人が本人を自分で作成することが多く、使う側は本人の存在を意識しません。<br>
詳しくは「Adapter パターン」「Decorator パターン」で検索してみてください。

<a id="深堀り2"></a>

## 【深堀り②】Proxy の種類

本記事の `AttachmentProxy` クラスは、本人の作成を必要になるまで遅らせる代理人でした。<br>
Proxy パターンは、代理人が何を引き受けるかによって、いくつかの種類に分けられます。ここでは、実務で触れる機会が多いものから順に紹介します。

### 仮想 Proxy

作成に時間のかかる本人の作成を、本人が必要になるまで遅らせる代理人です。本記事の `AttachmentProxy` クラスがこれにあたります。

実務では、JPA（Java で DB を扱うための標準仕様）の実装である Hibernate の遅延読み込みで使われています。例えば、注文を DB から取得したときに、関連する顧客の情報はすぐには読み込まず、代わりに代理人を入れておきます。顧客の名前などが使われたときに、代理人が初めて DB から顧客の情報を読み込みます。顧客の ID のように注文の情報から分かるものは、`AttachmentProxy` クラスがファイル名を自分で答えたのと同じく、代理人が DB を読まずに答えます。

### キャッシュ Proxy

同じ要求に対する本人の処理の結果を代理人が保持しておき、2 回目以降は本人に処理させずに、保持しておいた結果を返す代理人です。<br>
例えば、画像の添付ファイルからサムネイル（縮小画像）を作る処理に時間がかかる場合、一度作ったサムネイルを代理人が保持しておき、2 回目以降はそれを返します。

仮想 Proxy も、一度作成した本人を保持し続けるため、キャッシュ Proxy と似ています。しかし、仮想 Proxy の目的は本人の作成を遅らせることで、キャッシュ Proxy の目的は本人の処理の結果を使い回すことです。

実務では、Spring の `@Cacheable` で使われています。メソッドに `@Cacheable` を付けると、Spring がそのクラスの代理人を作り、同じ引数で 2 回目以降に呼び出されたときは、本人のメソッドを実行せずに保持しておいた結果を返します。

### アクセス Proxy

本人に処理を任せる前に、呼び出した人にその権限があるかを代理人が確認する代理人です（保護 Proxy とも呼ばれます）。<br>
例えば、メンバーだけが参加できる鍵付きチャンネルの添付ファイルは、メンバー以外の社員が開こうとした場合、代理人が本人に処理を任せずに断ります。

実務では、Spring Security の `@PreAuthorize` で使われています。メソッドに `@PreAuthorize` を付けると、代理人が呼び出した人の権限を確認し、権限がなければ本人のメソッドを実行せずに例外を投げます。

### リモート Proxy

本人が別のサーバーにあるのに、手元のオブジェクトと同じようにメソッドを呼び出せるようにする代理人です。サーバーとの通信は代理人が引き受けるため、使う側は通信を意識せずに済みます。<br>
例えば、本記事のストレージとの通信を代理人が引き受ければ、使う側は手元の `Attachment` 型のオブジェクトと同じように `open` メソッドを呼び出すだけで済みます。

実務では、別のサーバーの Web API を、インターフェースのメソッドを呼び出すだけで使えるようにする OpenFeign などのライブラリで使われています。古くは、Java 標準の Java RMI でも使われています。

### 代理人のクラスを自動で作成する仕組み

ここまでで紹介したフレームワークやライブラリは、代理人のクラスを開発者に書かせず、Java 標準ライブラリの `java.lang.reflect.Proxy`（または同様の仕組みを持つライブラリ）を使って、実行時に自動で作成しています。そのため、普段は意識していなくても、実務では Proxy パターンを使っていることが多いです。<br>
詳しくは「Java 動的プロキシ」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】作成を遅らせることのデメリット

正しい実装を振り返ると、添付ファイルの中身は、開かれたときに初めて読み込まれるようになりました。<br>
ただし、一覧が表示されるまでの待ち時間がなくなっただけで、中身の読み込みにかかる時間そのものがなくなったわけではありません。そのため、作成を遅らせることには次のようなデメリットがあります。

- 添付ファイルを開いたときに待たされる。既存コードでは一覧を表示する前に読み込みが終わっていたため、開いたときにはすぐに表示されていた。正しい実装では、最初に開いたときに、中身の読み込み（1 件あたり 1 秒ほど）を待つことになる。2 回目以降は、作成済みの本人を使うため待たされない。
- 中身を読み込めないことに気づくのが、開いたときまで遅れる。既存コードでは、ストレージの障害などで中身を読み込めない場合、一覧を表示する時点で分かっていた。正しい実装では、一覧は問題なく表示されるため、その添付ファイルを開くまで気づけない。
    - その結果、一覧が表示されたのに添付ファイルを開くと失敗する、という形で問題が表れるため、利用者が混乱したり、障害の発見が遅れたりするリスクがある。

本記事では、実際に開かれる添付ファイルがごく一部なので、デメリットよりも、一覧が表示されるまでの待ち時間がなくなるメリットのほうが大きくなります。<br>
一方、すべての添付ファイルをまとめて ZIP ファイルでダウンロードする処理のように、作成したものを必ずすべて使う場合は、作成を遅らせても待ち時間の合計は変わらず、デメリットだけが残ります。<br>
作成を遅らせるかどうかは、作成したものが実際に使われる割合を考えて判断しましょう。

<a id="深堀り4"></a>

## 【深堀り④】複数のスレッドから同時に開く場合

正しい実装の `AttachmentProxy` クラスの `realize` メソッドを振り返ると、`realAttachment` フィールドが `null` かどうかを確認し、`null` であれば `StoredAttachment` オブジェクトを作成していました。<br>
本記事の `Main` クラスは 1 つの処理の流れで実行していますが、実際のチャットのサーバーは、複数の社員の操作を同時に処理するため、操作ごとに別々の処理の流れで進めます。このように同時に進める処理の流れの 1 つ 1 つを「スレッド」と呼びます。

また、実務では、チャンネルを開くたびに DB からメッセージを読み込み直さないよう、読み込んだメッセージをサーバーのメモリに保持しておき、そのチャンネルを開いた社員全員で使い回すことがあります。この場合、同じ `AttachmentProxy` オブジェクトを複数のスレッドが使うことになります。<br>
例えば、山田さんと鈴木さんが、同じ添付ファイルをほぼ同時に初めて開いた場合を考えてみましょう。

1. 山田さんの操作を処理するスレッド 1 が `realize` メソッドで `realAttachment` フィールドを確認する
2. 本人はまだ作成されていないため、スレッド 1 は `null` だと判定する
3. スレッド 1 が `StoredAttachment` オブジェクトを作成して `realAttachment` フィールドに代入する前に、鈴木さんの操作を処理するスレッド 2 が `realAttachment` フィールドを確認する
4. `realAttachment` フィールドにはまだ代入されていないため、スレッド 2 も `null` だと判定する
5. 2 つのスレッドが、それぞれ `StoredAttachment` オブジェクトを作成し、`realAttachment` フィールドに代入する

このように、あるスレッドが `realAttachment` フィールドを確認してから代入するまでの間に他のスレッドが割り込むと、同じ添付ファイルの中身をストレージから 2 回読み込むことになります。先に代入された本人は後から代入された本人で上書きされるため、1 回分の読み込みは無駄になります。

これを防ぐ案として、`realize` メソッドに `synchronized` を付ける方法があります。

**`AttachmentProxy.java`（一部抜粋）**

```java
package example;

public class AttachmentProxy implements Attachment {
    private synchronized void realize() { // ←ここを修正
        if (realAttachment == null) {
            realAttachment = new StoredAttachment(fileName, size);
        }
    }
}
```

※フィールド・コンストラクター・`getFileName`・`getSize`・`open` メソッドは変更がないため省略しています。

`synchronized` を付けたメソッドには、同時に 1 つのスレッドしか入れません。つまり、スレッド 1 が `realize` メソッドを実行している間は、他のスレッドが入れないよう鍵がかかった状態（ロック）になり、スレッド 2 はスレッド 1 が `realize` メソッドを抜けるまで待たされます。<br>
これにより、スレッド 2 は、スレッド 1 が本人を `realAttachment` フィールドに代入し終えてから確認するため、作成済みの本人をそのまま使えます。

> ※`synchronized` は直訳すると「同期」ですが、「同期処理・非同期処理」の同期とは意味が異なります。

ここで、「本人を保持している `realAttachment` フィールドに `synchronized` を付ければよいのでは？」と考えるかもしれません。しかし、`synchronized` はメソッドかブロック（`synchronized (オブジェクト) { ... }`）にしか付けられず、フィールドに付けるとコンパイルエラーになります。<br>
また、ブロックを使って `synchronized (realAttachment) { ... }` と書いた場合も、うまくいきません。ブロックの `synchronized` は、丸括弧の中に指定したオブジェクトを鍵として使います。ところが、`realAttachment` フィールドは本人を作成するまで `null` なので鍵として使えず、ブロックに入ろうとした時点で `NullPointerException` が発生します。<br>
そのため、本記事のように `realize` メソッドに `synchronized` を付けて、`AttachmentProxy` オブジェクト自身を鍵として使うようにします。

なお、本記事の `Main` クラスのように 1 つのスレッドで実行する場合は、他のスレッドが割り込むことがないため、`synchronized` を付けても付けなくても結果は変わりません。

複数のスレッドから同じ代理人を使う場合は、本人が作成済みかを確認してから作成するまでの間に、他のスレッドが割り込めない設計にするようにしましょう。<br>
詳しくは「Java スレッドセーフ」で検索してみてください。

<a id="深堀り5"></a>

## 【深堀り⑤】代理人に本人のクラスを知らせない方法

正しい実装の `AttachmentProxy` クラスを振り返ると、`realize` メソッドで `new StoredAttachment(fileName, size)` と書いており、代理人が本人のクラスを直接知っていました。

例えば、ストレージを社外のクラウドサービスに移行することになり、その中身を読み込む `CloudAttachment` クラスを新しく作った場合を考えてみましょう。移行期間中は、古い添付ファイルは `StoredAttachment` オブジェクトで、新しい添付ファイルは `CloudAttachment` オブジェクトで読み込む必要があります。<br>
ところが、`AttachmentProxy` クラスは `StoredAttachment` オブジェクトを直接作成しているため、このままでは `CloudAttachment` オブジェクトの代理人になれません。本人のクラスごとに代理人のクラスを作ると、`realize` メソッド以外はまったく同じクラスが増えていきます。

そこで、代理人が本人のクラスを知らずに済む方法を 2 つ紹介します。

### リフレクションでクラス名から作成する

1 つ目は、本人のクラス名を文字列で受け取り、リフレクション（実行時にクラスの情報を調べて操作する仕組み）で本人を作成する方法です。

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
`realAttachment` フィールドの型も `Attachment` に変えたため、`AttachmentProxy` クラスのコードからは `StoredAttachment` クラスの名前が消えています。使う側は、次のように本人のクラス名を渡します。

```java
new AttachmentProxy("見積書.pdf", 2048, "example.StoredAttachment")
```

ここで注意が必要なのは、クラス名はパッケージ名を含めた名前（完全修飾名）で渡す必要があることです。例えば、`"StoredAttachment"` とクラス名だけを渡して添付ファイルを開くと、次のようになります。

```
クラス StoredAttachment が見つかりません
Exception in thread "main" java.lang.NullPointerException: Cannot invoke "example.Attachment.open()" because "this.realAttachment" is null
```

クラスが見つからなくても、`catch` ブロックで文字列を出力した後に処理が続くため、`realAttachment` フィールドは `null` のままです。その状態で `open` メソッドが本人の `open` メソッドを呼び出すため、`NullPointerException` が発生します。<br>
このように、エラーの原因（クラス名の誤り）とエラーが発生した場所（`open` メソッド）が離れているため、原因を突き止めにくくなります。また、クラス名は文字列なので、綴りを誤ってもコンパイルエラーにならず、実行して初めて誤りに気づきます。IDE でクラス名を変更しても、文字列のクラス名は書き換わりません。

### Supplier で作り方を渡す

2 つ目は、本人の作り方そのものを `Supplier` で受け取る方法です。`Supplier` は Java 標準ライブラリのインターフェースで、「引数を受け取らず、値を 1 つ返す処理」を表します。<br>
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

使う側は、次のようにラムダ式で本人の作り方を渡します。

```java
new AttachmentProxy("見積書.pdf", 2048, () -> new StoredAttachment("見積書.pdf", 2048))
```

ラムダ式で渡した処理は、渡した時点では実行されず、`realize` メソッドで `factory.get()` を呼び出したときに初めて実行されます。そのため、本人の作成は、これまでどおり最初に開かれたときまで遅らせられます。<br>
`AttachmentProxy` クラスは `Supplier<Attachment>` 型の `factory` フィールドを呼び出すだけなので、本人のクラスを知らずに済みます。`CloudAttachment` オブジェクトの代理人にしたい場合は、使う側が `() -> new CloudAttachment(...)` を渡すだけで済みます。<br>
また、リフレクションと違い、クラス名の綴りやコンストラクターの引数を誤るとコンパイルエラーになるため、実行する前に誤りに気づけます。

代理人に本人のクラスを知らせたくない場合は、まず `Supplier` で作り方を渡す方法を検討しましょう。リフレクションは、設定ファイルから読み込んだクラス名で本人を切り替えたい場合のように、コンパイルする時点では本人のクラスが決まらない場面に限って使うのがよいでしょう。<br>
詳しくは「Java Supplier ラムダ式」や「Java リフレクション」で検索してみてください。

<a id="深堀り6"></a>

## 【深堀り⑥】GoF デザインパターンとの位置づけ

今回使った Proxy パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「構造パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
