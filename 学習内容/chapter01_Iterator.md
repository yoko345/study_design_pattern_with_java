# Iterator（処理を繰り返す）

【具体例】
フルーツバスケットに「りんご（100円）」「バナナ（300円）」「いちご（500円）」があるとする。<br>
繰り返し文より「名前：果物名, 価格：」の表記で各フルーツの情報を出力したい。

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

上記は固定長のため、下記のようにすると`ArrayIndexOutOfBoundsException`が発生する

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

※補足
下記のようにすると`UnsupportedOperationException`が発生する

```Java
public class MainChapter02 {
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

そこで、可変長にすることで適切な出力となる。

```Java
public class MainChapter02 {
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

【脱線】
データ型[]（配列）、Listインタフェース、ArrayListの違い

|           |                                                                    |
| --------- | ------------------------------------------------------------------ |
| 配列      | Java言語に組み込まれている<br>クラスではなく「特別な構造」         |
| List      | 「こういう操作ができます」という仕様だけを定義したもので中身はない |
| ArrayList | Listを実装したクラスで、実体（オブジェクト）が生成される           |

`ArrayList<String> list = new ArrayList<>();`ではなく`List<String> list = new ArrayList<>();`とした方が良い理由
差し替え可能にするため
例）List<String> list = new LinkedList<>();
→設計の柔軟性が高くなる

・実務での使い分け<br>
|場面|選択|
| ---- | ---- |
|API設計|List|
|実装内部|ArrayList|
|パフォーマンス重視 or 低レベル処理|配列|

## Iteratorを用いる

【前提】

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

| 名前        | 説明                                   |
| ----------- | -------------------------------------- |
| Iterable<T> | T型が集まったもの                      |
| Iterator<E> | 1つ1つの要素の処理を繰り返すためのもの |

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

繰り返し文の登場人物が下記の`Iterator`インタフェースのメソッドだけになる。
_`hasNext`
_`next`
これにより、`fruitBasket`の実装に依存しなくなる。

上記は固定長のため、下記のようにすると`ArrayIndexOutOfBoundsException`が発生する

```Java
public class Main {
    public static void main(String[] args) throws Exception {
        FruitBasket fruitBasket = new FruitBasket(3);
        fruitBasket.appendFruit(new Fruit("りんご", 100));
        fruitBasket.appendFruit(new Fruit("バナナ", 300));
        fruitBasket.appendFruit(new Fruit("いちご", 500));
        fruitBasket[3] = new Fruit("キュウイ", 150); // ←ここを追加

        Iterator<Fruit> iterator = fruitBasket.iterator();
        while (iterator.hasNext()) {
            Fruit fruit = iterator.next();
            System.out.println(fruit.getFruitInfo());
        }
    }
}
```

そこで可変長に実装し直す。

```Java


public class FruitBasket implements Iterable<Fruit> {
    private List<Fruit> fruits;

    public FruitBasket(int initialsize) {
        this.fruits = new ArrayList<>(initialsize);
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
これにより、Mainクラスを修正することなくコンパイルが通ることになる。

このことからも分かるように、繰り返し文の部分が`FruitBasket`の実装に依存しない様になる。
つまり、再利用可能なコードとなる。

拡張For文に関して

```Java
Iterator<Fruit> iterator = fruitBasket.iterator();
while (iterator.hasNext()) {
    Fruit fruit = iterator.next();
    System.out.println(fruit.getFruitInfo());
}
```
上記のコードは下記のように書ける。
逆に言えば、下記のコードがコンパイルされるときに上記のコードに変換されて実行される。
```Java
for (Fruit fruit : fruitBasket) {
    System.out.println(fruit.getFruitInfo());
}
```
ちなみに、配列`fruitBasket`の場合は下記のように変換される。
```Java
for (int i = 0; i < fruitBasket.length; i++) {
    System.out.println(fruitBasket[i].getFruitInfo());
}
```
