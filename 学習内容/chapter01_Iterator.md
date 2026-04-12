# Iterator（処理を繰り返す）パターン

繰り返し処理に関して以下の経験はないだろうか？<br>
`for`文の中身が処理対象のデータ構造に依存している関係で、後からリファクタリングを行おうとした際、繰り返し処理以外の部分も修正しないといけなくなった。<br>
<br>
この記事では、その問題を解決する「Iteratorパターン」を具体例を通して学ぶ。<br>
<br>
**【具体例】**<br>
フルーツバスケットに「りんご（100円）」「バナナ（300円）」「いちご（500円）」があるとする。<br>
繰り返し文より「名前：果物名, 価格：金額」の表記で各フルーツの情報を出力したい。

## forループで繰り返し処理をする

今回の具体例では、果物の名前と金額の情報を保持している必要があるので、以下のように`Fruit`クラスを作成する。

```Java
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

では、準備ができたので、繰り返し処理の実装を行うと以下のようになる。

```Java
public class Main {
    public static void main(String[] args) throws Exception {
        Fruit[] fruitBasket = new Fruit[3];

        fruitBasket[0] = new Fruit("りんご", 100);
        fruitBasket[1] = new Fruit("バナナ", 300);
        fruitBasket[2] = new Fruit("いちご", 500);

        for (int i = 0; i < fruitBasket.length; i++) {
            System.out.println(fruitBasket[i].getFruitInfo());
        }
    }
}
```

出力結果

```
名前：りんご, 価格：100
名前：バナナ, 価格：300
名前：いちご, 価格：500
```

---

## 実装が変わると繰り返し文も変わる

冒頭の経験の1つに、後から「キュウイ（150円）」を追加したくなったとする。<br>
下記のような実装をしたくなると思う。

```Java
public class Main {
    public static void main(String[] args) throws Exception {
        Fruit[] fruitBasket = new Fruit[3];

        fruitBasket[0] = new Fruit("りんご", 100);
        fruitBasket[1] = new Fruit("バナナ", 300);
        fruitBasket[2] = new Fruit("いちご", 500);
        fruitBasket[3] = new Fruit("キュウイ", 150); // ←ここを追加

        for (int i = 0; i < fruitBasket.length; i++) {
            System.out.println(fruitBasket[i].getFruitInfo());
        }
    }
}
```

しかし、配列は**固定長**のため、サイズを超えて追加しようとすると`ArrayIndexOutOfBoundsException`が発生する。

ここで「配列の代わりに`List`を使えばよいのでは？」と思うかもしれない。

```Java
public class Main {
    public static void main(String[] args) throws Exception {
        List<Fruit> fruitBasket =
                Arrays.asList(new Fruit("りんご", 100), new Fruit("バナナ", 300), new Fruit("いちご", 500));

        fruitBasket.add(new Fruit("キュウイ", 150));

        for (int i = 0; i < fruitBasket.size(); i++) {
            System.out.println(fruitBasket.get(i).getFruitInfo());
        }
    }
}
```

しかし、`Arrays.asList`で生成したリストも**固定長**のため、

```Java
fruitBasket.add(new Fruit("キュウイ", 150));
```

の部分で`UnsupportedOperationException`が発生する。

### 補足：配列・Listインタフェース・ArrayListの違い

なぜ`List`でも固定長になってしまうのか。それぞれの違いを整理しておこう。

|           |                                                                    |
| --------- | ------------------------------------------------------------------ |
| 配列      | Java言語に組み込まれている<br>クラスではなく「特別な構造」         |
| List      | 「こういう操作ができます」という仕様だけを定義したもので中身はない |
| ArrayList | Listを実装したクラスで、実体（オブジェクト）が生成される           |

`Arrays.asList`が返すのは`List`の実装の一種だが、内部は配列をラップしたものなので追加・削除ができない。<br>
**可変長**にするには、`ArrayList`を使う必要がある。

そこで**可変長**の`ArrayList`を使うと要素を追加できることが下記の実装から分かる。

```Java
public class Main {
    public static void main(String[] args) throws Exception {
        ArrayList<Fruit> fruitBasket = new ArrayList<>();

        fruitBasket.add(new Fruit("りんご", 100));
        fruitBasket.add(new Fruit("バナナ", 300));
        fruitBasket.add(new Fruit("いちご", 500));
        fruitBasket.add(new Fruit("キュウイ", 150));

        for (int i = 0; i < fruitBasket.size(); i++) {
            System.out.println(fruitBasket.get(i).getFruitInfo());
        }
    }
}
```

出力結果

```
名前：りんご, 価格：100
名前：バナナ, 価格：300
名前：いちご, 価格：500
名前：キュウイ, 価格：150
```

### 本質的な問題：繰り返し文が実装に依存している

`ArrayList`への変更で要素追加の問題は解決した。<br>
しかし、**繰り返し文も修正が必要になった**点に注目してほしい。

| 実装      | 繰り返し文での要素取得 |
| --------- | ---------------------- |
| 配列      | `fruitBasket[i]`       |
| ArrayList | `fruitBasket.get(i)`   |

つまり、`fruitBasket`の実装を変えるたびに繰り返し文も変更しなければならない。<br>
これは**再利用性が低い**状態であると言える。

---

## Iteratorパターンによる解決

この問題を解決するのが**Iteratorパターン**となる。

まずは前提となるインタフェースを確認しておく。

```Java
public interface Iterable<T> {
    Iterator<T> iterator();
}
```

```Java
public interface Iterator<E> {
    boolean hasNext();
    E next();
}
```

| 名前          | 説明                                   |
| ------------- | -------------------------------------- |
| Iterable\<T\> | T型が集まったもの                      |
| Iterator\<E\> | 1つ1つの要素の処理を繰り返すためのもの |

これらを使って`FruitBasket`クラスと`FruitBasketIterator`クラスを実装する。

```Java
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

