# ― Adapterパターン

追加実装をする際に、既存のコードを修正することなく実装をするにはどうすればよいでしょうか？

この記事では、このような問題を解決する「Adapterパターン」を具体例を通して学びます。

## 【具体例】

### シナリオ

> あなたはECサイトの開発チームに所属しています。
> これまでクレジットカード決済のみ対応していましたが、若年層ユーザーの利用率向上を目的に、QRコード決済「サンプルPay」を追加することになりました。
> サンプルPay社からは外部クラスが提供されていますが、メソッド名が既存システムと異なるため、そのまま組み込むことができない状態です。

### 既存コードの仕様

- PaymentProcessor（インターフェース）

ECサイトの決済機能における共通インターフェースであり、全ての決済クラスはこれを実装する必要があります。
新しい決済手段を追加する際にはこのインターフェースを通して実現することで、既存の処理と統一した形で扱えるようになります。

| メソッド                    | 説明             |
| --------------------------- | ---------------- |
| `void pay(int amount)`      | 指定金額を支払う |
| `String getPaymentMethod()` | 決済手段名を返す |

```Java:PaymentProcessor.java
public interface PaymentProcessor {
    void pay(int amount);
    String getPaymentMethod();
}
```

- CreditCardPayment（既存サービスの実装クラス）

クレジットカード決済を行うクラスとなります。
リリース済みで稼働中のため、変更によるリグレッションは避ける必要があります。

| メソッド             | 動作                                                |
| -------------------- | --------------------------------------------------- |
| `pay(int amount)`    | "クレジットカードで {amount}円 支払いました" を出力 |
| `getPaymentMethod()` | "クレジットカード" を返す                           |

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

- SamplePayClient（サンプルPay社から提供された追加したい外部クラス・変更不可）

SamplePay社が提供する外部クラスとなります。
こちらは、社外のコードのため変更できません。
下記を見て分かるように、既存サービスのインタフェースである `PaymentProcessor` とメソッド名が異なるため、そのままでは組み込めない状態です。

| メソッド                  | 動作                                    |
| ------------------------- | --------------------------------------- |
| `void charge(int yen)`    | "SamplePayで {yen}円 決済します" を出力 |
| `String getServiceName()` | "SamplePay" を返す                      |

```Java:SamplePayClient.java
public class SamplePayClient {

    public void charge(int yen) {
        System.out.println("SamplePayで " + yen + "円 決済します");
    }

    public String getServiceName() {
        return "SamplePay";
    }
}
```

- 実行クラス

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        PaymentProcessor creditCard = new CreditCardPayment();

        System.out.println(creditCard.getPaymentMethod() + "を使用します");
        creditCard.pay(3000);
    }
}
```

## 好ましくない実装①

シナリオに「サンプルPay社から提供された追加したい外部クラス」は変更がNGであること記載されているが、もしこの要件を見落としていたら以下のように実装を行ってしまうと考えられます。

PaymentProcessor（インターフェース）を実現する必要があるので、下記のようにすると思います。

```Java
public class SamplePayClient implements PaymentProcessor {
    〜省略〜
}
```

ただ、PaymentProcessorで実装しないといけないメソッドは

- `void pay(int amount)`
- `String getPaymentMethod()`

のため、SamplePayClientクラスにはないことから追加でオーバーライドする必要があります。
最終的に、以下のような実装になると思います。

```Java:SamplePayClient.java
public class SamplePayClient implements PaymentProcessor {

    @Override
    public void pay(int amount) {
        charge(amount);
    }

    @Override
    public String getPaymentMethod() {
        return getServiceName();
    }

    public void charge(int yen) {
        System.out.println("SamplePayで " + yen + "円 決済します");
    }

    public String getServiceName() {
        return "SamplePay";
    }
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        PaymentProcessor creditCard = new CreditCardPayment();

        PaymentProcessor samplePay1 = new SamplePayClient(); // ←ここを追加

        SamplePayClient samplePay2 = new SamplePayClient(); // ←ここを追加

        System.out.println(creditCard.getPaymentMethod() + "を使用します");
        creditCard.pay(3000);

        System.out.println(samplePay1.getPaymentMethod() + "を使用します"); // ←ここを追加
        samplePay1.pay(3000); // ←ここを追加

