# Observer（オブザーバー）パターン ― 状態の変化を登録済みのオブジェクトへ一斉に通知する

次のような経験をしたことはありませんか？

> ある処理の完了後に実行する後続処理を、その処理を担うクラスへ直接書き足していたら、後続処理を 1 つ増やすたびに、そのクラスを修正する羽目になった。おまけに、後続処理を追加するつもりで手を入れただけなのに、既存の処理まで誤って壊してしまうこともあった。

この記事では、人事システムの異動登録に連携先を追加するシナリオを通して、Observer パターンがこの問題をどのように解決するかを紹介します。

## 目次

- [【具体例】](#具体例)
    - [シナリオ](#シナリオ)
    - [既存コードの仕様](#既存コードの仕様)
- [好ましくない実装](#好ましくない実装)
- [正しい実装](#正しい実装)
- [まとめ](#まとめ)
- [【深堀り①】通知で渡す情報の決め方（push 型と pull 型）](#深堀り1)
- [【深堀り②】通知の途中で例外が発生した場合](#深堀り2)
- [【深堀り③】Java 標準ライブラリにおける Observer パターンの例](#深堀り3)
- [【深堀り④】OCP（オープン・クローズドの原則）](#深堀り4)
- [【深堀り⑤】GoF デザインパターンとの位置づけ](#深堀り5)

---

## 【具体例】

### シナリオ

> あなたは社内の人事システムの開発チームに所属しています。<br>
> 現状の人事システムでは、社員の異動を登録すると、その社員の社内システムのアクセス権限と、部署ごとのメーリングリストの登録先が、異動後の部署に合わせて自動で更新される仕組みになっています。一方、勤怠システムと経費精算システムは、昨年実施した外部のクラウドサービスへの切り替えの際に、人事システムとの連携が後回しになってしまいました。そのため、異動者が出るたびに、総務部が各システムの承認者を手作業で更新している状態です。<br>
> このたび、後回しになっていた人事システムとの連携に着手することになりました。<br>
> あなたは、異動登録の際に、勤怠システムと経費精算システムの承認者も自動で更新される仕組みを追加することになりました。

※実際の異動登録では、アクセス権限・メーリングリストを管理する社内システムや、勤怠システム、経費精算システムとの連携を行う実装が必要ですが、本記事では Observer パターンの解説に集中するため、コンソールへの文字列出力のみとします。

### 既存コードの仕様

※実務では、次の `Employee` のようなエンティティクラスは `entity` パッケージなど専用のディレクトリに切り出すのが一般的です。しかし、本記事ではパッケージ構成を主題としないため `example` パッケージ直下にまとめています。

- `Employee`（既存クラス）

社員 1 人を表すクラスです。<br>
社員の名前と所属部署を保持します。

| フィールド   | 型       | 説明     |
| ------------ | -------- | -------- |
| `name`       | `String` | 社員名   |
| `department` | `String` | 所属部署 |

| メソッド           | 引数                | 戻り値の型 | 説明               |
| ------------------ | ------------------- | ---------- | ------------------ |
| `getName`          | なし                | `String`   | 社員名を取得する   |
| `getDepartment`    | なし                | `String`   | 所属部署を取得する |
| `changeDepartment` | `String department` | `void`     | 所属部署を変更する |

**`Employee.java`**

```java
package example;

public class Employee {
    private String name;
    private String department;

    public Employee(String name, String department) {
        this.name = name;
        this.department = department;
    }

    public String getName() {
        return name;
    }

    public String getDepartment() {
        return department;
    }

    public void changeDepartment(String department) {
        this.department = department;
    }
}
```

<br>

- `AccountPermissionService`（既存クラス）

社内システムのアクセス権限を更新するクラスです。

| メソッド           | 引数                | 戻り値の型 | 説明                                                                     |
| ------------------ | ------------------- | ---------- | ------------------------------------------------------------------------ |
| `updatePermission` | `Employee employee` | `void`     | 社員のアクセス権限を現在の所属部署用に更新し、結果をコンソールに出力する |

**`AccountPermissionService.java`**

```java
package example;

public class AccountPermissionService {
    public void updatePermission(Employee employee) {
        System.out.println("[アクセス権限] " + employee.getName() + "さんの権限を" + employee.getDepartment() + "用に更新しました");
    }
}
```

<br>

- `MailingListService`（既存クラス）

部署ごとのメーリングリストの登録先を更新するクラスです。

| メソッド     | 引数                                        | 戻り値の型 | 説明                                                                                       |
| ------------ | ------------------------------------------- | ---------- | ------------------------------------------------------------------------------------------ |
| `moveMember` | `Employee employee`, `String oldDepartment` | `void`     | 社員を異動前の部署から現在の所属部署のメーリングリストへ移動し、結果をコンソールに出力する |

**`MailingListService.java`**

```java
package example;

public class MailingListService {
    public void moveMember(Employee employee, String oldDepartment) {
        System.out.println("[メーリングリスト] " + employee.getName() + "さんを" + oldDepartment + "から" + employee.getDepartment() + "のメーリングリストへ移動しました");
    }
}
```

<br>

- `TransferService`（既存クラス）

異動を登録するクラスです。<br>
社員の所属部署を変更したうえで、アクセス権限とメーリングリストの更新を呼び出します。

| フィールド                 | 型                         | 説明                                     |
| -------------------------- | -------------------------- | ---------------------------------------- |
| `accountPermissionService` | `AccountPermissionService` | アクセス権限を更新するクラス             |
| `mailingListService`       | `MailingListService`       | メーリングリストの登録先を更新するクラス |

| メソッド           | 引数                                        | 戻り値の型 | 説明                                                                                 |
| ------------------ | ------------------------------------------- | ---------- | ------------------------------------------------------------------------------------ |
| `registerTransfer` | `Employee employee`, `String newDepartment` | `void`     | 社員の所属部署を異動後の部署に変更し、アクセス権限とメーリングリストの更新を呼び出す |

**`TransferService.java`**

```java
package example;

public class TransferService {
    private AccountPermissionService accountPermissionService = new AccountPermissionService();
    private MailingListService mailingListService = new MailingListService();

    public void registerTransfer(Employee employee, String newDepartment) {
        String oldDepartment = employee.getDepartment();
        employee.changeDepartment(newDepartment);
        System.out.println("[異動登録] " + employee.getName() + "さんを" + oldDepartment + "から" + newDepartment + "へ異動しました");

        accountPermissionService.updatePermission(employee);
        mailingListService.moveMember(employee, oldDepartment);
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
        TransferService transferService = new TransferService();

        Employee yamada = new Employee("山田太郎", "開発部");
        Employee sato = new Employee("佐藤花子", "総務部");

        transferService.registerTransfer(yamada, "営業部");

        System.out.println();

        transferService.registerTransfer(sato, "人事部");
    }
}
```

**実行結果**

```
[異動登録] 山田太郎さんを開発部から営業部へ異動しました
[アクセス権限] 山田太郎さんの権限を営業部用に更新しました
[メーリングリスト] 山田太郎さんを開発部から営業部のメーリングリストへ移動しました

[異動登録] 佐藤花子さんを総務部から人事部へ異動しました
[アクセス権限] 佐藤花子さんの権限を人事部用に更新しました
[メーリングリスト] 佐藤花子さんを総務部から人事部のメーリングリストへ移動しました
```

※ここで一旦読むのを止めて、ご自身でコーディングを行なってみてください。その後で、続きを読んでください。

## 好ましくない実装

では、シナリオに従い追加実装をしていきましょう。

まず思いつくのは、勤怠システムと経費精算システムの承認者を更新するクラスをそれぞれ作成し、既存の連携先と同じように異動を登録するクラスの `registerTransfer` メソッドから直接呼び出す、という実装ではないでしょうか？

**`AttendanceApprovalService.java`**

```java
package example;

public class AttendanceApprovalService {
    public void changeApprover(Employee employee) {
        System.out.println("[勤怠承認] " + employee.getName() + "さんの承認者を" + employee.getDepartment() + "の部長に変更しました");
    }
}
```

**`ExpenseApprovalService.java`**

```java
package example;

public class ExpenseApprovalService {
    public void assignApprover(Employee employee) {
        System.out.println("[経費承認] " + employee.getName() + "さんの経費精算の承認者を" + employee.getDepartment() + "の部長に変更しました");
    }
}
```

**`TransferService.java`**

```java
package example;

public class TransferService {
    private AccountPermissionService accountPermissionService = new AccountPermissionService();
    private MailingListService mailingListService = new MailingListService();
    /* ここを追加（ここから） */
    private AttendanceApprovalService attendanceApprovalService = new AttendanceApprovalService();
    private ExpenseApprovalService expenseApprovalService = new ExpenseApprovalService();
    /* ここを追加（ここまで） */

    public void registerTransfer(Employee employee, String newDepartment) {
        String oldDepartment = employee.getDepartment();
        employee.changeDepartment(newDepartment);
        System.out.println("[異動登録] " + employee.getName() + "さんを" + oldDepartment + "から" + newDepartment + "へ異動しました");

        accountPermissionService.updatePermission(employee);
        mailingListService.moveMember(employee, oldDepartment);
        /* ここを追加（ここから） */
        attendanceApprovalService.changeApprover(employee);
        expenseApprovalService.assignApprover(employee);
        /* ここを追加（ここまで） */
    }
}
```

`Main` クラスに変更はありません。

**実行結果**

```
[異動登録] 山田太郎さんを開発部から営業部へ異動しました
[アクセス権限] 山田太郎さんの権限を営業部用に更新しました
[メーリングリスト] 山田太郎さんを開発部から営業部のメーリングリストへ移動しました
[勤怠承認] 山田太郎さんの承認者を営業部の部長に変更しました
[経費承認] 山田太郎さんの経費精算の承認者を営業部の部長に変更しました

[異動登録] 佐藤花子さんを総務部から人事部へ異動しました
[アクセス権限] 佐藤花子さんの権限を人事部用に更新しました
[メーリングリスト] 佐藤花子さんを総務部から人事部のメーリングリストへ移動しました
[勤怠承認] 佐藤花子さんの承認者を人事部の部長に変更しました
[経費承認] 佐藤花子さんの経費精算の承認者を人事部の部長に変更しました
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 新しい連携先（例えば「社員証の所属表示」）が増えるたびに `TransferService` クラスにフィールドを追加し、`registerTransfer` メソッドの中身を修正しなければならないため、追加した連携先だけでなく、すでにテストが完了している所属部署の変更や既存の連携先の呼び出しまで、再テストが必要になってしまう。
- 新しい連携先が増えるたびに `TransferService` クラスの `registerTransfer` メソッドを直接修正する必要があるため、所属部署の変更や既存の連携先の呼び出しを誤って壊してしまうおそれがある。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

この問題を解決するのが **Observer パターン**です。<br>
異動登録を行う側が連携先を 1 つずつ直接呼び出すのをやめ、「異動が発生したことを知りたいオブジェクト」を共通のインターフェースで登録しておき、異動が発生したら登録済みのオブジェクトへ一斉に通知する形にすることで、連携先が増えても異動登録のクラスを修正する必要がなくなります。

まず、異動の通知を受け取る側に共通する振る舞いを定義するインターフェースから見ていきましょう。

**`TransferObserver.java`**

```java
package example;

public interface TransferObserver {
    void onTransferred(Employee employee, String oldDepartment);
}
```

`TransferObserver` は新たに追加したインターフェースで、異動が発生したときに呼び出される `onTransferred` メソッドを 1 つだけ持ちます。引数には異動した社員と異動前の部署を受け取ります。異動後の部署は `employee.getDepartment()` で取得できるため、引数には含めていません。

次に、インターフェース `TransferObserver` を実装したクラスを見ていきましょう。既存の `AccountPermissionService`・`MailingListService` クラスと、好ましくない実装で作成した `AttendanceApprovalService`・`ExpenseApprovalService` クラスを、いずれもインターフェース `TransferObserver` を実装する形に変更します。

**`AccountPermissionService.java`**

```java
package example;

public class AccountPermissionService implements TransferObserver {
    @Override
    public void onTransferred(Employee employee, String oldDepartment) {
        System.out.println("[アクセス権限] " + employee.getName() + "さんの権限を" + employee.getDepartment() + "用に更新しました");
    }
}
```

**`MailingListService.java`**

```java
package example;

public class MailingListService implements TransferObserver {
    @Override
    public void onTransferred(Employee employee, String oldDepartment) {
        System.out.println("[メーリングリスト] " + employee.getName() + "さんを" + oldDepartment + "から" + employee.getDepartment() + "のメーリングリストへ移動しました");
    }
}
```

**`AttendanceApprovalService.java`**

```java
package example;

public class AttendanceApprovalService implements TransferObserver {
    @Override
    public void onTransferred(Employee employee, String oldDepartment) {
        System.out.println("[勤怠承認] " + employee.getName() + "さんの承認者を" + employee.getDepartment() + "の部長に変更しました");
    }
}
```

**`ExpenseApprovalService.java`**

```java
package example;

public class ExpenseApprovalService implements TransferObserver {
    @Override
    public void onTransferred(Employee employee, String oldDepartment) {
        System.out.println("[経費承認] " + employee.getName() + "さんの経費精算の承認者を" + employee.getDepartment() + "の部長に変更しました");
    }
}
```

4 つのクラスを振り返ると、クラスごとにバラバラだった `updatePermission`・`moveMember`・`changeApprover`・`assignApprover` メソッドが、すべてインターフェース `TransferObserver` の `onTransferred` メソッドのオーバーライドに置き換わっています。一方、コンソールへの出力内容は変わっていません。

次に、異動を通知する側のクラスを見ていきましょう。

**`TransferService.java`**

```java
package example;

public class TransferService {
    private List<TransferObserver> observers = new ArrayList<>();

    public void addObserver(TransferObserver observer) {
        observers.add(observer);
    }

    public void registerTransfer(Employee employee, String newDepartment) {
        String oldDepartment = employee.getDepartment();
        employee.changeDepartment(newDepartment);
        System.out.println("[異動登録] " + employee.getName() + "さんを" + oldDepartment + "から" + newDepartment + "へ異動しました");

        notifyObservers(employee, oldDepartment);
    }

    private void notifyObservers(Employee employee, String oldDepartment) {
        for (TransferObserver observer: observers) {
            observer.onTransferred(employee, oldDepartment);
        }
    }
}
```

`TransferService` クラスを振り返ると、4 つの連携先クラスのフィールドがなくなり、代わりにインターフェース `TransferObserver` のリストである `observers` フィールドを保持しています。<br>
`addObserver` メソッドで通知先を登録し、`registerTransfer` メソッドでは所属部署を変更した後に `notifyObservers` メソッドを呼び出して、登録済みのすべての通知先に異動を通知しています。<br>
`TransferService` クラスは、登録された通知先が具体的にどのクラスなのかを知らず、「`onTransferred` メソッドを持っている」ことだけを知っている状態です。

最後に、実行クラスを見てみましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        TransferService transferService = new TransferService();
        transferService.addObserver(new AccountPermissionService());
        transferService.addObserver(new MailingListService());
        transferService.addObserver(new AttendanceApprovalService());
        transferService.addObserver(new ExpenseApprovalService());

        Employee yamada = new Employee("山田太郎", "開発部");
        Employee sato = new Employee("佐藤花子", "総務部");

        transferService.registerTransfer(yamada, "営業部");

        System.out.println();

        transferService.registerTransfer(sato, "人事部");
    }
}
```

**実行結果**

```
[異動登録] 山田太郎さんを開発部から営業部へ異動しました
[アクセス権限] 山田太郎さんの権限を営業部用に更新しました
[メーリングリスト] 山田太郎さんを開発部から営業部のメーリングリストへ移動しました
[勤怠承認] 山田太郎さんの承認者を営業部の部長に変更しました
[経費承認] 山田太郎さんの経費精算の承認者を営業部の部長に変更しました

[異動登録] 佐藤花子さんを総務部から人事部へ異動しました
[アクセス権限] 佐藤花子さんの権限を人事部用に更新しました
[メーリングリスト] 佐藤花子さんを総務部から人事部のメーリングリストへ移動しました
[勤怠承認] 佐藤花子さんの承認者を人事部の部長に変更しました
[経費承認] 佐藤花子さんの経費精算の承認者を人事部の部長に変更しました
```

`Main` クラスを振り返ると、`TransferService` クラスのインスタンスを生成した直後に、`addObserver` メソッドで 4 つの通知先を登録しています。どの連携先に異動を通知するかは、`TransferService` クラスの中ではなく、この登録処理で決まるようになっています。

実行結果を振り返ると、好ましくない実装の実行結果と完全に一致しています。つまり、外から見た振る舞いを変えずに、内部の構造だけを変更できたことになります。

以上のような実装を行うと、以下のメリットがあります。

- 新しい連携先（例えば「社員証の所属表示」）を追加する場合も、インターフェース `TransferObserver` を実装したクラスを新たに作成し、`Main` クラスで `addObserver` メソッドを呼び出して登録するだけでよく、`TransferService` クラスには一切手を加える必要がないため、テスト済みの所属部署の変更や既存の連携先の呼び出しを再テストする必要もない。
    - これは、`TransferService` クラスがインターフェース `TransferObserver` の型を通じて通知先を扱うため、連携先の具象クラスを一切知らずに済むためである。
- 連携先の追加で `registerTransfer` メソッドを修正することがなくなるため、所属部署の変更や既存の連携先の呼び出しを誤って壊す心配がない。

## まとめ

正しい実装を振り返ると、`TransferService` クラスは連携先の具象クラスを一切知らず、異動が発生したことを登録済みの `TransferObserver` に一斉に通知するだけになっています。<br>
このように Observer パターンは、あるオブジェクトの状態が変化したときに、その変化を知りたい複数のオブジェクトへ、互いの具体的なクラスを知らないまま通知できるようにする設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】通知で渡す情報の決め方（push 型と pull 型）

正しい実装を振り返ると、`onTransferred` メソッドは「異動した社員」と「異動前の部署」を引数として通知先に渡しています。このように、通知する側が必要そうな情報をあらかじめ引数に詰めて渡す方式は「**push 型**」と呼ばれます。

push 型は通知先が受け取った情報をそのまま使えるため分かりやすい一方、通知先ごとに必要な情報が異なると無駄が生じます。実際、`AccountPermissionService`・`AttendanceApprovalService`・`ExpenseApprovalService` クラスは、引数の `oldDepartment` を一切使っていません。さらに、今後「異動日」や「役職」を必要とする連携先が現れた場合、`onTransferred` メソッドの引数を増やすことになり、その変更は `TransferObserver` を実装したすべてのクラスに及びます。

これに対し、通知の際には「変化があった」ことと通知元のオブジェクトだけを渡し、必要な情報は通知先が通知元から取りに行く方式は「**pull 型**」と呼ばれます。pull 型は通知先が必要な情報だけを取得できる一方、通知先が通知元の具象クラスとその取得用メソッドを知る必要があるため、通知元と通知先の結びつきが強くなります。

実務では、両者の中間として、通知する情報を 1 つのクラスにまとめた「イベントオブジェクト」を渡す方式がよく使われます。

**`TransferEvent.java`**

```java
package example;

public class TransferEvent {
    private final Employee employee;
    private final String oldDepartment;
    private final String newDepartment;

    public TransferEvent(Employee employee, String oldDepartment, String newDepartment) {
        this.employee = employee;
        this.oldDepartment = oldDepartment;
        this.newDepartment = newDepartment;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getOldDepartment() {
        return oldDepartment;
    }

    public String getNewDepartment() {
        return newDepartment;
    }
}
```

`onTransferred` メソッドの引数を `TransferEvent` クラス 1 つにしておけば、後から「異動日」などの情報が必要になっても、`TransferEvent` クラスにフィールドを追加するだけで済み、`onTransferred` メソッドのシグネチャ（メソッド名と引数の組み合わせ）は変わりません。そのため、既存の通知先クラスを修正する必要もありません。Java 標準ライブラリの `PropertyChangeEvent` クラスも、この考え方に基づいたイベントオブジェクトです（→ [Java 標準ライブラリにおける Observer パターンの例](#深堀り3)）。

<a id="深堀り2"></a>

## 【深堀り②】通知の途中で例外が発生した場合

正しい実装の `notifyObservers` メソッドは、登録済みの通知先を先頭から順番に呼び出しています。ここで、例えば勤怠システムに接続できず、`AttendanceApprovalService` クラスの `onTransferred` メソッドで例外が発生した場合を考えてみましょう。

例外が発生した時点で `for` 文は中断されるため、その後ろに登録されている `ExpenseApprovalService` クラスには異動が通知されません。つまり、無関係な連携先の障害によって、経費精算システムの承認者だけが異動前のまま残ってしまいます。

これを防ぐには、次のように通知先ごとに例外を捕捉し、1 つの通知先が失敗しても残りの通知先への通知を続けるようにします。

**`TransferService.java`（一部抜粋）**

```java
package example;

public class TransferService {
    private void notifyObservers(Employee employee, String oldDepartment) {
        for (TransferObserver observer: observers) {
            try {
                observer.onTransferred(employee, oldDepartment);
            } catch (RuntimeException e) {
                System.out.println("[通知失敗] " + observer.getClass().getSimpleName() + "：" + e.getMessage());
            }
        }
    }
}
```

また、本記事の実装では登録した順番に通知していますが、通知先同士が「アクセス権限の更新が終わってからメーリングリストを更新する」のような順序に依存する作りにはしないことが重要です。Observer パターンは通知先同士が互いを知らないことを前提としているため、通知の順番に意味を持たせると、登録順を入れ替えただけで不具合が生じる、壊れやすい設計になってしまいます。

実務では、失敗した通知を後から再送する仕組みや、通知そのものをメッセージキューを通じて非同期に行う仕組みと組み合わせることもあります。詳しくは「Pub/Sub」や「メッセージキュー」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】Java 標準ライブラリにおける Observer パターンの例

Java には、JDK 1.0 の時代から `java.util.Observer` インターフェースと `java.util.Observable` クラスという、名前のとおり Observer パターンを実装するための仕組みが用意されていました。しかし、この 2 つは Java 9 で非推奨（`@Deprecated`）となっています。

`Observable` クラスの Javadoc には、非推奨とされた理由として「サポートしているイベントモデルが限定的であること」「通知の順番が規定されていないこと」「状態の変化と通知が 1 対 1 に対応していないこと」が挙げられており、代わりに `java.beans` パッケージの利用が案内されています（→ OpenJDK [Observable.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/Observable.java)）。また、`Observable` はインターフェースではなくクラスであるため、通知する側のクラスは `Observable` クラスを継承しなければならず、他のクラスを継承できなくなるという使いづらさもありました。

そこで、案内されている `java.beans` パッケージの `PropertyChangeListener` インターフェースと `PropertyChangeSupport` クラスを見ていきましょう。

**`PropertyChangeListener.java`（一部抜粋）**

```java
package java.beans;

public interface PropertyChangeListener extends java.util.EventListener {

    void propertyChange(PropertyChangeEvent evt);

}
```

> 引用元: OpenJDK [PropertyChangeListener.java](https://github.com/openjdk/jdk/blob/master/src/java.desktop/share/classes/java/beans/PropertyChangeListener.java)

**`PropertyChangeSupport.java`（一部抜粋）**

```java
package java.beans;

public class PropertyChangeSupport implements Serializable {
    private PropertyChangeListenerMap map = new PropertyChangeListenerMap();

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        if (listener == null) {
            return;
        }
        if (listener instanceof PropertyChangeListenerProxy) {
            PropertyChangeListenerProxy proxy =
                   (PropertyChangeListenerProxy)listener;
            // Call two argument add method.
            addPropertyChangeListener(proxy.getPropertyName(),
                                      proxy.getListener());
        } else {
            this.map.add(null, listener);
        }
    }

    public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        if (oldValue == null || newValue == null || !oldValue.equals(newValue)) {
            firePropertyChange(new PropertyChangeEvent(this.source, propertyName, oldValue, newValue));
        }
    }

    public void firePropertyChange(PropertyChangeEvent event) {
        Object oldValue = event.getOldValue();
        Object newValue = event.getNewValue();
        if (oldValue == null || newValue == null || !oldValue.equals(newValue)) {
            String name = event.getPropertyName();

            PropertyChangeListener[] common = this.map.get(null);
            PropertyChangeListener[] named = (name != null)
                        ? this.map.get(name)
                        : null;

            fire(common, event);
            fire(named, event);
        }
    }

    private static void fire(PropertyChangeListener[] listeners, PropertyChangeEvent event) {
        if (listeners != null) {
            for (PropertyChangeListener listener : listeners) {
                listener.propertyChange(event);
            }
        }
    }
}
```

> 引用元: OpenJDK [PropertyChangeSupport.java](https://github.com/openjdk/jdk/blob/master/src/java.desktop/share/classes/java/beans/PropertyChangeSupport.java)

`PropertyChangeListener` インターフェースは、値の変化を通知されたときに呼び出される `propertyChange` メソッドを 1 つだけ持ちます。これは、本記事の `TransferObserver` インターフェースと `onTransferred` メソッドにあたります。

`PropertyChangeSupport` クラスの `addPropertyChangeListener` メソッドは、通知先のリスナーを `map` フィールド（プロパティ名ごとにリスナーを管理する内部クラス）に登録します。`PropertyChangeListenerProxy` の分岐は特定のプロパティだけを監視するリスナー向けの処理で、それ以外のリスナーは `null` をキーとして「すべてのプロパティの変化を受け取るリスナー」として登録されます。これは、本記事の `addObserver` メソッドにあたります。

`firePropertyChange` メソッドは、変化したプロパティ名と変化前後の値から `PropertyChangeEvent`（変化の内容をまとめたイベントオブジェクト）を生成し、`fire` メソッドで登録済みのリスナーの `propertyChange` メソッドを順番に呼び出しています。これは、本記事の `notifyObservers` メソッドにあたります。値の変化 1 回ごとに、その変化の内容を持つ `PropertyChangeEvent` が 1 つ生成されて通知されるため、「状態の変化と通知が 1 対 1 に対応していない」という `Observable` クラスの弱点が解消されています。また、変化前後の値が等しい場合は、実際には変化していないものとして通知しない判定も組み込まれています。

利用する側は、自分のクラスに `PropertyChangeSupport` クラスのインスタンスを持たせ、値を変更するたびに `firePropertyChange` メソッドを呼び出すだけで、登録済みのリスナーへの通知を任せられます。継承ではなくフィールドとして持たせる形のため、`Observable` クラスのように継承の枠を使ってしまうこともありません。

<a id="深堀り4"></a>

## 【深堀り④】OCP（オープン・クローズドの原則）

正しい実装を振り返ると、異動の連携先が増える（例えば「社員証の所属表示」を追加する）場合、インターフェース `TransferObserver` を実装したクラスを新たに 1 つ追加し、`Main` クラス側で `addObserver` メソッドを呼び出して登録するだけで、既存の `TransferService` クラスや、他の通知先クラスには一切手を加える必要がありません。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。Observer パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

<a id="深堀り5"></a>

## 【深堀り⑤】GoF デザインパターンとの位置づけ

今回使った Observer パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
