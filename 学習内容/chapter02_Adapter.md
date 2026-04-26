# ― Adapterパターン

追加実装をする際に、既存のコードを修正することなく実装をするにはどうすればよいでしょうか？

この記事では、このような問題を解決する「Adapterパターン」を具体例を通して学びます。

## 【具体例】

### シナリオ

> あなたはECサイトの開発チームに所属しています。
> これまでクレジットカード決済のみ対応していましたが、若年層ユーザーの利用率向上を目的に、QRコード決済「サンプルPay」を追加することになりました。
> サンプルPay社からはクライアントライブラリが提供されていますが、メソッド名が既存システムと異なるため、そのまま組み込むことができない状態です。
> →[修正の提案]クライアントライブラリという用語をこの時点で出すのは認知的負荷が高い認識なので修正する

### 既存コードの仕様

- PaymentProcessor（インターフェース）

決済処理の共通インターフェースとなります。
ECサイトのチェックアウト処理が依存しており、全ての決済クラスはこれを実装する必要があります。
→[修正の提案]チェックアウト処理がない認識なので、別の表現に修正する
そのため、新しい決済手段を追加する際も、このインターフェースを通じて実装を行う必要があります。

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

SamplePay社が提供するクライアントライブラリとなります。
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

- 仕様の確認の際に見落としてしまった、「外部ライブラリは変更NG」の要件を満たしていない
- 外部ライブラリのため、サンプルPay社がライブラリのバージョンアップを行い、コードが上書きされてしまうリスクがある
- 変数を定義する型によっては、使用できるメソッドが増え統一性がなくなる
- 変数を定義する型が複数あるため統一性がなくなる
- 決済手段が増えるとカオスになる
  →[修正の提案]上記以外の問題点がないかを今一度検証する。

上記の問題点から、好ましくない実装となります。
→[修正の提案]レビュー依頼で必ず差し戻しを食らうっていうことを書くほうが好ましい？？

## 好ましくない実装②

追加実装のため、「外部ライブラリは変更NG」は仕様書に書かれる可能性が高いと思います。
そのため、こちらの件は守れる可能性が高いです。
ただ、実務では、今回のようなクラス数であることはありえず、もっと沢山のクラスがあります。
そのため、「PaymentProcessor（インターフェース）を実現するクラスを作成しないといけない」ということが漏れてしまうことは大いにありえます。また、こちらの件は社内でのコードのため、仕様には明記されません。
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

しかしこちらの実装に関しては以下のような問題点があります。

- PaymentProcessorという共通の型で扱えなくなる
- 決済手段が増えるたびに呼び出し側の書き方がバラバラになり、修正箇所が増え続ける
- 決済手段が増えるたびにMainクラスが肥大化する
  →[修正の提案]上記以外の問題点がないかを今一度検証する。

上記の問題点から、好ましくない実装となります。
→[修正の提案]レビュー依頼で必ず差し戻しを食らうっていうことを書くほうが好ましい？？

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

上記のようにすることで、SamplePayAdapterクラスはPaymentProcessorインタフェースを実装したクラスとなるので、CreditCardPaymentクラスと同様にPaymentProcessor型で変数の宣言をすることができます。
また、SamplePayAdapterクラスのインスタンスを生成したときにSamplePayClientクラスのインスタンスを生成するようにしているため、SamplePayClientクラスのメソッドを呼び出すことができるようにしています。
以上から、上記の変数creditCardを使用したときのメソッドと同じメソッドをsamplePayにて呼び出すとSamplePayAdapterクラスの変数samplePayClientを通してサンプルPay社から提供された追加したい外部クラスのメソッドを呼び出すことができるようになります。
これにより、既存コードの変更をせずに既存コードのルールに従った追加実装を行うことができます。

ちなみに、上記のようにメソッドの実際の処理を他のインスタンスのメソッドに任せるようなAdapterパターンを「委譲を使ったAdapterパターン」といいます。
→[修正の提案]上記ような実装をするメリットの記載をするとなお良いかも
→Mainクラスのコードから外部ライブラリであるSamplePayClientクラスのメソッドが隠されていることが分かる
→また、SamplePayAdapterクラスがどのように実現されているかを知らない
→以上から、Mainクラスのコードを修正することなくSamplePayAdapterクラスの実装を変えることができる。
→既存コードはすでにテスト済みのため、再度既存コードのテストを行わなくても良くなるというメリットは必ず盛り込みたい
→既存コードを再利用できる
　→十分にテストされ、バグが少なく、実際にこれまで使われてきた実績があるならなおさら再利用したい
→好ましくない実装では、既存クラスを修正するため修正後にもう一度テストをする必要が出てくる（こちらの件に関しては、好ましくないコードの実装の問題点の部分に持っていったほうが良い気がする）
→古い版と新しい版とを共存させてメンテナンスを楽にすることができる

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

正しい実装①や正しい実装②から分かるように、Adapterパターンを使用することで、既存のコードに一切手を加えることなく追加実装を行えます。
また、既存コードはすでにテスト済みのため、再度テストを行う必要もありません。
これにより、変更が必要なのは追加したAdapterクラスのみとなるため、テスト範囲を最小限に抑えることができます。

「動いているコードには触れたくない」という気持ちは、実務でも自然な感覚です。
Adapterパターンはその感覚を設計として実現する手段の一つと言えるでしょう。
