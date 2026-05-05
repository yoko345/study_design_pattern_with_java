# Factory Method パターン ― インスタンス生成をサブクラスに委ねる

新しい種類のオブジェクトを追加するたびに、呼び出し元のコードを修正していませんか？

実務では、最初は 1 種類のオブジェクトを生成するコードでも、要件の変化とともに種類の追加を求められることは珍しくありません。そのたびに `if (type == "A")` の分岐が膨らんでいく、あるいは `new 具体クラス()` が呼び出し元のあちこちに散らばっていく経験はないでしょうか。

この記事では、企業の受付システムで来訪者パスを発行するシナリオを通して、Factory Method パターンがこの問題をどのように解決するかを学びます。さらに、同じ基盤を使って全く異なる病院の診察票システムにも応用できることを確認します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装①（new を直接書く）](#好ましくない実装1)
- [好ましくない実装②（条件分岐ファクトリ）](#好ましくない実装2)
- [正しい実装](#正しい実装)
- [別システムへの転用](#別システムへの転用)
- [まとめ](#まとめ)
- [【深堀り①】抽象クラスとインターフェースの比較](#深堀り1)
- [【深堀り②】OCP（開放閉鎖原則）](#深堀り2)
- [【深堀り③】Template Method パターンとの関係](#深堀り3)
- [【深堀り④】static ファクトリメソッドとの違い](#深堀り4)
- [【深堀り⑤】GoF デザインパターンとの位置づけ](#深堀り5)
- [【深堀り⑥】DIP（依存性逆転の原則）](#深堀り6)
- [【深堀り⑦】Iterator パターンでの Factory Method 使用例](#深堀り7)

---

## 【具体例】

### シナリオ

> あなたは企業の情報システム部門に所属しています。<br>
> 社屋への入館管理システムに、来訪者パスの発行機能を追加する開発タスクが割り振られました。<br>
> 来訪者の名前を受け取り、来訪者パスを発行して入館記録に登録するまでを担当します。

### 既存コードの仕様

- `VisitorPass`（来訪者パスクラス）

来訪者 1 名に対して 1 枚のパスを発行します。<br>
コンストラクタで来訪者名とパス番号を受け取り、発行時にコンソールへ出力します。

| フィールド    | 型       | 説明           |
| ------------- | -------- | -------------- |
| `visitorName` | `String` | 来訪者の氏名   |
| `passNumber`  | `int`    | パスの発行番号 |

| メソッド     | 説明                         |
| ------------ | ---------------------------- |
| `void use()` | パスを使ってゲートを通過する |

```Java:VisitorPass.java
public class VisitorPass {
    private String visitorName;
    private int passNumber;

    public VisitorPass(String visitorName, int passNumber) {
        System.out.println(visitorName + "の来訪者パスを" + passNumber + "番で発行します。");
        this.visitorName = visitorName;
        this.passNumber = passNumber;
    }

    public void use() {
        System.out.println(this + "でゲートを通過します。");
    }

    @Override
    public String toString() {
        return "[ 来訪者パス" + passNumber + "：" + visitorName + " ]";
    }
}
```

<br>

- `Main`（実行クラス）

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        VisitorPass pass1 = new VisitorPass("田中 太郎", 1);
        VisitorPass pass2 = new VisitorPass("山田 花子", 2);

        pass1.use();
        pass2.use();
    }
}
```

実行結果：

```
田中 太郎の来訪者パスを1番で発行します。
山田 花子の来訪者パスを2番で発行します。
[ 来訪者パス1：田中 太郎 ]でゲートを通過します。
[ 来訪者パス2：山田 花子 ]でゲートを通過します。
```

---

<a id="好ましくない実装1"></a>

## 好ましくない実装①（new を直接書く）

受付システムに加えて、病院の診察票も同じ仕組みで発行することになりました。
そこで `PatientTicket` クラスを追加し、`Main` でそのまま `new` して使ってみます。

```Java:PatientTicket.java
public class PatientTicket {
    private String patientName;
    private int ticketNumber;

    public PatientTicket(String patientName, int ticketNumber) {
        System.out.println(patientName + "の診察票を" + ticketNumber + "番で発行します。");
        this.patientName = patientName;
        this.ticketNumber = ticketNumber;
    }

    public void use() {
        System.out.println(this + "を診察室で提示します。");
    }

    @Override
    public String toString() {
        return "[ 診察票" + ticketNumber + "：" + patientName + " ]";
    }
}
```

```Java:Main.java
public class Main {
    private static int passNumber = 0;
    private static int ticketNumber = 0;

    public static void main(String[] args) {
        // 来訪者パス発行
        VisitorPass pass = new VisitorPass("田中 太郎", ++passNumber);
        System.out.println("[入館記録] " + pass + "を登録しました。");
        pass.use();

        // 診察票発行
        PatientTicket ticket = new PatientTicket("山田 花子", ++ticketNumber);
        System.out.println("[受付記録] " + ticket + "を登録しました。");
        ticket.use();
    }
}
```

**問題点：**

- 生成処理（`new`）と登録処理（`System.out.println`）が呼び出し元の `Main` に直接書かれている
- 新しい種類が増えるたびに `Main` を修正しなければならない
- 「発行 → 登録」という手順を守る責任が `Main` 側にあるため、呼び出す側が正しい手順を知っていなければならない

---

<a id="好ましくない実装2"></a>

## 好ましくない実装②（条件分岐ファクトリ）

「生成処理を 1 か所にまとめよう」と考えて、`PassFactory` という静的ファクトリメソッドを作ります。

```Java:PassFactory.java
public class PassFactory {
    private static int number = 0;

    public static void issue(String type, String name) {
        if (type.equals("visitor")) {
            VisitorPass pass = new VisitorPass(name, ++number);
            System.out.println("[入館記録] " + pass + "を登録しました。");
        } else if (type.equals("patient")) {
            PatientTicket ticket = new PatientTicket(name, ++number);
            System.out.println("[受付記録] " + ticket + "を登録しました。");
        }
    }
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        PassFactory.issue("visitor", "田中 太郎");
        PassFactory.issue("patient", "山田 花子");
    }
}
```

確かに `Main` はすっきりしました。しかし問題が残ります。

**問題点：**

- 新しい種類（例：業者バッジ）が増えるたびに `PassFactory` 自体に `else if` を追加しなければならない
- 「既存コードを変更せずに機能を拡張する」という OCP（開放閉鎖原則）に違反している
- `type` の文字列と具体クラスの対応が暗黙的で、タイポしてもコンパイルエラーにならない

---

<a id="正しい実装"></a>

## 正しい実装

Factory Method パターンでは、「何を作るか」をサブクラスに委ねます。フレームワーク側（`Factory`・`Product` の抽象クラス）が「生成 → 登録」という手順を定義し、具体的なクラスの生成はサブクラスが担います。

**クラス構成：**

```
framework パッケージ（変更しない）
  ├── Factory.java    抽象クラス：生成・登録の手順を定義
  └── Product.java    抽象クラス：発行物の共通インターフェース

visitorpass パッケージ（具体実装）
  ├── VisitorPass.java        Product のサブクラス
  └── VisitorPassFactory.java Factory のサブクラス
```

**framework 側：**

```Java:Product.java
package framework;

public abstract class Product {
    public abstract void use();
}
```

```Java:Factory.java
package framework;

public abstract class Factory {

    protected abstract Product createProduct(String owner);

    protected abstract void registerProduct(Product product);

    public final Product create(String owner) {
        Product product = createProduct(owner);
        registerProduct(product);
        return product;
    }
}
```

`create()` は `final` で宣言されているため、サブクラスが手順を変えることはできません。<br>
「生成してから登録する」という順序がフレームワーク側で保証されています。

**visitorpass 側：**

```Java:VisitorPass.java
package visitorpass;

import framework.Product;

class VisitorPass extends Product {
    private String visitorName;
    private int passNumber;

    VisitorPass(String visitorName, int passNumber) {
        System.out.println(visitorName + "の来訪者パスを" + passNumber + "番で発行します。");
        this.visitorName = visitorName;
        this.passNumber = passNumber;
    }

    @Override
    public void use() {
        System.out.println(this + "でゲートを通過します。");
    }

    @Override
    public String toString() {
        return "[ 来訪者パス" + passNumber + "：" + visitorName + " ]";
    }
}
```

```Java:VisitorPassFactory.java
package visitorpass;

import framework.Factory;
import framework.Product;

public class VisitorPassFactory extends Factory {
    private int passNumber;

    @Override
    protected Product createProduct(String owner) {
        return new VisitorPass(owner, ++passNumber);
    }

    @Override
    protected void registerProduct(Product product) {
        System.out.println("[入館記録] " + product + "を登録しました。");
    }
}
```

**呼び出し側：**

```Java:Main.java
import framework.Factory;
import framework.Product;
import visitorpass.VisitorPassFactory;

public class Main {
    public static void main(String[] args) {
        Factory factory = new VisitorPassFactory();
        Product pass1 = factory.create("田中 太郎");
        Product pass2 = factory.create("山田 花子");

        pass1.use();
        pass2.use();
    }
}
```

実行結果：

```
田中 太郎の来訪者パスを1番で発行します。
[入館記録] [ 来訪者パス1：田中 太郎 ]を登録しました。
山田 花子の来訪者パスを2番で発行します。
[入館記録] [ 来訪者パス2：山田 花子 ]を登録しました。
[ 来訪者パス1：田中 太郎 ]でゲートを通過します。
[ 来訪者パス2：山田 花子 ]でゲートを通過します。
```

`Main` は `Factory` と `Product` だけを知っていれば動作します。`VisitorPass` や `VisitorPassFactory` の内部実装を知る必要がありません。

---

<a id="別システムへの転用"></a>

## 別システムへの転用

ここで、病院の受付システムからも同じフレームワークを使いたいという要件が来たとします。`PatientTicket` と `PatientTicketFactory` を追加するだけです。**`framework` パッケージのコードは 1 行も変更しません。**

```
patientticket パッケージ（追加するだけ）
  ├── PatientTicket.java        Product のサブクラス
  └── PatientTicketFactory.java Factory のサブクラス
```

```Java:PatientTicket.java
package patientticket;

import framework.Product;

class PatientTicket extends Product {
    private String patientName;
    private int ticketNumber;

    PatientTicket(String patientName, int ticketNumber) {
        System.out.println(patientName + "の診察票を" + ticketNumber + "番で発行します。");
        this.patientName = patientName;
        this.ticketNumber = ticketNumber;
    }

    @Override
    public void use() {
        System.out.println(this + "を診察室で提示します。");
    }

    @Override
    public String toString() {
        return "[ 診察票" + ticketNumber + "：" + patientName + " ]";
    }
}
```

```Java:PatientTicketFactory.java
package patientticket;

import framework.Factory;
import framework.Product;

public class PatientTicketFactory extends Factory {
    private int ticketNumber;

    @Override
    protected Product createProduct(String owner) {
        return new PatientTicket(owner, ++ticketNumber);
    }

    @Override
    protected void registerProduct(Product product) {
        System.out.println("[受付記録] " + product + "を登録しました。");
    }
}
```

```Java:Main.java
import framework.Factory;
import framework.Product;
import patientticket.PatientTicketFactory;

public class Main {
    public static void main(String[] args) {
        Factory factory = new PatientTicketFactory();
        Product ticket1 = factory.create("鈴木 一郎");
        Product ticket2 = factory.create("伊藤 さくら");

        ticket1.use();
        ticket2.use();
    }
}
```

実行結果：

```
鈴木 一郎の診察票を1番で発行します。
[受付記録] [ 診察票1：鈴木 一郎 ]を登録しました。
伊藤 さくらの診察票を2番で発行します。
[受付記録] [ 診察票2：伊藤 さくら ]を登録しました。
[ 診察票1：鈴木 一郎 ]を診察室で提示します。
[ 診察票2：伊藤 さくら ]を診察室で提示します。
```

`framework` に手を加えることなく、全く異なるドメインのシステムへ転用できました。これが Factory Method パターンの強みです。

---

<a id="まとめ"></a>

## まとめ

Factory Method パターンは、インスタンスの生成をサブクラスに委ねることで、フレームワーク（抽象クラス）と具体的な実装を分離するパターンです。

| 観点                   | 好ましくない実装                | Factory Method パターン              |
| ---------------------- | ------------------------------- | ------------------------------------ |
| 生成処理の場所         | 呼び出し元に散在                | Factory サブクラスにカプセル化       |
| 種類の追加             | Main や条件分岐ファクトリを修正 | 新しいサブクラスを追加するだけ       |
| 手順の保証             | 呼び出し元が手順を知る必要がある | `create()` が `final` で保証         |
| 異なるシステムへの転用 | 共通基盤がないため再利用が難しい | framework を変えずにサブクラスを追加 |

**このパターンを使うべき場面：**

- 生成するオブジェクトの種類が将来増える可能性がある
- 「生成 → 登録」などの手順を確実に踏ませたい
- 生成ロジックを呼び出し元から隠蔽したい

---

<a id="深堀り1"></a>

## 【深堀り①】抽象クラスとインターフェースの比較

`Factory` と `Product` が抽象クラスで定義されているのはなぜでしょうか。

インターフェースとの違いは、**実装（メソッドの中身）を持てるかどうか**です。

| 比較項目       | 抽象クラス                           | インターフェース                     |
| -------------- | ------------------------------------ | ------------------------------------ |
| 実装の共有     | できる                               | できない（`default` メソッドを除く） |
| フィールド     | 持てる                               | 定数のみ                             |
| コンストラクタ | 持てる                               | 持てない                             |
| 継承           | 1 つのみ                             | 複数実装可能                         |

`Factory` の `create()` メソッドは、「生成 → 登録」という共通手順を持つ**具体的な実装**です。この実装をサブクラスに継承させたいため、抽象クラスが適しています。インターフェースでは `create()` に実装を持たせることができません（`default` メソッドは使えますが `final` にはできないため、手順の変更を防げません）。

---

<a id="深堀り2"></a>

## 【深堀り②】OCP（開放閉鎖原則）

OCP（Open/Closed Principle）とは、「**拡張に対してオープン、修正に対してクローズド**」という原則です。

好ましくない実装②では、診察票を追加するために `PassFactory` の `if/else` を修正しました。これは「修正に対してオープン」な状態です。

Factory Method パターンでは、`PatientTicketFactory` という新しいサブクラスを**追加するだけ**で対応できます。`framework` も `VisitorPassFactory` も一切変更しません。これが「既存コードを修正せず、新しいクラスを追加することで拡張する」OCP の実践です。

---

<a id="深堀り3"></a>

## 【深堀り③】Template Method パターンとの関係

`Factory` クラスの `create()` メソッドをよく見ると、前章で学んだ Template Method パターンになっています。

```Java:Factory.java
public final Product create(String owner) {
    Product product = createProduct(owner);  // 抽象メソッド（サブクラスが実装）
    registerProduct(product);               // 抽象メソッド（サブクラスが実装）
    return product;
}
```

`create()` が処理の流れ（テンプレート）を定義し、`createProduct()` と `registerProduct()` という抽象メソッドの実装をサブクラスに委ねています。

つまり、**Factory Method パターンの中に Template Method パターンが使われている**のです。デザインパターンは独立したものではなく、このように組み合わされて使われることがあります。

---

<a id="深堀り4"></a>

## 【深堀り④】static ファクトリメソッドとの違い

「static なファクトリメソッドも似たようなものでは？」と思うかもしれません。Java の標準ライブラリにも static ファクトリメソッドは多く登場します。

```Java
List<String> list  = List.of("a", "b", "c");
List<String> list2 = Arrays.asList("a", "b", "c");
```

これらは `new ArrayList<>()` などを直接書かずにインスタンスを得られる便利な書き方です。しかし、GoF が定義する Factory Method パターンとは別物です。

| 比較項目   | static ファクトリメソッド                    | Factory Method パターン            |
| ---------- | -------------------------------------------- | ---------------------------------- |
| 定義場所   | 生成したいクラス自身やユーティリティクラス   | 抽象クラス（`Factory`）            |
| 拡張方法   | 静的メソッドを追加・修正                     | サブクラスを追加                   |
| 多態性     | なし（コンパイル時に決定）                   | あり（実行時に決定）               |
| 目的       | コンストラクタの代替・可読性の向上           | 生成処理のカプセル化・拡張性の確保 |

`List.of()` は「`List` インターフェースを実装した不変リストを返す」という実装の詳細を隠蔽しています。一方、Factory Method パターンは「どのサブクラスを生成するか」をサブクラスに委ね、拡張を容易にすることが目的です。

---

<a id="深堀り5"></a>

## 【深堀り⑤】GoF デザインパターンとの位置づけ

GoF（Gang of Four）の 23 パターンは「生成」「構造」「振る舞い」の 3 カテゴリに分類されます。Factory Method パターンは**生成パターン**の 1 つです。

生成パターンは 5 種類あります：

| パターン           | 概要                                     |
| ------------------ | ---------------------------------------- |
| **Factory Method** | サブクラスがインスタンス生成を担う       |
| Abstract Factory   | 関連するオブジェクト群をまとめて生成する |
| Builder            | 複雑なオブジェクトを段階的に組み立てる   |
| Prototype          | 既存インスタンスをコピーして生成する     |
| Singleton          | インスタンスを 1 つだけに制限する        |

Factory Method と Abstract Factory は名前が似ていますが、Factory Method は「1 種類の製品の生成をサブクラスに委ねる」のに対し、Abstract Factory は「関連する複数種類の製品群をまとめて生成する」という違いがあります。

---

<a id="深堀り6"></a>

## 【深堀り⑥】DIP（依存性逆転の原則）

DIP（Dependency Inversion Principle）とは、「**上位モジュールは下位モジュールに依存してはならない。どちらも抽象に依存すべきである**」という原則です。

正しい実装の `Main` を振り返ります：

```Java:Main.java
Factory factory = new VisitorPassFactory();  // ← ここだけ具象クラス
Product pass1 = factory.create("田中 太郎");

pass1.use();
```

`factory` の型は `Factory`（抽象クラス）、`pass1` の型は `Product`（抽象クラス）です。`Main` は `VisitorPass` や `VisitorPassFactory` の内部実装を知らずに動作できます。

`new VisitorPassFactory()` の 1 行だけが具象クラスに触れていますが、ここを差し替えるだけで `PatientTicketFactory` に切り替えられます。DI（依存性の注入）を使えばこの 1 行すら `Main` から取り除くことができますが、それはまた別の話題です。

---

<a id="深堀り7"></a>

## 【深堀り⑦】Iterator パターンでの Factory Method 使用例

第 1 章で学んだ Iterator パターンにも、Factory Method パターンが使われています。

Java の `java.util.Collection` インターフェースには `iterator()` というメソッドがあります。

```Java
public interface Collection<E> {
    Iterator<E> iterator();  // ← これが Factory Method
    // ...
}
```

`iterator()` は「`Iterator` を返す」という契約だけを定義し、**どの `Iterator` を返すかは実装クラスに委ねています**。

```Java
List<String> arrayList = new ArrayList<>();
Iterator<String> it1 = arrayList.iterator();  // ArrayList が生成する Iterator

List<String> linkedList = new LinkedList<>();
Iterator<String> it2 = linkedList.iterator(); // LinkedList が生成する Iterator
```

`ArrayList` と `LinkedList` ではデータ構造が異なるため、最適な `Iterator` の実装も異なります。しかし呼び出し側は `iterator()` を呼ぶだけでよく、内部でどの `Iterator` が返ってくるかを知る必要はありません。

これが Factory Method パターンの実世界での活用例です。フレームワーク（`Collection` インターフェース）が「Iterator を作る」という手順を定義し、具体的な実装（`ArrayList`・`LinkedList`）がどの Iterator を作るかを決めています。