        System.out.println(samplePay2.getPaymentMethod() + "を使用します"); // ←ここを追加
        samplePay2.pay(3000); // ←ここを追加
        System.out.println(samplePay2.getServiceName() + "を使用します"); // ←ここを追加
        samplePay2.charge(3000); // ←ここを追加
    }
}
```

**出力結果**

```
クレジットカードを使用します
クレジットカードで 3000円 支払いました
SamplePayを使用します
SamplePayで 3000円 決済します
SamplePayを使用します
SamplePayで 3000円 決済します
SamplePayを使用します
SamplePayで 3000円 決済します
```

しかしこちらの実装に関しては以下のような問題点があります。

- 仕様の確認の際に見落としてしまった、「外部クラスは社外コードのため、変更できません」という仕様の要件を満たしていない
- サンプルPay社に提供していただいた外部クラスが、自社で把握できないタイミングでバージョンアップされた際、コードが上書きされてしまうリスクがある
- 変数の型として複数の候補が出てきてしまい、実装者によって選択にばらつきが発生すると同時に、使えるメソッドも異なってコードの一貫性が保てなくなる
- 決済手段が増えるたびに、実装者によって変数の型の選び方がばらつき、コードの一貫性が保てなくなる
- `SamplePayClient`クラスを直接修正しているため、修正後に再テストが必要になる

上記の問題点から、実務ではレビューで差し戻しになる可能性が高い実装となります。

## 好ましくない実装②

追加実装のため、「外部クラスは社外コードのため、変更できません」という内容は仕様書に書かれる可能性が高いため、要件に沿った実装が行えると思います。
しかし実務では、今回のような少ないクラス数であることはありえず、もっと沢山のクラスがあります。
そのため、「PaymentProcessor（インターフェース）を実現するクラスを作成しないといけない」ということが漏れてしまうことは大いにありえます。また、こちらの件は社内コードのため、仕様には明記されない可能性のほうが高いです。
このことから以下のような実装を行ってしまうことが考えられます。

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        PaymentProcessor creditCard = new CreditCardPayment();

        SamplePayClient samplePay = new SamplePayClient(); // ←ここを追加

        System.out.println(creditCard.getPaymentMethod() + "を使用します");
        creditCard.pay(3000);

        System.out.println(samplePay.getServiceName() + "を使用します"); // ←ここを追加
        samplePay.charge(3000); // ←ここを追加
    }
}
```

**出力結果**

```
クレジットカードを使用します
クレジットカードで 3000円 支払いました
SamplePayを使用します
SamplePayで 3000円 決済します
```

しかしこちらの実装に関しては、以下のような問題点があります。

- 実行クラスで直接外部クラスを呼び出しているため、共通の型で扱えなくなる
- 決済手段が増えるたびに各クラス固有の型で変数を定義することになり、呼び出し側のコードに統一性がなくなる
- その結果、Mainクラスが肥大化し、修正箇所も増え続ける
- `Main`クラスを直接修正しているため、修正後に再テストが必要になる

上記の問題点から、実務ではレビューで差し戻しになる可能性が高い実装となります。

## 正しい実装①

では、PaymentProcessorという共通の型を実現しつつ、SamplePayClientクラスに変更を加えずに実装するにはどうすればよいのでしょうか？

この問題を解決するのが「Adapterパターン」となります。

