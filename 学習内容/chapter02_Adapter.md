# Adapter パターン ― 既存コードに触れずに新機能を追加する

追加実装をする際に、既存コードを修正することなく実装するにはどうすればよいでしょうか？

実務では、「すでに動いているコードには触れたくない」という感覚は自然なものです。<br>
テスト済みのコードを再び変更することは、デグレードのリスクを新たに持ち込むことになるからです。

しかし、既存システムとはメソッド名や引数の仕様が異なる外部クラスを追加したい場合、その差異をどこかで吸収しなければなりません。

そのための設計が「Adapter パターン」です。<br>
名前の通り、異なるインターフェースを持つクラスを、既存システムに「適合（adapt）」させる役割を持ちます。

この記事では、EC サイトへの決済手段追加というシナリオを通して、Adapter パターンの実装と、なぜこの設計が選ばれるのかを学んでいきます。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装①（外部クラスの修正）](#好ましくない実装外部クラスの修正)
- [好ましくない実装②（実行クラスで直接呼び出す）](#好ましくない実装実行クラスで直接呼び出す)
- [正しい実装①（委譲を使った Adapter パターン）](#正しい実装委譲を使った-adapter-パターン)
- [正しい実装②（継承を使った Adapter パターン）](#正しい実装継承を使った-adapter-パターン)
- [「委譲を使った Adapter パターン」と「継承を使った Adapter パターン」の使い分け](#委譲を使った-adapter-パターンと継承を使った-adapter-パターンの使い分け)
- [まとめ](#まとめ)
- [【深堀り①】委譲（Delegation）とは](#深堀り1)
- [【深堀り②】オープン・クローズドの原則（OCP）](#深堀り2)
- [【深堀り③】Java の単一継承の制約](#深堀り3)
- [【深堀り④】GoF デザインパターンとの位置づけ](#深堀り4)

---

## 【具体例】

### シナリオ

> あなたは EC サイトの開発チームに所属しています。<br>
> 今までクレジットカード決済のみ対応していましたが、若年層ユーザーの利用率向上を目的に、QR コード決済「サンプル Pay」を追加することになりました。<br>
> サンプル Pay 社からは外部クラスが提供されていますが、メソッド名が既存システムと異なるため、そのまま組み込むことができない状態です。

### 既存コードの仕様

- `PaymentProcessor`（インターフェース）

EC サイトの決済機能における共通インターフェースであり、全ての決済クラスはこれを実現する必要があります。<br>
新しい決済手段を追加する際には、このインターフェースを実現することで、既存の処理と統一した形で扱えるようになります。

| メソッド                    | 説明                 |
| --------------------------- | -------------------- |
| `void pay(int amount)`      | 指定金額を支払う     |
| `String getPaymentMethod()` | 決済手段の名称を返す |

```Java:PaymentProcessor.java
public interface PaymentProcessor {
    void pay(int amount);
    String getPaymentMethod();
}
```

<br>

- `CreditCardPayment`（既存サービスの実装クラス）

クレジットカード決済を行うクラスです。<br>
リリース済みで稼働中のため、変更によるリグレッションを避ける必要があります。

| メソッド             | 動作                                                   |
| -------------------- | ------------------------------------------------------ |
| `pay(int amount)`    | 「クレジットカードで {`amount`}円 支払いました」を出力 |
| `getPaymentMethod()` | 「クレジットカード」という名称を返す                   |

```Java:CreditCardPayment.java
public class CreditCardPayment implements PaymentProcessor {

    @Override
    public void pay(int amount) {
        System.out.println("クレジットカードで " + amount + "円 支払いました");
    }

    @Override
    public String getPaymentMethod() {
        return "クレジットカード";
    }
}
```

<br>

- `SamplePayClient`（サンプル Pay 社から提供された、追加したい変更不可の外部クラス）

サンプル Pay 社が提供する外部クラスです。<br>
社外のコードのため、変更できません。<br>
既存サービスの `PaymentProcessor` インターフェースとメソッド名が異なるため、そのままでは組み込めない状態です。

| メソッド                  | 動作                                           |
| ------------------------- | ---------------------------------------------- |
| `void charge(int yen)`    | 「サンプル Pay で {`yen`}円 決済します」を出力 |
| `String getServiceName()` | 「サンプル Pay」という名称を返す               |

```Java:SamplePayClient.java
public class SamplePayClient {

    public void charge(int yen) {
        System.out.println("サンプルPayで " + yen + "円 決済します");
    }

    public String getServiceName() {
        return "サンプルPay";
    }
}
```

- Main（実行クラス）

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        PaymentProcessor creditCard = new CreditCardPayment();

        System.out.println(creditCard.getPaymentMethod() + "を使用します");
        creditCard.pay(3000);
    }
}
```

**出力結果**

```
クレジットカードを使用します
クレジットカードで 3000円 支払いました
```

## 好ましくない実装①（外部クラスの修正）

「既存コードの仕様」では「サンプル Pay 社が提供する外部クラスは変更ができない」ことが記載されています。<br>
しかし、もしこの仕様を見落としてしまったら以下のように実装してしまうと考えられます。

仕様から「すべての決済クラス」は `PaymentProcessor` インターフェースを実現する必要があるので、下記のように記述すると思います。

```Java
public class SamplePayClient implements PaymentProcessor {
```

この時、`PaymentProcessor` インターフェースで実装が必要なメソッドは以下となります。

- `void pay(int amount)`
- `String getPaymentMethod()`

ただし、`SamplePayClient` クラスには上記のメソッドは存在しないので、追加でオーバーライドする必要があります。<br>
最終的に、以下のような実装になると思います。

```Java:SamplePayClient.java
public class SamplePayClient implements PaymentProcessor {

    public void charge(int yen) {
        System.out.println("サンプルPayで " + yen + "円 決済します");
    }

    public String getServiceName() {
        return "サンプルPay";
    }

    /* ここを追加（ここから） */
    @Override
    public void pay(int amount) {
        charge(amount);
    }

    @Override
    public String getPaymentMethod() {
        return getServiceName();
    }
    /* ここを追加（ここまで） */
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        PaymentProcessor creditCard = new CreditCardPayment();
        /* ここを追加（ここから） */
        PaymentProcessor samplePay1 = new SamplePayClient();
        SamplePayClient samplePay2 = new SamplePayClient();
        /* ここを追加（ここまで） */

        System.out.println(creditCard.getPaymentMethod() + "を使用します");
        creditCard.pay(3000);

        /* ここを追加（ここから） */
        System.out.println();

        System.out.println(samplePay1.getPaymentMethod() + "を使用します");
        samplePay1.pay(3000);

        System.out.println(samplePay2.getPaymentMethod() + "を使用します");
        samplePay2.pay(3000);
        System.out.println(samplePay2.getServiceName() + "を使用します");
        samplePay2.charge(3000);
        /* ここを追加（ここまで） */
    }
}
```

**出力結果**

```
クレジットカードを使用します
クレジットカードで 3000円 支払いました

サンプルPayを使用します
サンプルPayで 3000円 決済します
サンプルPayを使用します
サンプルPayで 3000円 決済します
サンプルPayを使用します
サンプルPayで 3000円 決済します
```

しかし、この実装には以下の問題点があります。

- 仕様の確認の際に見落としてしまった、「外部クラスは社外コードのため、変更ができません」という仕様を満たしていない
- サンプル Pay 社が提供した外部クラスが、自社で把握できないタイミングでバージョンアップされた際、今回の実装コードが上書きされてしまうリスクがある
- 変数の型として複数の候補が出てきてしまい、実装者によって選択にばらつきが発生すると同時に、使えるメソッドも異なってコードの一貫性が保てなくなる
- 決済手段が増えるたびに、実装者によって変数の型の選び方がばらつき、コードの一貫性が保てなくなる
- `SamplePayClient` クラスを直接修正しているため、修正後に `SamplePayClient` クラスが関係するところにおいて全て再テストが必要になる

上記の問題点から、実務ではレビューで差し戻しになる可能性が高い実装となります。

## 好ましくない実装②（実行クラスで直接呼び出す）

今回のような追加実装では、「提供された外部クラスは変更不可」という要件が仕様書に明記されると思いますので、"好ましくない実装①"のようなミスは避けられると考えられます。<br>
しかし実務では、今回のように関係クラスが少ないことはほぼなく、より多くのクラスが存在します。<br>
そのため、「`PaymentProcessor` インターフェースを実現するクラスを作成する」という要件の見落としは十分にありえます。また、この要件は社内コードに関するものであるため、仕様書に明記されないことも多いです。<br>
以上から次のような実装をしてしまうと考えられます。

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        PaymentProcessor creditCard = new CreditCardPayment();
        SamplePayClient samplePay = new SamplePayClient(); // ←ここを追加

        System.out.println(creditCard.getPaymentMethod() + "を使用します");
        creditCard.pay(3000);

        /* ここを追加（ここから） */
        System.out.println();

        System.out.println(samplePay.getServiceName() + "を使用します");
        samplePay.charge(3000);
        /* ここを追加（ここまで） */
    }
}
```

**出力結果**

```
クレジットカードを使用します
クレジットカードで 3000円 支払いました

サンプルPayを使用します
サンプルPayで 3000円 決済します
```

しかし、この実装には以下の問題点があります。

- 実行クラスで直接外部クラスを呼び出しているため、共通の型で扱えなくなる
- 決済手段が増えるたびに各クラス固有の型で変数を定義することになり、呼び出し側のコードに統一性がなくなる
    - その結果、`Main` クラスが肥大化し、修正箇所も増える
- `Main` クラスを直接修正しているため、修正後に既存コードも含めた再テストが必要になる

上記の問題点から、実務ではレビューで差し戻しになる可能性が高い実装となります。

## 正しい実装①（委譲を使った Adapter パターン）

では、`PaymentProcessor` という共通の型を実現しつつ、`SamplePayClient` クラスに変更を加えずに実装するにはどうすればよいのでしょうか？

この問題を解決するのが **Adapter パターン**です。<br>
以下の具体的な実装コードを見てみましょう。

```Java:SamplePayAdapter.java
public class SamplePayAdapter implements PaymentProcessor {

    private SamplePayClient samplePayClient = new SamplePayClient();

    @Override
    public void pay(int amount) {
        samplePayClient.charge(amount);
    }

    @Override
    public String getPaymentMethod() {
        return samplePayClient.getServiceName();
    }
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        PaymentProcessor creditCard = new CreditCardPayment();
        PaymentProcessor samplePay = new SamplePayAdapter(); // ←ここを追加

        System.out.println(creditCard.getPaymentMethod() + "を使用します");
        creditCard.pay(3000);

        /* ここを追加（ここから） */
        System.out.println();

        System.out.println(samplePay.getPaymentMethod() + "を使用します");
        samplePay.pay(3000);
        /* ここを追加（ここまで） */
    }
}
```

**出力結果**

```
クレジットカードを使用します
クレジットカードで 3000円 支払いました

サンプルPayを使用します
サンプルPayで 3000円 決済します
```

`PaymentProcessor` インターフェースを実現した `SamplePayAdapter` クラスを作成します。これにより、`CreditCardPayment` クラスと同じ `PaymentProcessor` 型で変数を宣言できるようになります。<br>
また、内部で `SamplePayClient` クラスのインスタンスを持つようにします。これにより、`SamplePayClient` クラスのメソッドを `PaymentProcessor` インターフェースで定義された共通のメソッドから呼び出せるようになります。

以上より、実績のあるコードを変更せずに再利用しながら、既存コードのルールに従った追加実装を行うことができます。

加えて、以下のメリットも得られます。

- `PaymentProcessor` インターフェースのメソッドの呼び出しが統一される
- `Main` クラスには `SamplePayClient` クラスのメソッドが一切登場せず、外部クラスの存在は `SamplePayAdapter` クラスの内部に隠される
- 将来 `SamplePayAdapter` クラスの内部実装が変わっても、`Main` クラスの修正は不要
- 既存コードはすでにテスト済みのため、再テストは不要

このようにメソッドの処理を他のインスタンスに委ねる実装を「委譲を使った Adapter パターン」といいます。[^1]

## 正しい実装②（継承を使った Adapter パターン）

正しい実装①では、「委譲を使った Adapter パターン」を示しましたが、Adapter パターンには「継承を使った Adapter パターン」もあります。

`Main.java` は正しい実装①と同様なので、`SamplePayAdapter.java` のコードを次に示します。

```Java:SamplePayAdapter.java
public class SamplePayAdapter extends SamplePayClient implements PaymentProcessor {

    @Override
    public void pay(int amount) {
        charge(amount);
    }

    @Override
    public String getPaymentMethod() {
        return getServiceName();
    }
}
```

**出力結果**

```
クレジットカードを使用します
クレジットカードで 3000円 支払いました

サンプルPayを使用します
サンプルPayで 3000円 決済します
```

`SamplePayAdapter` クラスが `SamplePayClient` を継承しているため、スーパークラスである `SamplePayClient` のメソッドを直接呼んでいることがわかります。<br>
この実装でも正しい実装①のメリットと同様のメリットが得られます。

## 「委譲を使った Adapter パターン」と「継承を使った Adapter パターン」の使い分け

結論から先に伝えると、基本的には「委譲を使った Adapter パターン」を使用するのが良いです。<br>
確かに、今回の例ではどちらの実装でも正しく動作するため、使い分けの必要性が感じられないと思います。<br>
しかし実務では、現時点で動くかどうかだけでなく、将来の変更を見越した選択が重要となります。

例えば「全決済クラスに共通のログ処理（`AppLogger` クラス）を追加したい」という要件がきたとしましょう。<br>
継承を使ったパターンでは、`SamplePayAdapter` がすでに `SamplePayClient` を `extends` しているため、Java の単一継承の制約から `AppLogger` クラスをさらに `extends` できません。[^2]<br>
そのため、次のように `new` でインスタンス化することになります。

```Java:SamplePayAdapter.java
public class SamplePayAdapter extends SamplePayClient implements PaymentProcessor {

    private AppLogger logger = new AppLogger(); // ←ここを追加

    @Override
    public void pay(int amount) {
        logger.log("決済処理: " + amount + "円"); // ←ここを追加
        charge(amount);
    }

    @Override
    public String getPaymentMethod() {
        ...
    }
}
```

一方、委譲を使ったパターンでは、`SamplePayClient` も `AppLogger` もどちらも `new` でインスタンス化する形で統一されます。

```Java:SamplePayAdapter.java
public class SamplePayAdapter implements PaymentProcessor {

    private SamplePayClient samplePayClient = new SamplePayClient();
    private AppLogger logger = new AppLogger(); // ←ここを追加

    @Override
    public void pay(int amount) {
        logger.log("決済処理: " + amount + "円"); // ←ここを追加
        samplePayClient.charge(amount);
    }

    @Override
    public String getPaymentMethod() {
        ...
    }
}
```

2 つのコードを比較すると、以下のような違いがあります。

| 観点                           | 継承を使ったパターン                                                                                              | 委譲を使ったパターン                                                        |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| メソッドの呼び出し方           | `SamplePayClient` は `extends` により直接メソッドを呼び出し、`AppLogger` は変数経由となり呼び出し方に差が生まれる | すべて `new` でインスタンス化するため呼び出し方が統一され、実装の意図が明確 |
| 他の決済クラスとの統一性       | 他クラスが `AppLogger` を `extends` で継承している場合、`SamplePayAdapter` だけ異なる実装になる                   | 全クラスを `new` でインスタンス化する実装にすることで、統一性が保たれる     |
| 外部クラスの内部実装への依存度 | 内部の振る舞いを詳しく理解する必要があり、把握できないバージョンアップで予期しない動作が起きるリスクがある        | 使用するメソッドのみ把握すれば良く、バージョンアップによる影響範囲も狭い    |
| テスト範囲                     | 変更箇所が広がりやすく、既存コードの再テストも必要                                                                | 追加クラスのみとなり明確で、保守・運用コストを抑えられる                    |

以上のことから、基本的には「委譲を使った Adapter パターン」で実装することで、将来の変更に対して柔軟に対応できるようになります。

## まとめ

では、冒頭の問いに戻りましょう。

> 追加実装をする際に、既存のコードを修正することなく実装をするにはどうすればよいでしょうか？

正しい実装①・②から分かるように、Adapter パターンを使用することで、既存のコードに一切手を加えることなく追加実装を行えます。<br>
また、既存コードはすでにテスト済みのため再テストは不要で、変更が行われた Adapter クラスのみテストを行えばよいことから、テスト範囲を最小限に抑えることができます。

冒頭でも述べましたが、「動いているコードには触れたくない」という気持ちは、実務でも自然な感覚です。<br>
Adapter パターンはその感覚を設計として実現する手段の一つと言えるでしょう。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

[^1]: 「委譲」の概念について → 【深堀り①】委譲（Delegation）とは

[^2]: 「単一継承の制約」について → 【深堀り③】Java の単一継承の制約

<a id="深堀り1"></a>

## 【深堀り①】委譲（Delegation）とは

委譲（Delegation）とは、「あるメソッドの実際の処理を別のインスタンスのメソッドに任せる」という設計の考え方です。

本記事の `SamplePayAdapter` では、`pay()` や `getPaymentMethod()` の処理を `samplePayClient` に任せており、これが委譲にあたります。

<a id="深堀り2"></a>

## 【深堀り②】オープン・クローズドの原則（OCP）

本記事の実装を振り返ると、`CreditCardPayment` や `SamplePayClient` には一切手を加えず、`SamplePayAdapter` という新しいクラスを追加するだけで、サンプル Pay 決済を既存システムに組み込みました。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、**OCP（Open/Closed Principle：オープン・クローズドの原則）**と呼ばれる設計原則の実践です。<br>
Adapter パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】Java の単一継承の制約

Java では、`extends` によるクラスの継承は 1 つしかできません（単一継承）。これを「単一継承の制約」といいます。

```Java
// コンパイルエラー（Javaでは多重継承は不可）
public class SamplePayAdapter extends SamplePayClient extends AppLogger {
```

なぜ多重継承が禁止されているのでしょうか。<br>
例えば、`ClassA` と `ClassB` の両方に `doSomething()` というメソッドが存在するとしましょう。両方を継承すると「どちらの `doSomething()` を呼べばよいか」が曖昧になります。これを「ダイヤモンド問題」と呼びます。Java はこの問題を避けるために、クラスの多重継承を禁止しています。

一方、インターフェースは複数 `implements` できます。<br>
インターフェースはメソッドの実装を持たないため、ダイヤモンド問題が起きません。

```Java
// OK: インターフェースは複数implements可能
public class SamplePayAdapter extends SamplePayClient implements PaymentProcessor, Serializable {
```

この制約が、「委譲を使った Adapter パターン」と「継承を使った Adapter パターン」の使い分けセクションで述べた、「継承を使った Adapter パターンでは、後から `AppLogger` クラスをさらに `extends` できない」という問題の背景です。

<a id="深堀り4"></a>

## 【深堀り④】GoF デザインパターンとの位置づけ

今回使った Adapter パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「構造パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
