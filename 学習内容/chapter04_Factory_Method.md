# Factory Method パターン ― インスタンス生成をサブクラスに委ねる

次のようなコードを書いた経験はありませんか？

> この処理は A 用だから new ClassA()、これは B 用だから new ClassB()……と大量の条件ブロックを記述した

また、次のようなコードを引き継いだ経験はありませんか？

> new 具体クラス() が呼び出し元のあちこちに散らばっている

この記事では、社員入館管理システムに来訪者カード機能を追加するシナリオを通して、Factory Method パターンがこの問題をどのように解決するかを学びます。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】Template Method パターンとの関係](#深堀り1)
- [【深堀り②】DIP と OCP](#深堀り2)
    - [DIP（依存性逆転の原則）](#dip依存性逆転の原則)
    - [OCP（開放閉鎖原則）](#ocp開放閉鎖原則)
- [【深堀り③】static Factory Method との違い](#深堀り3)
- [【深堀り④】文字列連結時の `toString()` 自動呼び出し](#深堀り4)
- [【深堀り⑤】GoF デザインパターンとの位置づけ](#深堀り5)

---

## 【具体例】

### シナリオ

> あなたは企業の情報システム部門に所属しています。<br>
> 社員証による自社社員の入退館管理システムはすでに稼働しています。<br>
> ある日、総務部から「来訪者の入退館管理も追加してほしい」という依頼が来ました。<br>
> あなたは以下を担当します。
>
> - 来訪者 1 名に対して 1 枚の来訪者用カードを発行し、発行時にコンソールへ出力する
> - 発行した来訪者用カードを登録し、コンソールへ出力する<br>
>   ※登録時は本来、DB に登録するのが一般的だが、本記事では Factory Method パターンの解説に集中するため、コンソールへの出力のみとする

※実際の入退館管理では退館処理も必要ですが、本記事では Factory Method パターンの解説に集中するため、入館処理のみを扱います。

### 既存コードの仕様

- `EmployeeCard`（既存クラス）

社員名とカード番号を管理するクラスです。<br>
社員 1 名に対して 1 枚の社員証を発行するため、コンストラクタで社員名とカード番号を受け取り、発行時にコンソールへ出力します。

| フィールド           | 型       | 説明             |
| -------------------- | -------- | ---------------- |
| `employeeName`       | `String` | 社員の氏名       |
| `employeeCardNumber` | `int`    | 社員証の発行番号 |

| メソッド      | 説明                                                          |
| ------------- | ------------------------------------------------------------- |
| `void pass()` | 社員証でゲートを通過する<br>※ここでの `this` に関する説明[^1] |

```Java:EmployeeCard.java
public class EmployeeCard {
    private String employeeName;
    private int employeeCardNumber;

    public EmployeeCard(String employeeName, int employeeCardNumber) {
        System.out.println(employeeName + " さんの社員証を " + employeeCardNumber + " 番で発行します。");
        this.employeeName = employeeName;
        this.employeeCardNumber = employeeCardNumber;
    }

    public void pass() {
        System.out.println(this + " でゲートを通過します。");
    }

    @Override
    public String toString() {
        return "[社員証" + employeeCardNumber + "：" + employeeName + "]";
    }
}
```

<br>

- `Main`（実行クラス）

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        EmployeeCard employeeCard1 = new EmployeeCard("田中 太郎", 1001);
        EmployeeCard employeeCard2 = new EmployeeCard("山田 花子", 1002);

        employeeCard1.pass();
        employeeCard2.pass();
    }
}
```

**実行結果**

```
田中 太郎 さんの社員証を 1001 番で発行します。
山田 花子 さんの社員証を 1002 番で発行します。
[社員証1001：田中 太郎] でゲートを通過します。
[社員証1002：山田 花子] でゲートを通過します。
```

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

既存のコードがあるので、`EmployeeCard` を参考に、以下のような実装をするのではないでしょうか？

```Java:VisitorCard.java
public class VisitorCard {
    private String visitorName;
    private int visitorCardNumber;

    public VisitorCard(String visitorName, int visitorCardNumber) {
        System.out.println(visitorName + " さんの来訪者カードを " + visitorCardNumber + " 番で発行します。");
        this.visitorName = visitorName;
        this.visitorCardNumber = visitorCardNumber;
    }

    public void pass() {
        System.out.println(this + " でゲートを通過します。");
    }

    @Override
    public String toString() {
        return "[来訪者カード" + visitorCardNumber + "：" + visitorName + "]";
    }
}
```

```Java:Main.java
public class Main {

    public static void main(String[] args) {
        EmployeeCard employeeCard1 = new EmployeeCard("田中 太郎", 1001);
        EmployeeCard employeeCard2 = new EmployeeCard("山田 花子", 1002);
        /* ここを追加（ここから） */
        System.out.println();

        VisitorCard visitorCard1 = new VisitorCard("鈴木 次郎", 1);
        System.out.println("[登録記録] " + visitorCard1 + " を登録しました。");
        VisitorCard visitorCard2 = new VisitorCard("伊藤 咲子", 2);
        System.out.println("[登録記録] " + visitorCard2 + " を登録しました。");

        System.out.println();
        /* ここを追加（ここまで） */


        employeeCard1.pass();
        employeeCard2.pass();

        /* ここを追加（ここから） */
        System.out.println();

        visitorCard1.pass();
        visitorCard2.pass();
        /* ここを追加（ここまで） */
    }
}
```

**実行結果**

```
田中 太郎 さんの社員証を 1001 番で発行します。
山田 花子 さんの社員証を 1002 番で発行します。

鈴木 次郎 さんの来訪者カードを 1 番で発行します。
[登録記録] [来訪者カード1：鈴木 次郎] を登録しました。
伊藤 咲子 さんの来訪者カードを 2 番で発行します。
[登録記録] [来訪者カード2：伊藤 咲子] を登録しました。

[社員証1001：田中 太郎] でゲートを通過します。
[社員証1002：山田 花子] でゲートを通過します。

[来訪者カード1：鈴木 次郎] でゲートを通過します。
[来訪者カード2：伊藤 咲子] でゲートを通過します。
```

コンパイルエラーがなく結果が出力されていることから、実装・動作確認ともに問題ないことがわかります。

しかし、この実装には以下の問題点があります。

- 仕様変更のたびに、全クラスへの修正が必要になる
    - その結果、追加実装のたびにクラスが増え、仕様変更に伴う修正の漏れが発生するリスクが高くなる
- 「発行 → 登録」という手順が呼び出し元の `Main` クラス側に直接書かれているため、正しい手順を呼び出す側が管理しないといけない
- 機能追加時に既存クラスを見て真似るしかないため、構造が同じクラスが無秩序に増え続け、設計の一貫性が保てない
- `EmployeeCard`・`VisitorCard` に共通の型がないため、一括で扱うことができない（例えば、`pass` メソッドの呼び出し方が同じため、下記のように処理をまとめようとしても、まとめることができない）

    ```java
    // 共通の型がないので型が決められない
    List<???> managements = new ArrayList<>();
    managements.add(new EmployeeCard("田中 太郎", 1001));
    managements.add(new EmployeeCard("山田 花子", 1002));
    managements.add(new VisitorCard("鈴木 次郎", 1));
    managements.add(new VisitorCard("伊藤 咲子", 2));

    for (??? management : managements) {
        management.pass();
    }
    ```

- 通し番号を表す `visitorCardNumber` の採番が、呼び出し元の `Main` クラスに委ねられているため、発行するたびに呼び出す側が手動で番号を決める必要がある
    - その結果、対象が増えるほど採番ミスや重複が発生しやすくなる

## 正しい実装

では、好ましくない実装で触れた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Factory Method パターン**です。<br>
まずは、次のコードを見てください。

※本記事では下記の構成としています。

**クラス構成：**

```
framework パッケージ（変更不可のスーパークラス）
  ├── Management.java 抽象クラス：入退館管理のための共通インターフェース
  └── Factory.java    抽象クラス：「発行 → 登録」の手順を定義

visitorcard パッケージ（具体的な実装を行うサブクラス）
  ├── VisitorCard.java        Management のサブクラス
  └── VisitorCardFactory.java Factory のサブクラス
```

```Java:Management.java
package framework;

public abstract class Management {
    public abstract void pass();
}
```

```Java:Factory.java
package framework;

public abstract class Factory {

    protected abstract Management createManagement(String person);

    protected abstract void registerManagement(Management management);

    public final Management create(String person) {
        Management management = createManagement(person);
        registerManagement(management);

        return management;
    }
}
```

上記のスーパークラスを見ると、`create` メソッドの戻り値と変数の型がいずれも抽象クラス `Management` であることがわかります。<br>
このことから、スーパークラスは具体的なクラスを知る必要がなく、インスタンス生成のための枠組みだけを定義していることが読み取れます。<br>
また、`createManagement` メソッドと `registerManagement` メソッドは修飾子 `abstract` がついているため、実際の処理内容はサブクラスに委ねられていることも読み取れます。

以上のように、インスタンスの生成処理をサブクラスに委ねることで、インスタンス生成のための枠組みを定義するクラスと実際のインスタンス生成を行うクラスとを分離するパターンが **Factory Method パターン**です。

さらに、`Factory` クラスの `create` メソッドに `final` がついています。<br>
このことから、サブクラスは `create` メソッドを上書きできないため、処理が固定化（「発行 → 登録」という手順が変わらない）されることが読み取れます。

次に、サブクラスのコードを見ていきましょう。

```Java:VisitorCard.java
package visitorcard;

import framework.Management;

public class VisitorCard extends Management {
    private String visitorName;
    private int visitorCardNumber;

    public VisitorCard(String visitorName, int visitorCardNumber) {
        System.out.println(visitorName + " さんの来訪者カードを " + visitorCardNumber + " 番で発行します。");
        this.visitorName = visitorName;
        this.visitorCardNumber = visitorCardNumber;
    }

    @Override
    public void pass() {
        System.out.println(this + " でゲートを通過します。");
    }

    @Override
    public String toString() {
        return "[来訪者カード" + visitorCardNumber + "：" + visitorName + "]";
    }
}
```

```Java:VisitorCardFactory.java
package visitorcard;

import framework.Factory;
import framework.Management;

public class VisitorCardFactory extends Factory {
    private int visitorCardNumber = 0;

    @Override
    protected Management createManagement(String person) {
        return new VisitorCard(person, ++visitorCardNumber);
    }

    @Override
    protected void registerManagement(Management management) {
        System.out.println("[登録記録] " + management + " を登録しました。");
    }
}
```

上記のサブクラスを見ると、`createManagement` メソッドで `new VisitorCard(...)` と記述していることから、具体的なインスタンス生成をサブクラスが担っていることがわかります。

実行クラスでは次のようなコードとなり、出力結果は下記となります。

```Java:Main.java
import framework.Factory;
import framework.Management;
import visitorcard.VisitorCardFactory;

public class Main {
    public static void main(String[] args) {
        EmployeeCard employeeCard1 = new EmployeeCard("田中 太郎", 1001);
        EmployeeCard employeeCard2 = new EmployeeCard("山田 花子", 1002);

        /* ここを追加（ここから） */
        System.out.println();

        Factory visitorCardFactory = new VisitorCardFactory();
        Management visitorCard1 = visitorCardFactory.create("鈴木 次郎");
        Management visitorCard2 = visitorCardFactory.create("伊藤 咲子");

        System.out.println();
        /* ここを追加（ここまで） */

        employeeCard1.pass();
        employeeCard2.pass();

        /* ここを追加（ここから） */
        System.out.println();

        visitorCard1.pass();
        visitorCard2.pass();
        /* ここを追加（ここまで） */
    }
}
```

**実行結果**

```
田中 太郎 さんの社員証を 1001 番で発行します。
山田 花子 さんの社員証を 1002 番で発行します。

鈴木 次郎 さんの来訪者カードを 1 番で発行します。
[登録記録] [来訪者カード1：鈴木 次郎] を登録しました。
伊藤 咲子 さんの来訪者カードを 2 番で発行します。
[登録記録] [来訪者カード2：伊藤 咲子] を登録しました。

[社員証1001：田中 太郎] でゲートを通過します。
[社員証1002：山田 花子] でゲートを通過します。

[来訪者カード1：鈴木 次郎] でゲートを通過します。
[来訪者カード2：伊藤 咲子] でゲートを通過します。
```

ここで補足です。<br>
実務では工数が決まっているため、既存コードである `EmployeeCard` クラスは修正していません。しかし、追加実装をしたうえで、今後のメンテナンスのしやすさを PM に伝えた場合、修正の許可が出る場合もあると思います。<br>
その場合は以下のようになります。

**クラス構成：**

```
employeecard パッケージ（具体的な実装を行うサブクラス）
  ├── EmployeeCard.java        Management のサブクラス
  └── EmployeeCardFactory.java Factory のサブクラス
```

```Java:EmployeeCard.java
package employeecard;

import framework.Management;

public class EmployeeCard extends Management {
    private String employeeName;
    private int employeeCardNumber;

    public EmployeeCard(String employeeName, int employeeCardNumber) {
        System.out.println(employeeName + " さんの社員証を " + employeeCardNumber + " 番で発行します。");
        this.employeeName = employeeName;
        this.employeeCardNumber = employeeCardNumber;
    }

    @Override
    public void pass() {
        System.out.println(this + " でゲートを通過します。");
    }

    @Override
    public String toString() {
        return "[社員証" + employeeCardNumber + "：" + employeeName + "]";
    }
}
```

```Java:EmployeeCardFactory.java
package employeecard;

import framework.Factory;
import framework.Management;

public class EmployeeCardFactory extends Factory {
    private int employeeCardNumber = 1000;

    @Override
    protected Management createManagement(String person) {
        return new EmployeeCard(person, ++employeeCardNumber);
    }

    @Override
    protected void registerManagement(Management management) {
        // 既存の仕様では、登録処理をしていないため下記をコメントアウトしているが、
        // PM から既存の仕様を追加実装に合わせてもよいという許可が出た場合は、以下のコメントアウトを外す
        // System.out.println("[登録記録] " + management + " を登録しました。");
    }
}
```

```Java:Main.java
import employeecard.EmployeeCardFactory; // ←ここを追加
import framework.Factory;
import framework.Management;
import visitorcard.VisitorCardFactory;

public class Main {
    public static void main(String[] args) {
        /* ここを追加（ここから） */
        Factory employeeCardFactory = new EmployeeCardFactory();
        Management employeeCard1 = employeeCardFactory.create("田中 太郎");
        Management employeeCard2 = employeeCardFactory.create("山田 花子");
        /* ここを追加（ここまで） */

        System.out.println();

        Factory visitorCardFactory = new VisitorCardFactory();
        Management visitorCard1 = visitorCardFactory.create("鈴木 次郎");
        Management visitorCard2 = visitorCardFactory.create("伊藤 咲子");

        System.out.println();

        employeeCard1.pass();
        employeeCard2.pass();

        System.out.println();

        visitorCard1.pass();
        visitorCard2.pass();
    }
}
```

実行結果は同様。

上記のコードの本質的な部分を抽出したコードが下記となります（社員証の入退館システムの方を提示している）。

```Java:Main.java
〜省略〜

public class Main {
    public static void main(String[] args) {
        Factory factory = new EmployeeCardFactory();
        // Factory factory = new VisitorCardFactory();
        Management management1 = factory.create("田中 太郎");
        Management management2 = factory.create("山田 花子");

        management1.pass();
        management2.pass();
    }
}
```

上記のコードを見ると、追加実装では「どのサブクラスをインスタンス化するか」だけで決まっていることがわかります（コメントアウトしている `VisitorCardFactory` の方をインスタンス生成すれば、来訪者カードの入退館システムになるということです）。

このような Factory Method パターンの実装を行うと以下のメリットがあります。

- 仕様変更があった際、修正箇所が明確で修正漏れのリスクが低い
    - もし根本的な処理を変更したい場合は、スーパークラスの修正のみ行えば良い
    - テスト時に発覚したバグなど、具体的な実装の修正の場合は、対象のサブクラスのみ修正を行えば良い
- 「発行 → 登録」という手順がスーパークラスで定義されているため、追加実装の際に処理の流れを気にする必要がない
- 新しい種類を追加する際は追加実装用のサブクラスを作成するだけでよく、各サブクラスが独立しているため一方の変更がもう一方に影響せず、設計の一貫性を担保できる
- サブクラスを共通の型で呼び出せる（[好ましくない実装](#好ましくない実装)で触れた「処理がまとめられなかった件」の解決ができる）

    ```java
    Factory employeeCardFactory = new EmployeeCardFactory();
    Factory visitorCardFactory = new VisitorCardFactory();
    // 共通の型で定めることができる
    List<Management> managements = new ArrayList<>();
    managements.add(employeeCardFactory.create("田中 太郎"));
    managements.add(employeeCardFactory.create("山田 花子"));
    managements.add(visitorCardFactory.create("鈴木 次郎"));
    managements.add(visitorCardFactory.create("伊藤 咲子"));

    for (Management management : managements) {
        management.pass();
    }
    ```

- 通し番号に関して、`Factory` クラスを継承したサブクラスで管理するため、採番ミスや重複が発生しない

上記以外のメリットとして、別システムへの転用ができることが挙げられます。<br>
例えば、「病院の受付システムからも同じフレームワークを使いたいという要件が来た」としましょう。その際は、`employeecard` パッケージを作成したときと同様に、下記のパッケージを追加するだけで実装ができます。この時、**`framework` パッケージのコードは 1 行も変更しません。**

```
patientticket パッケージ
  ├── PatientTicket.java        Management のサブクラス
  └── PatientTicketFactory.java Factory のサブクラス
```

このように、`framework` に手を加えることなく、全く異なるドメインのシステムへ転用できるといったメリットが Factory Method パターンにはあります。

## まとめ

正しい実装の実行クラスを見ると、変数の型はスーパークラスのみです。<br>
そのため、サブクラスの内部実装を知っている必要がありません。<br>
つまり、Factory Method パターンは、サブクラスをカプセル化することができます。

また、追加実装の際は、スーパークラスへの変更も処理の流れへの意識も不要です。<br>
新しいサブクラスを追加するだけで設計者の意図通りの実装が行えるため、品質を均一に保つことができます。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

[^1]: `pass` メソッド内の `this + "..."` という書き方は、Java の文字列連結時に `toString()` が自動呼び出しされる仕組みを利用しています。詳細は[【深堀り④】](#深堀り4)を参照。

---

<a id="深堀り1"></a>

## 【深堀り①】Template Method パターンとの関係

本記事の `Factory` クラスを改めて見てみましょう。

```Java:Factory.java
public abstract class Factory {

    protected abstract Management createManagement(String person);

    protected abstract void registerManagement(Management management);

    public final Management create(String person) {
        Management management = createManagement(person);
        registerManagement(management);

        return management;
    }
}
```

上記のコードからは最終的にどんな処理を行うかはわかりませんが、処理の枠組みは定められていることがわかります。<br>
このことから、処理の枠組みが定まった `Factory` を継承し、実装が強制された 2 つの抽象メソッド（`createManagement`・`registerManagement`）を実装することで具体的な処理が確定することが読み取れます。

このように、スーパークラスで処理の枠組みを定め、サブクラスで具体的な処理内容を定めるようなデザインパターンを **Template Method パターン**と言います。<br>
また、`create` メソッドに `final` がついているため、サブクラスはこのメソッドを上書きできず、処理が固定化されます。このようなメソッドを「**テンプレートメソッド**」と言います。

つまり、**Template Method パターンをインスタンス生成の場面に適用したものが Factory Method パターン**なのです。

### なぜ抽象クラスか

ここで、`create` メソッドに関して、インターフェースの `default` メソッド（Java 8 以降）を使えば、抽象クラスではなくインターフェースで実装できるのではないか？<br>
このように思った方がいるかもしれません。

しかし、インターフェースで記述するのは好ましくありません。<br>
なぜなら、インターフェースの `default` メソッドには修飾子 `final` をつけることができないため、インターフェースを実現したクラスで `create` メソッドを上書きできてしまうからです。<br>

Template Method パターンの本質は「流れは固定し、中身だけを差し替える」という構造にあります。<br>
`create` メソッドに `final` をつけることができ、「流れを守らせる強制力」を表現できる抽象クラスが Template Method パターンを成立させる実装方法となります。

<a id="深堀り2"></a>

## 【深堀り②】DIP と OCP

Factory Method パターンには、設計原則の観点から 2 つの側面があります。

### DIP（依存性逆転の原則）

正しい実装の実行クラスを見てみましょう。

```Java:Main.java
import employeecard.EmployeeCardFactory;
import framework.Factory;
import framework.Management;

public class Main {
    public static void main(String[] args) {
        Factory factory = new EmployeeCardFactory();
        Management management1 = factory.create("田中 太郎");
        Management management2 = factory.create("山田 花子");

        management1.pass();
        management2.pass();
    }
}
```

変数 `factory` の型は抽象クラス `Factory`、変数 `management1` の型は抽象クラス `Management` です。<br>
実行クラスはサブクラス（`EmployeeCard`・`EmployeeCardFactory`）の内部実装を知らなくても動作できています。

変数 `factory` を定める時のみサブクラスに触れています（`new EmployeeCardFactory()`）が、ここを別のサブクラス（例えば `new VisitorCardFactory()`）に差し替えるだけで、実行クラス全体の挙動を切り替えることができます。

このように、「具体的なクラス（`EmployeeCard`・`EmployeeCardFactory`）ではなく、抽象クラス（`Management`・`Factory`）に依存して実装する」という設計は、「**DIP（Dependency Inversion Principle：依存性逆転の原則）**」と呼ばれる設計原則の実践です。Factory Method パターンは DIP を実現するための設計手段の一つと言えます。

DIP を守ることで、スーパークラスを修正することなく具体的な実装を追加・変更できるようになります。

### OCP（開放閉鎖原則）

既存コードが「正しい実装のコード」となっている段階で、若手にリファクタリングを依頼したとしましょう。

「`EmployeeCardFactory` と `VisitorCardFactory` はほぼ同じコードなので、個別に作るのは冗長だ」という理由で、生成処理を 1 か所にまとめ、インスタンス化も省いた次のようなコードが PR として来るかもしれません。

```Java:CardFactory.java
public class CardFactory {
    private static int number = 0;

    public static void create(String type, String name) {
        if (type.equals("employee")) {
            EmployeeCard card = new EmployeeCard(name, 1000 + ++number);
            card.pass();
        } else if (type.equals("visitor")) {
            VisitorCard pass = new VisitorCard(name, ++number);
            System.out.println("[登録記録] " + pass + " を登録しました。");
            pass.pass();
        }
    }
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        CardFactory.create("employee", "田中 太郎");
        CardFactory.create("employee", "山田 花子");
        System.out.println();
        CardFactory.create("visitor", "鈴木 次郎");
        CardFactory.create("visitor", "伊藤 咲子");
    }
}
```

**実行結果**

```
田中 太郎 さんの社員証を 1001 番で発行します。
[社員証1001：田中 太郎] でゲートを通過します。
山田 花子 さんの社員証を 1002 番で発行します。
[社員証1002：山田 花子] でゲートを通過します。

鈴木 次郎 さんの来訪者カードを 3 番で発行します。
[登録記録] [来訪者カード3：鈴木 次郎] を登録しました。
[来訪者カード3：鈴木 次郎] でゲートを通過します。
伊藤 咲子 さんの来訪者カードを 4 番で発行します。
[登録記録] [来訪者カード4：伊藤 咲子] を登録しました。
[来訪者カード4：伊藤 咲子] でゲートを通過します。
```

実行結果の時点で「来訪者カード」の通し番号にバグがあるので、実装ミスに気がつくと思います。<br>
また、次の問題もあります。

- 追加実装するたびに、`CardFactory` 自体に条件分岐が増えていく
- `static` メソッドはサブクラスで上書きできない（`@Override` アノテーションをつけるとコンパイルエラーが発生）

Factory Method パターンでは、[正しい実装](#正しい実装)で見たように、スーパークラスや既存のサブクラスを修正することなく、機能の追加ができています。<br>
このような「既存コードを修正せず、新しいクラスを追加することで拡張する」という設計は、「**OCP（Open/Closed Principle：開放閉鎖原則）**」と呼ばれる設計原則の実践です。Factory Method パターンは OCP を実現するための設計手段の一つと言えます。<br>
上記のリファクタリングは既存コードを変更して機能の拡張をしているため、OCP に反しており差し戻しが妥当です。

また、「`static` メソッドはサブクラスで上書きできない」という問題は、「生成処理をサブクラスに委ねる」という Factory Method パターンの根幹が成立していないことを意味しています。この点でも差し戻しが妥当です。<br>
※詳細は[【深堀り③】static Factory Method との違い](#深堀り3)を参照してください。

<a id="深堀り3"></a>

## 【深堀り③】static Factory Method との違い

[【深堀り①】Template Method パターンとの関係](#深堀り1)で、「Template Method パターンをインスタンス生成の場面に適用したものが Factory Method パターン」という話をしました。Java ではインスタンスを生成するために、クラスメソッドを用いることがあります。このようなインスタンス生成のためのクラスメソッドのことを「**static Factory Method**」といいます。

ここでは、static Factory Method と Factory Method パターンの違いに関して学びます。

馴染み深い `List` の `of` メソッドと `Arrays` の `asList` メソッドを例に見ていきましょう。

```Java
List<String> list  = List.of("sample1", "sample2", "sample3");
List<String> list2 = Arrays.asList("sample1", "sample2", "sample3");
```

```Java
public interface List<E> extends SequencedCollection<E> {
    static <E> List<E> of(E e1, E e2, E e3) {
        return ImmutableCollections.listFromTrustedArray(e1, e2, e3);
    }
}
```

> 引用元: OpenJDK [List.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/List.java)

```Java
class ImmutableCollections {
    static <E> List<E> listFromTrustedArray(Object... input) {
        assert input.getClass() == Object[].class;
        for (Object o : input) { // implicit null check of 'input' array
            Objects.requireNonNull(o);
        }

        return switch (input.length) {
            case 0 -> (List<E>) ImmutableCollections.EMPTY_LIST;
            case 1 -> (List<E>) new List12<>(input[0]);
            case 2 -> (List<E>) new List12<>(input[0], input[1]);
            default -> (List<E>) new ListN<>(input, false);
        };
    }
}
```

> 引用元: OpenJDK [ImmutableCollections.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/ImmutableCollections.java)

```Java
public final class Arrays {
    public static <T> List<T> asList(T... a) {
        return new ArrayList<>(a);
    }
}
```

> 引用元: OpenJDK [Arrays.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/Arrays.java)

上記から、どちらも `new` キーワードを用いずに `List` のインスタンスを生成していることがわかります。

このように、static Factory Method は実装の詳細を隠蔽することが目的である一方、Factory Method パターンは「どのサブクラスを生成するか」をサブクラスに委ね、拡張を容易にすることが目的です。

詳しくは「static Factory Method」で検索してみてください。

<a id="深堀り4"></a>

## 【深堀り④】文字列連結時の `toString()` 自動呼び出し

[既存コードの仕様](#既存コードの仕様)の `EmployeeCard` クラスを確認しましょう。

```Java:EmployeeCard.java
public class EmployeeCard {
    private String employeeName;
    private int employeeCardNumber;

    public EmployeeCard(String employeeName, int employeeCardNumber) {
        System.out.println(employeeName + " さんの社員証を " + employeeCardNumber + " 番で発行します。");
        this.employeeName = employeeName;
        this.employeeCardNumber = employeeCardNumber;
    }

    public void pass() {
        System.out.println(this + " でゲートを通過します。");
    }

    @Override
    public String toString() {
        return "[社員証" + employeeCardNumber + "：" + employeeName + "]";
    }
}
```

```Java:Main.java
public class Main {
    public static void main(String[] args) {
        EmployeeCard employeeCard1 = new EmployeeCard("田中 太郎", 1001);
        EmployeeCard employeeCard2 = new EmployeeCard("山田 花子", 1002);

        employeeCard1.pass();
        employeeCard2.pass();
    }
}
```

**実行結果**

```
田中 太郎 さんの社員証を 1001 番で発行します。
山田 花子 さんの社員証を 1002 番で発行します。
[社員証1001：田中 太郎] でゲートを通過します。
[社員証1002：山田 花子] でゲートを通過します。
```

実行クラスで `pass` メソッドを呼び出した際、`this` の部分が `toString` メソッドをオーバーライドした内容が出力されています。

ここではなぜ `this` だけで文字列が出力されるのかを学びます。

### `+` 演算子と `StringBuilder`

Java では、オブジェクト（`this`）と文字列とを `+` 演算子を使って連結すると、コンパイラは内部で `StringBuilder` を使ったコードに変換します。

```java
// コンパイル後の実態（概略）
new StringBuilder().append(this).append(" でゲートを通過します。").toString()
```

ここでさらに、下記から分かるように `append(Object obj)` は内部で `String.valueOf(obj)` を呼びます。

```java
public final class StringBuilder
    extends AbstractStringBuilder
    implements Appendable, java.io.Serializable, Comparable<StringBuilder>, CharSequence
{
    public StringBuilder append(Object obj) {
        return append(String.valueOf(obj));
    }
}
```

> 引用元: OpenJDK [StringBuilder.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/StringBuilder.java)

上記の `append` メソッドに渡す `String.valueOf(Object obj)` は次のように実装されています。

```java
public final class String
    implements java.io.Serializable, Comparable<String>, CharSequence, Constable, ConstantDesc
{
    public static String valueOf(Object obj) {
        return (obj == null) ? "null" : obj.toString();
    }
}
```

> 引用元: OpenJDK [String.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/String.java)

以上から呼び出しチェーンをまとめると次のようになります。

```
this + " でゲートを通過します。"
  └─→ StringBuilder.append(this)
        └─→ String.valueOf(this)
              └─→ this.toString()   ← オーバーライドした toString() が呼ばれる
```

そのため、実行クラスで `pass` メソッドを呼び出すと `this` の部分は `toString` メソッドをオーバーライドした内容が出力されることになります。

### `toString()` をオーバーライドしない場合

`toString()` をオーバーライドしないと、`Object` クラスのデフォルト実装が使われます。

```java
// Object.toString() のデフォルト実装（概略）
return getClass().getName() + "@" + Integer.toHexString(hashCode());
```

例えば `EmployeeCard` クラスの `toString` メソッドをオーバーライドしないで、`pass` メソッドを呼び出すと次のように「クラス名とメモリアドレス由来のハッシュコード」が出力されることになります。

```
EmployeeCard@1b6d3586 でゲートを通過します。
```

<a id="深堀り5"></a>

## 【深堀り⑤】GoF デザインパターンとの位置づけ

今回使った Factory Method パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「生成パターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