```Java
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

```Java
public class Main {
    public static void main(String[] args) throws Exception {
        FruitBasket fruitBasket = new FruitBasket(3);
        fruitBasket.appendFruit(new Fruit("りんご", 100));
        fruitBasket.appendFruit(new Fruit("バナナ", 300));
        fruitBasket.appendFruit(new Fruit("いちご", 500));

        Iterator<Fruit> iterator = fruitBasket.iterator();
        while (iterator.hasNext()) {
            Fruit fruit = iterator.next();
            System.out.println(fruit.getFruitInfo());
        }
    }
}
```

繰り返し文の中に登場するのが、`Iterator`インタフェースのメソッドだけになった。

- `hasNext()`
- `next()`

これにより、**繰り返し文が`FruitBasket`の内部実装に依存しなくなった**。

---

## 実装が変わっても繰り返し文は変わらない

`FruitBasket`の内部実装を配列から`ArrayList`に変えてみよう。

```Java
public class FruitBasket implements Iterable<Fruit> {
    private List<Fruit> fruits;

    public FruitBasket(int initialSize) {
        this.fruits = new ArrayList<>(initialSize);
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

`FruitBasket`の実装は変わったが、**`Main`クラスの繰り返し文はまったく変更していない**。

```Java
// この部分は一切変更不要
Iterator<Fruit> iterator = fruitBasket.iterator();
while (iterator.hasNext()) {
    Fruit fruit = iterator.next();
    System.out.println(fruit.getFruitInfo());
}
```

これが**再利用可能なコード**の意味となる。<br>
`FruitBasket`の内部実装がどのように変わっても、繰り返しを行う側のコードは修正せずに済む。

---

## 【補足】拡張for文との関係

先ほどの`while`ループは、拡張for文で書き換えることができる。

```Java
// while ループ（Iteratorを明示）
Iterator<Fruit> iterator = fruitBasket.iterator();
while (iterator.hasNext()) {
    Fruit fruit = iterator.next();
    System.out.println(fruit.getFruitInfo());
}
```

```Java
// 拡張for文（上と等価）
for (Fruit fruit : fruitBasket) {
    System.out.println(fruit.getFruitInfo());
}
```

逆に言えば、拡張for文はコンパイル時に上記の`while`ループに変換されて実行される。<br>
`Iterable<T>`を実装したクラスであれば、拡張for文が使えるということだ。

なお、**配列**に対して拡張for文を使った場合は、`Iterator`ではなく通常の`for`ループに変換される。

```Java
// 配列に対する拡張for文は、下記に変換される
for (int i = 0; i < fruitBasket.length; i++) {
    System.out.println(fruitBasket[i].getFruitInfo());
}
```

---

本記事の内容はここまでとなる。<br>
以降は「もう少し深く知りたい」という方向けの補足だ。
実務でよく遭遇する落とし穴や、今回学んだパターンが繋がる設計原則について触れている。

---

## 【深堀り①】`List<>` で宣言する理由 ― DIP（依存性逆転の原則）

`ArrayList<Fruit> fruitBasket`ではなく`List<Fruit> fruitBasket`と宣言する方が良いとされている。

```Java
// 推奨
List<Fruit> fruitBasket = new ArrayList<>();

// 非推奨
ArrayList<Fruit> fruitBasket = new ArrayList<>();
```

【理由】<br>
**差し替え可能にする**ため。<br>
これにより、後から`LinkedList`など別の実装に変えても呼び出し元を修正せずに済む。

```Java
List<Fruit> fruitBasket = new LinkedList<>(); // 呼び出し元のコードはそのまま
```

実務での使い分けをまとめると下記の通りとなる。

| 場面                               | 選択      |
| ---------------------------------- | --------- |
| API設計（メソッドの引数・戻り値）  | List      |
| 実装内部                           | ArrayList |
| パフォーマンス重視 or 低レベル処理 | 配列      |

この「依存する先をインタフェースにする」という考え方は、**DIP（依存性逆転の原則）** と呼ばれる設計原則のひとつとなる。<br>
具体的な実装クラス（`ArrayList`や`LinkedList`）ではなく、抽象（`List`インタフェース）に依存することで、実装が変わっても呼び出し元への影響をなくせる。

この原則は、今回学んだIteratorパターンにも同じ考え方が現れている。<br>
繰り返し文が`FruitBasket`の具体的な実装（配列か`ArrayList`か）ではなく、`Iterator`インタフェースの`hasNext()`・`next()`に依存しているのは、まさにDIPの実践となる。

---

## 【深堀り②】`ConcurrentModificationException` の罠

以下の実装を見てみよう。

```Java
List<Fruit> fruits = new ArrayList<>();
fruits.add(new Fruit("りんご", 100));
fruits.add(new Fruit("バナナ", 300));
fruits.add(new Fruit("いちご", 500));

for (Fruit fruit : fruits) {
    if (fruit.getFruitInfo().contains("バナナ")) {
        fruits.remove(fruit); // ConcurrentModificationException が発生！
    }
}
```

一見問題ない実装に見えるが、

```Java
fruits.remove(fruit);
```

の部分で`ConcurrentModificationException`が発生する。<br>
これは実務でよく踏むバグで、Iterator反復中にコレクションを変更すると例外が発生するので注意しよう。

## 【深堀り③】GoFデザインパターンとの位置づけ

今回は使ったIteratorパターンは GoFの23パターンのうち「振る舞いパターン」に分類されるものとなります。<br>
詳しくは「GoF」で検索してみてください。