実装したコードは下記となります。

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

        System.out.println(samplePay.getPaymentMethod() + "を使用します"); // ←ここを追加
        samplePay.pay(3000); // ←ここを追加
    }
}
```

**出力結果**

```
クレジットカードを使用します
クレジットカードで 3000円 支払いました
SamplePayを使用します
SamplePayで 3000円 決済します
```

`SamplePayAdapter`クラスは`PaymentProcessor`インターフェースを実装しているため、`CreditCardPayment`クラスと同じ`PaymentProcessor`型で変数を宣言できます。
また、内部で`SamplePayClient`クラスのインスタンスを持つことで、`PaymentProcessor`インターフェースで定義された共通のメソッドの呼び出しから`SamplePayClient`クラスのメソッドの呼び出しに橋渡しできます。

これにより、実績のあるコードを変更せずに再利用しながら、既存コードのルールに従った追加実装を行うことができます。

加えて、以下のメリットも得られます。

- `creditCard`と`samplePay`で`PaymentProcessor`インターフェースの共通メソッドを統一的に呼び出せる
- `Main`クラスには`SamplePayClient`クラスのメソッドが一切登場せず、外部クラスの存在は`SamplePayAdapter`クラスの内部に隠される
- `SamplePayAdapter`クラスの内部実装が将来変わっても、`Main`クラスの修正は不要
- 既存コードはすでにテスト済みのため、再テストは不要

上記のようにメソッドの処理を他のインスタンスに委ねる実装を「委譲を使ったAdapterパターン」といいます。

## 正しい実装②

正しい実装①では、「委譲を使ったAdapterパターン」を示しましたが、Adapterパターンには「継承を使ったAdapterパターン」もあります。

まずは、コードの確認から行いましょう。

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

Main.javaは正しい実装①と同様。

**出力結果**

```
クレジットカードを使用します
クレジットカードで 3000円 支払いました
SamplePayを使用します
SamplePayで 3000円 決済します
```

SamplePayAdapterクラスがSamplePayClientを継承しているため、親クラスであるSamplePayClientのメソッドを直接呼んでいることがわかります。
この実装でも正しい実装①のメリットと同様のメリットを得ることができます。

## 「委譲を使ったAdapterパターン」と「継承を使ったAdapterパターン」の使い分け

結論から先に伝えると、基本的には「委譲を使ったAdapterパターン」を使用するのが良いです。
確かに、今回の例では、どちらの実装でも正しく動作するため、使い分けの必要性が感じられないかもしれません。
しかし実務では、現時点で動くかどうかだけでなく、将来の変更を見越した選択が重要となります。

継承を使った場合、Javaの単一継承の制約により、SamplePayAdapterは`SamplePayClient`以外のクラスを継承できなくなります。
例えば将来「全決済クラスに共通のログ処理を追加したい」といった要件が生まれたとき、継承を使ったパターンではログ処理を行うクラスを`extends`できないため、インスタンス化で対応するしかありません。
その結果、他の決済クラスと実装方法に差が生まれ、コードの意図が読みにくくなります。
下記の実装コード例を見ても違和感がある事がよくわかります。

```Java:SamplePayAdapter.java
public class SamplePayAdapter extends SamplePayClient implements PaymentProcessor {

    private AppLogger logger = new AppLogger();

    @Override
    public void pay(int amount) {
        ...
    }

    @Override
    public String getPaymentMethod() {
        ...
    }
}
```

加えて、継承を使う場合は親クラスの内部的な振る舞いを詳しく理解している必要があります。SamplePayClientのような提供された外部クラスを継承した場合、自社で把握できないバージョンアップにより内部実装が変わると、SamplePayAdapterで予期しない動作が起きるリスクがあります。

一方で委譲を使った場合、追加クラスが増えても呼び出し方が統一されるため、実装の意図が明確なままになります。
また、テストの範囲も「追加したクラスのみ」と明確になるため、保守・運用コストを抑えることができます。

```Java:SamplePayAdapter.java
public class SamplePayAdapter implements PaymentProcessor {

    private SamplePayClient samplePayClient = new SamplePayClient();

    private AppLogger logger = new AppLogger(); // テスト範囲①

    @Override
    public void pay(int amount) {
        logger.log("決済処理: " + amount + "円"); // テスト範囲②
        samplePayClient.charge(amount);
    }

    @Override
    public String getPaymentMethod() {
        return samplePayClient.getServiceName();
    }
}
```

以上のことから、基本的には「委譲を使ったAdapterパターン」で実装することで、将来の変更に対して柔軟に対応できるようになります。

## まとめ

では、冒頭の問いに戻りましょう。

> 追加実装をする際に、既存のコードを修正することなく実装をするにはどうすればよいでしょうか？

正しい実装①・②から分かるように、Adapterパターンを使用することで、既存のコードに一切手を加えることなく追加実装を行えます。
既存コードはすでにテスト済みのため再テストは不要で、変更が行われたAdapterクラスのみテストを行えばよいため、テスト範囲を最小限に抑えることができます。

「動いているコードには触れたくない」という気持ちは、実務でも自然な感覚です。
Adapterパターンはその感覚を設計として実現する手段の一つと言えるでしょう。
