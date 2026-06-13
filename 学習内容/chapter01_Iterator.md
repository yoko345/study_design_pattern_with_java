# Iterator（イテレータ）パターン ― 繰り返し文を実装に依存させない

次のような経験をしたことはありませんか？

> `for` 文の中身が処理対象のデータ構造に依存している関係で、後からリファクタリングや、ちょっとした動作確認をしたいとき（デバッグを行いたいときなど）に、「繰り返し処理」以外の部分まで気にする必要が出てきた。

この記事では、そうした「変更や確認のたびに手間がかかる実装」の悪い例と、それを解決する「Iterator パターン」を、具体例を通して紹介します。

## 目次

- [【具体例】](#具体例)
- [`for` ループで繰り返し処理をする](#for-ループで繰り返し処理をする)
- [実装が変わると繰り返し文も変わる](#実装が変わると繰り返し文も変わる)
- [繰り返し文が実装に依存している](#繰り返し文が実装に依存している)
- [Iterator パターンによる解決](#iterator-パターンによる解決)
- [実装が変わっても繰り返し文は変わらない](#実装が変わっても繰り返し文は変わらない)
- [拡張 `for` 文との関係](#拡張-for-文との関係)
- [まとめ](#まとめ)
- [【深堀り①】配列・`List` インタフェース・`ArrayList` の違い](#深堀り1)
- [【深堀り②】`List<>` で宣言する理由 ― DIP（依存性逆転の原則）](#深堀り2)
- [【深堀り③】`ConcurrentModificationException` の罠](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

> フルーツバスケットに「りんご（150 円）」「バナナ（200 円）」「いちご（500 円）」があるとします。
> 繰り返し文により「名前：果物名, 価格：金額」の表記で各フルーツの情報を出力してください。

## `for` ループで繰り返し処理をする

今回の具体例では、果物の名前と金額の情報を出力する必要があるので、`Fruit` クラスを事前に作成しておきます。

**`Fruit.java`**

```java
package example;

public class Fruit {
    private String name;
    private int price;

    public Fruit(String name, int price) {
        this.name = name;
        this.price = price;
    }

    public String getFruitInfo() {
        return "名前：" + name + ", 価格：" + price;
    }
}
```

準備ができたので、繰り返し処理の実装を行いましょう。<br>
※もし、ご自身でコーディングを行う場合は一旦ここで読むのを止めて実装してみてください。その後で、続きを読んでください。

以下のようになると思います。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) throws Exception {
        Fruit[] fruitBasket = new Fruit[3];

        fruitBasket[0] = new Fruit("りんご", 150);
        fruitBasket[1] = new Fruit("バナナ", 200);
        fruitBasket[2] = new Fruit("いちご", 500);

        for (int i = 0; i < fruitBasket.length; i++) {
            System.out.println(fruitBasket[i].getFruitInfo());
        }
    }
}
```

**出力結果**

```
名前：りんご, 価格：150
名前：バナナ, 価格：200
名前：いちご, 価格：500
```

## 実装が変わると繰り返し文も変わる

冒頭の経験のなかで例えば、後から「キウイ（130 円）」を追加したくなったとします。その場合、下記のような実装をしたくなると思います。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) throws Exception {
        Fruit[] fruitBasket = new Fruit[3];

        fruitBasket[0] = new Fruit("りんご", 150);
        fruitBasket[1] = new Fruit("バナナ", 200);
        fruitBasket[2] = new Fruit("いちご", 500);
        fruitBasket[3] = new Fruit("キウイ", 130); // ←ここを追加

        for (int i = 0; i < fruitBasket.length; i++) {
            System.out.println(fruitBasket[i].getFruitInfo());
        }
    }
}
```

しかし、配列は**固定長**のため、上記のようにサイズを超えて追加しようとすると `ArrayIndexOutOfBoundsException` が発生します。

ここで「配列の代わりに `List` を使えばよいのでは？」と思うかもしれません。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) throws Exception {
        List<Fruit> fruitBasket = Arrays.asList(
            new Fruit("りんご", 150),
            new Fruit("バナナ", 200),
            new Fruit("いちご", 500)
        );

        fruitBasket.add(new Fruit("キウイ", 130));

        for (int i = 0; i < fruitBasket.size(); i++) { // ←fruitBasket.size() に変更
            System.out.println(fruitBasket.get(i).getFruitInfo()); // ←fruitBasket.get(i) に変更
        }
    }
}
```

しかし、`Arrays.asList` で生成したリストも**固定長**のため、

```
fruitBasket.add(new Fruit("キウイ", 130));
```

の部分で `UnsupportedOperationException` が発生します（→ [【深堀り①】配列・`List` インタフェース・`ArrayList` の違い](#深堀り1)）。

そこで**可変長**の `ArrayList`（→ [【深堀り②】`List<>` で宣言する理由 ― DIP（依存性逆転の原則）](#深堀り2)）を使うことで要素の追加ができるようになります。このことは、下記の実装から分かります。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) throws Exception {
        ArrayList<Fruit> fruitBasket = new ArrayList<>(); // ※2

        fruitBasket.add(new Fruit("りんご", 150));
        fruitBasket.add(new Fruit("バナナ", 200));
        fruitBasket.add(new Fruit("いちご", 500));
        fruitBasket.add(new Fruit("キウイ", 130));

        for (int i = 0; i < fruitBasket.size(); i++) {
            System.out.println(fruitBasket.get(i).getFruitInfo());
        }
    }
}
```

**出力結果**

```
名前：りんご, 価格：150
名前：バナナ, 価格：200
名前：いちご, 価格：500
名前：キウイ, 価格：130
```

## 繰り返し文が実装に依存している

後から「キウイ（130 円）」を追加するという実装は、`ArrayList` への変更で解決できました。

しかし実装の中身を見てみると、下記のように**繰り返し文も修正が必要になった**ことがわかります。

| 実装        | 繰り返し文での要素取得 |
| ----------- | ---------------------- |
| 配列        | `fruitBasket[i]`       |
| `ArrayList` | `fruitBasket.get(i)`   |

つまり、`fruitBasket` の実装を変えるたびに繰り返し文も変更しなければならないということです。
これは**再利用性が低い**状態であると言えます。

## Iterator パターンによる解決

この問題を解決するのが **Iterator パターン**となります。

まずは前提となるインタフェースを確認しておきましょう。

| 名前          | 説明                                      |
| ------------- | ----------------------------------------- |
| `Iterable<T>` | T 型が集まったもの                        |
| `Iterator<E>` | 1 つ 1 つの要素の処理を繰り返すためのもの |

```Java
public interface Iterable<T> {
    Iterator<T> iterator();
}
```

> 引用元: OpenJDK [Iterable.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/Iterable.java)

```Java
public interface Iterator<E> {
    boolean hasNext();
    E next();
}
```

> 引用元: OpenJDK [Iterator.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/Iterator.java)

これらを使って `FruitBasket` クラスと `FruitBasketIterator` クラスを実装します。

**`FruitBasket.java`**

```java
package example;

public class FruitBasket implements Iterable<Fruit> {

    private Fruit[] fruits;
    private int lastIndex = 0;

    public FruitBasket(int maxNumber) {
        this.fruits = new Fruit[maxNumber];
    }

    public Fruit getFruitAt(int index) {
        return fruits[index];
    }

    public void appendFruit(Fruit fruit) {
        this.fruits[lastIndex] = fruit;
        lastIndex++;
    }

    public int getLength() {
        return lastIndex;
    }

    @Override
    public Iterator<Fruit> iterator() {
        return new FruitBasketIterator(this);
    }
}
```

**`FruitBasketIterator.java`**

```java
package example;

public class FruitBasketIterator implements Iterator<Fruit> {

    private FruitBasket fruitBasket;
    private int index;

    public FruitBasketIterator(FruitBasket fruitBasket) {
        this.fruitBasket = fruitBasket;
        this.index = 0;
    }

    @Override
    public boolean hasNext() {
        if (index < fruitBasket.getLength()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Fruit next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        Fruit fruit = fruitBasket.getFruitAt(index);
        index++;

        return fruit;
    }
}
```

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) throws Exception {
        FruitBasket fruitBasket = new FruitBasket(3);

        fruitBasket.appendFruit(new Fruit("りんご", 150));
        fruitBasket.appendFruit(new Fruit("バナナ", 200));
        fruitBasket.appendFruit(new Fruit("いちご", 500));

        Iterator<Fruit> iterator = fruitBasket.iterator();
        while (iterator.hasNext()) {
            Fruit fruit = iterator.next();
            System.out.println(fruit.getFruitInfo());
        }
    }
}
```

**出力結果**

```
名前：りんご, 価格：150
名前：バナナ, 価格：200
名前：いちご, 価格：500
```

実装コードを見ると、繰り返し文の中に登場するのが `Iterator` インタフェースのメソッドだけになっていることがわかります。

- `hasNext()`
- `next()`

これにより、**繰り返し文が `FruitBasket` の内部実装に依存しなくなりました**。

## 実装が変わっても繰り返し文は変わらない

改めて「キウイ（130 円）」を追加したくなったとしましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) throws Exception {
        FruitBasket fruitBasket = new FruitBasket(3);

        fruitBasket.appendFruit(new Fruit("りんご", 150));
        fruitBasket.appendFruit(new Fruit("バナナ", 200));
        fruitBasket.appendFruit(new Fruit("いちご", 500));
        fruitBasket.appendFruit(new Fruit("キウイ", 130)); // ←ここを追加

        Iterator<Fruit> iterator = fruitBasket.iterator();
        while (iterator.hasNext()) {
            Fruit fruit = iterator.next();
            System.out.println(fruit.getFruitInfo());
        }
    }
}
```

上記実装より、`fruitBasket` には `FruitBasket(3)` のインスタンスが代入されています。`FruitBasket` は内部で `new Fruit[3]` という固定長配列を保持しているため、`appendFruit` で 4 つ目の要素を追加しようとすると、配列のサイズを超えてしまい `ArrayIndexOutOfBoundsException` が発生します。

そこで、`FruitBasket` の内部実装を配列から `ArrayList`（→ [【深堀り②】`List<>` で宣言する理由 ― DIP（依存性逆転の原則）](#深堀り2)）に変えてみましょう。

**`FruitBasket.java`**

```java
package example;

public class FruitBasket implements Iterable<Fruit> {
    private List<Fruit> fruits;

    public FruitBasket(int initialSize) {
        this.fruits = new ArrayList<>(initialSize); // ※2
    }

    public Fruit getFruitAt(int index) {
        return fruits.get(index);
    }

    public void appendFruit(Fruit fruit) {
        fruits.add(fruit);
    }

    public int getLength() {
        return this.fruits.size();
    }

    @Override
    public Iterator<Fruit> iterator() {
        return new FruitBasketIterator(this);
    }
}
```

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) throws Exception {
        FruitBasket fruitBasket = new FruitBasket(3);

        fruitBasket.appendFruit(new Fruit("りんご", 150));
        fruitBasket.appendFruit(new Fruit("バナナ", 200));
        fruitBasket.appendFruit(new Fruit("いちご", 500));
        fruitBasket.appendFruit(new Fruit("キウイ", 130));

        Iterator<Fruit> iterator = fruitBasket.iterator();
        while (iterator.hasNext()) {
            Fruit fruit = iterator.next();
            System.out.println(fruit.getFruitInfo());
        }
    }
}
```

**出力結果**

```
名前：りんご, 価格：150
名前：バナナ, 価格：200
名前：いちご, 価格：500
名前：キウイ, 価格：130
```

`FruitBasket` クラスの実装に関して、要素の管理の仕方が配列から `ArrayList` に変わっています。しかし、下記の `Main` クラスの繰り返し文は、変更前の `Main` クラスとまったく同じコードのままです。

```Java
Iterator<Fruit> iterator = fruitBasket.iterator();
while (iterator.hasNext()) {
    Fruit fruit = iterator.next();
    System.out.println(fruit.getFruitInfo());
}
```

このように、`FruitBasket` の内部実装がどのように変わっても、繰り返しを行う側のコードは修正せずに済むため、**再利用可能なコード**となります。

## 拡張 `for` 文との関係

先ほどの `while` ループは、拡張 `for` 文で書き換えることができます。

```Java
Iterator<Fruit> iterator = fruitBasket.iterator();
while (iterator.hasNext()) {
    Fruit fruit = iterator.next();
    System.out.println(fruit.getFruitInfo());
}
```

上記のコードは下記のように書き換えられます。

```Java
for (Fruit fruit : fruitBasket) {
    System.out.println(fruit.getFruitInfo());
}
```

逆に、拡張 `for` 文はコンパイル時に上記の `while` 文に変換されて実行されます。このように、`Iterable<T>` を実装したクラスであれば、拡張 `for` 文が使えるということです。

ちなみに、**配列**に対して拡張 `for` 文を使った場合は、`Iterator` ではなく通常の `for` 文に変換されます。

```Java
for (int i = 0; i < fruitBasket.length; i++) {
    System.out.println(fruitBasket[i].getFruitInfo());
}
```

## まとめ

今回の記事で学んだことは以下となります。

- 繰り返し文がデータ構造の実装に依存すると、実装を変えるたびに繰り返し文も修正が必要になる
- Iterator パターンを使うことで、繰り返し文を `Iterator` インタフェースのみに依存させられる
- 結果として、データ構造の内部実装が変わっても繰り返しを行う側のコードは変更不要になる
    - 再利用可能なコードとなる

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。実務でよく遭遇する落とし穴や、今回学んだパターンに繋がる設計原則について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】配列・`List` インタフェース・`ArrayList` の違い

なぜ `Arrays.asList` で生成したリストが固定長になってしまうのか？<br>
それぞれの違いを整理しておきましょう。

|             |                                                                    |
| ----------- | ------------------------------------------------------------------ |
| 配列        | Java 言語に組み込まれている<br>クラスではなく「特別な構造」        |
| `List`      | 「こういう操作ができます」という仕様だけを定義したもので中身はない |
| `ArrayList` | `List` を実装したクラスで、実体（オブジェクト）が生成される        |

`Arrays.asList` が返すのは `List` の実装の一種なのですが、内部は配列をラップしたものなので追加・削除ができません。

<a id="深堀り2"></a>

## 【深堀り②】`List<>` で宣言する理由 ― DIP（依存性逆転の原則）

`ArrayList<Fruit> fruitBasket` ではなく `List<Fruit> fruitBasket` と宣言する方が良いとされています。

```Java
// 推奨
List<Fruit> fruitBasket = new ArrayList<>();

// 非推奨
ArrayList<Fruit> fruitBasket = new ArrayList<>();
```

**理由：** 差し替え可能にするためです。

これにより、後から `LinkedList` など別の実装に変えても呼び出し元を修正せずに済みます。

```Java
List<Fruit> fruitBasket = new LinkedList<>(); // 呼び出し元のコードはそのまま
```

実務での使い分けをまとめると下記の通りとなります。

| 場面                               | 選択        |
| ---------------------------------- | ----------- |
| API 設計（メソッドの引数・戻り値） | `List`      |
| 実装内部                           | `ArrayList` |
| パフォーマンス重視 or 低レベル処理 | 配列        |

ところで、なぜ `List` 型の変数に `ArrayList` のインスタンスを代入できるのでしょうか？

実際に `ArrayList` のソースコードを見てみましょう。

```Java
// ArrayListのクラス宣言（抜粋）
public class ArrayList<E> extends AbstractList<E>
        implements List<E>, RandomAccess, Cloneable, java.io.Serializable
```

> 引用元: OpenJDK [ArrayList.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/ArrayList.java)

上記を見ると、クラス宣言で `implements List<E>` と記述されています。
この部分が「`ArrayList` は `List` インタフェースの実装クラスである」ことを明示しています。
つまり、`ArrayList` は `List` 型の変数に代入できるため、

```
List<Fruit> fruitBasket = new ArrayList<>()
```

という宣言が成り立ちます。

この「依存する先をインタフェースにする」という考え方は、「**DIP（Dependency Inversion Principle：依存性逆転の原則）**」と呼ばれる設計原則のひとつとなります。
具体的な実装クラス（`ArrayList` や `LinkedList`）ではなく、抽象（`List` インタフェース）に依存することで、実装が変わっても呼び出し元への影響をなくせます。

この原則は、今回学んだ Iterator パターンにも同じ考え方が現れています。繰り返し文が `FruitBasket` の具体的な実装（配列か `ArrayList` か）ではなく、`Iterator` インタフェースの `hasNext()`・`next()` に依存しているのは、まさに DIP の実践となります。

<a id="深堀り3"></a>

## 【深堀り③】`ConcurrentModificationException` の罠

以下の実装を見てみましょう。

```Java
List<Fruit> fruits = new ArrayList<>();

fruits.add(new Fruit("りんご", 150));
fruits.add(new Fruit("バナナ", 200));
fruits.add(new Fruit("いちご", 500));

for (Fruit fruit : fruits) {
    if (fruit.getFruitInfo().contains("バナナ")) {
        fruits.remove(fruit);
    }
}
```

一見問題ない実装に見えますが、`fruits.remove(fruit);` の部分で `ConcurrentModificationException` が発生します。これは実務でよく踏むバグで、`Iterator` による反復中にコレクションを変更すると例外が発生するので注意しましょう。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Iterator パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
