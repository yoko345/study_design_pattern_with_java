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
- [【深堀り①】通知で渡す情報の決め方](#深堀り1)
    - [push 型](#push-型)
    - [pull 型](#pull-型)
    - [イベントオブジェクト](#イベントオブジェクト)
    - [3 つの方式の使い分け](#3-つの方式の使い分け)
- [【深堀り②】通知の途中で例外が発生した場合](#深堀り2)
- [【深堀り③】Java 標準ライブラリにおける Observer パターンの例](#深堀り3)
- [【深堀り④】OCP（オープン・クローズドの原則）](#深堀り4)
- [【深堀り⑤】GoF デザインパターンとの位置づけ](#深堀り5)

---

## 【具体例】

### シナリオ

> あなたは社内の人事システムの開発チームに所属しています。<br>
> 現状の人事システムでは、社員の異動を登録すると、その社員の社内システムのアクセス権限と、部署ごとのメーリングリストの登録先が、異動後の部署に合わせて自動で更新される仕組みになっています。一方、勤怠システムは、昨年実施した外部のクラウドサービスへの切り替えの際に、人事システムとの連携が後回しになってしまいました。そのため、異動者が出るたびに、総務部が勤怠システムの承認者を手作業で更新している状態です。<br>
> このたび、後回しになっていた人事システムとの連携に着手することが決まりました。<br>
> あなたは、異動登録の際に、勤怠システムの承認者も自動で更新される仕組みを追加することになりました。

※実際の異動登録では、アクセス権限・メーリングリストを管理する社内システムや、勤怠システムとの連携を行う実装が必要ですが、本記事では Observer パターンの解説に集中するため、コンソールへの文字列出力のみとします。

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
社員の所属部署を変更したうえで、アクセス権限とメーリングリストの更新処理を呼び出します。

| フィールド                 | 型                         | 説明                                     |
| -------------------------- | -------------------------- | ---------------------------------------- |
| `accountPermissionService` | `AccountPermissionService` | アクセス権限を更新するクラス             |
| `mailingListService`       | `MailingListService`       | メーリングリストの登録先を更新するクラス |

| メソッド           | 引数                                        | 戻り値の型 | 説明                                                                                                               |
| ------------------ | ------------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------ |
| `registerTransfer` | `Employee employee`, `String newDepartment` | `void`     | 社員の所属部署を異動後の部署に変更して結果をコンソールに出力し、アクセス権限とメーリングリストの更新処理を呼び出す |

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

まず思いつくのは、勤怠システムの承認者を更新するクラスを作成し、異動を登録するクラスの `registerTransfer` メソッドから、既存の連携先と同じように直接呼び出す、という実装ではないでしょうか？

**`AttendanceApprovalService.java`**

```java
package example;

public class AttendanceApprovalService {
    public void changeApprover(Employee employee) {
        System.out.println("[勤怠承認] " + employee.getName() + "さんの承認者を" + employee.getDepartment() + "の部長に変更しました");
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
    /* ここを追加（ここまで） */

    public void registerTransfer(Employee employee, String newDepartment) {
        String oldDepartment = employee.getDepartment();
        employee.changeDepartment(newDepartment);
        System.out.println("[異動登録] " + employee.getName() + "さんを" + oldDepartment + "から" + newDepartment + "へ異動しました");

        accountPermissionService.updatePermission(employee);
        mailingListService.moveMember(employee, oldDepartment);
        /* ここを追加（ここから） */
        attendanceApprovalService.changeApprover(employee);
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

[異動登録] 佐藤花子さんを総務部から人事部へ異動しました
[アクセス権限] 佐藤花子さんの権限を人事部用に更新しました
[メーリングリスト] 佐藤花子さんを総務部から人事部のメーリングリストへ移動しました
[勤怠承認] 佐藤花子さんの承認者を人事部の部長に変更しました
```

コンパイルエラーがなく結果が出力されていることから、一見すると実装・動作確認ともに問題ないように見えます。

しかし、この実装には以下の問題点があります。

- 新しい連携先（例えば「経費精算システム」）が増えるたびに、`TransferService` クラスにフィールドを追加し、`registerTransfer` メソッドの中身を修正しなければならない。
    - その結果、追加した連携先だけでなく、すでにテストが完了している所属部署の変更や既存の連携先の呼び出しまで、再テストが必要になってしまう。
- 連携先を追加するために `registerTransfer` メソッドに手を入れた際、所属部署の変更処理を誤って書き換えてしまったり、既存の連携先の呼び出し方を間違えてしまったりするおそれがある。

## 正しい実装

では、好ましくない実装で挙げた問題点を解決するにはどうすればよいのでしょうか？

これらの問題を解決するのが **Observer パターン**です。<br>
異動登録を行う側が連携先を 1 つずつ直接呼び出すのをやめ、「異動が発生したことを知りたいオブジェクト」に共通のインターフェースを実装させたうえで登録しておき、異動が発生したら登録済みのオブジェクトへ一斉に通知する形にします。

まず、異動の通知を受け取る側に共通する振る舞いを定義するインターフェースから見ていきましょう。

**`TransferObserver.java`**

```java
package example;

public interface TransferObserver {
    void onTransferred(Employee employee, String oldDepartment);
}
```

`TransferObserver` は新たに追加したインターフェースで、異動が発生したときに呼び出される `onTransferred` メソッドを 1 つだけ持ちます。

次に、インターフェース `TransferObserver` を実装したクラスを見ていきましょう。

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

`AccountPermissionService`・`MailingListService` クラスを振り返ると、既存の仕様からインターフェース `TransferObserver` を実装する修正が加わっています。これにより、クラスごとに異なっていた `updatePermission`・`moveMember` メソッドは、どちらも `onTransferred` メソッドのオーバーライドに置き換わっています。一方、コンソールへの出力内容は既存の仕様から変更されていません。

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

`AttendanceApprovalService` は新たに追加したクラスで、インターフェース `TransferObserver` を実装し、`onTransferred` メソッドで勤怠システムの承認者を変更しています。

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

`TransferService` クラスを振り返ると、既存の仕様から次の点が変わっています。

- 連携先クラスのフィールドがなくなり、代わりにインターフェース `TransferObserver` のリストである `observers` フィールドを保持している。
- 異動を知らせたい連携先を登録する `addObserver` メソッドが新たに追加されている。
- 登録済みのすべての連携先に異動を通知する `notifyObservers` メソッドが新たに追加されている。
- `registerTransfer` メソッドでは、連携先のメソッドを 1 つずつ呼び出していた部分が、`notifyObservers` メソッドの呼び出しに置き換わっている。

最後に、実行クラスを見てみましょう。

**`Main.java`**

```java
package example;

public class Main {
    public static void main(String[] args) {
        TransferService transferService = new TransferService();
        /* ここを追加（ここから） */
        transferService.addObserver(new AccountPermissionService());
        transferService.addObserver(new MailingListService());
        transferService.addObserver(new AttendanceApprovalService());
        /* ここを追加（ここまで） */

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

[異動登録] 佐藤花子さんを総務部から人事部へ異動しました
[アクセス権限] 佐藤花子さんの権限を人事部用に更新しました
[メーリングリスト] 佐藤花子さんを総務部から人事部のメーリングリストへ移動しました
[勤怠承認] 佐藤花子さんの承認者を人事部の部長に変更しました
```

`Main` クラスを振り返ると、`TransferService` クラスのインスタンスを生成し、`addObserver` メソッドで 3 つのクラス（`AccountPermissionService`・`MailingListService`・`AttendanceApprovalService`）のインスタンスを登録しています。

実行結果は、好ましくない実装とまったく同じになっています。

以上のような実装を行うと、以下のメリットがあります。

- 新しい連携先（例えば「経費精算システム」）を追加する場合、インターフェース `TransferObserver` を実装したクラスを新たに作成し、`Main` クラスで `addObserver` メソッドを呼び出して登録するだけでよく、`TransferService` クラスには一切手を加える必要がない。
    - これは、`Main` クラスでの登録によってどの連携先に異動を通知するかが決まるようになり、`TransferService` クラスは連携先の具体的なクラスを知らずに、`onTransferred` メソッドを呼び出すだけで済むためである。
    - その結果、テスト済みの所属部署の変更や既存の連携先の呼び出しを再テストする必要がなくなる。
- 連携先を追加しても `registerTransfer` メソッドに手が入ることはないため、所属部署の変更処理を誤って書き換えてしまったり、既存の連携先の呼び出し方を間違えてしまったりするおそれがなくなる。

## まとめ

正しい実装を振り返ると、`TransferService` クラスは連携先の具体的なクラスを一切知らず、異動が発生したことを登録済みの `TransferObserver` を実装したクラスに一斉に通知するだけでよくなっています。<br>
このように Observer パターンは、あるオブジェクトの状態が変化したときに、その変化を知りたい複数のオブジェクトへ、互いの具体的なクラスを知らないまま通知できるようにする設計パターンです。

本記事の内容はここまでとなります。

以降は「もう少し深く知りたい」という方向けの補足となります。今回学んだパターンに繋がる設計原則や、実務で役立つ背景知識について触れています。

---

<a id="深堀り1"></a>

## 【深堀り①】通知で渡す情報の決め方

### push 型

正しい実装を振り返ると、`TransferService` クラスは `onTransferred` メソッドの引数として、「異動した社員」と「異動前の部署」を各連携先のクラスに渡しています。<br>
このように、通知する側が、受け取る側で必要になりそうな情報をあらかじめ引数に詰めて渡す方式は「**push 型**」と呼ばれます。

push 型は、受け取る側が渡された情報をそのまま使えるため分かりやすい一方、連携先ごとに必要な情報が異なると無駄が生じます。実際、`AccountPermissionService`・`AttendanceApprovalService` クラスは、引数の `oldDepartment` を一切使っていません。<br>
また、「異動日」や「役職」を必要とする連携先が今後現れた場合、`onTransferred` メソッドの引数を増やすことになるため、`TransferObserver` を実装したすべてのクラスに手を加える必要が出てきます。

### pull 型

push 型のこれらの問題を解決するのが「**pull 型**」と呼ばれる方式です。pull 型では、通知する側は「変化があったこと」だけを知らせ、必要な情報は受け取る側が通知する側から取りに行きます。<br>
本記事では、受け取る側が情報を取りに行けるように、通知の際に通知する側のオブジェクト自身を渡します。

pull 型で実装すると、次のようになります。

**`TransferObserver.java`**

```java
package example;

public interface TransferObserver {
    void onTransferred(TransferService transferService);
}
```

**`TransferService.java`**

```java
package example;

public class TransferService {
    private List<TransferObserver> observers = new ArrayList<>();
    private Employee transferredEmployee;
    private String oldDepartment;

    public void addObserver(TransferObserver observer) {
        observers.add(observer);
    }

    public void registerTransfer(Employee employee, String newDepartment) {
        oldDepartment = employee.getDepartment();
        employee.changeDepartment(newDepartment);
        transferredEmployee = employee;
        System.out.println("[異動登録] " + employee.getName() + "さんを" + oldDepartment + "から" + newDepartment + "へ異動しました");

        notifyObservers();
    }

    public Employee getTransferredEmployee() {
        return transferredEmployee;
    }

    public String getOldDepartment() {
        return oldDepartment;
    }

    private void notifyObservers() {
        for (TransferObserver observer: observers) {
            observer.onTransferred(this);
        }
    }
}
```

**`AccountPermissionService.java`**

```java
package example;

public class AccountPermissionService implements TransferObserver {
    @Override
    public void onTransferred(TransferService transferService) {
        Employee employee = transferService.getTransferredEmployee();
        System.out.println("[アクセス権限] " + employee.getName() + "さんの権限を" + employee.getDepartment() + "用に更新しました");
    }
}
```

**`MailingListService.java`**

```java
package example;

public class MailingListService implements TransferObserver {
    @Override
    public void onTransferred(TransferService transferService) {
        Employee employee = transferService.getTransferredEmployee();
        String oldDepartment = transferService.getOldDepartment();
        System.out.println("[メーリングリスト] " + employee.getName() + "さんを" + oldDepartment + "から" + employee.getDepartment() + "のメーリングリストへ移動しました");
    }
}
```

※ `AttendanceApprovalService` クラスも `AccountPermissionService` クラスと同じように修正します。`Main` クラスと実行結果に変更はありません。

`onTransferred` メソッドの引数は `TransferService` クラスだけになり、各連携先は getter メソッド（`getTransferredEmployee`・`getOldDepartment`）を通じて自分が使う情報だけを取りに行っています。実際、`AccountPermissionService` クラスは異動前の部署を取得していません。<br>
そのため、必要な情報が増えても `TransferService` クラスにフィールドと getter メソッドを追加するだけで済み、`onTransferred` メソッドの引数を変える必要はありません。

ただし、pull 型では、受け取る側が通知する側のクラス（`TransferService`）とその getter メソッドを知る必要があるため、両者の結びつきが強くなるという欠点があります。<br>
例えば、`AccountPermissionService` クラスの動作だけを確認したい場合、push 型であれば `onTransferred` メソッドに社員と異動前の部署を直接渡すだけで済みます。一方、pull 型では `TransferService` クラスのインスタンスを用意し、`registerTransfer` メソッドで異動を登録しなければ、異動した社員を取得できません。そのため、`registerTransfer` メソッドに不具合があると、`AccountPermissionService` クラスの動作確認まで失敗してしまいます。

### イベントオブジェクト

pull 型の欠点を補うため、実務では push 型と pull 型の良いところを取り入れ、通知する情報を 1 つのオブジェクトにまとめた「**イベントオブジェクト**」を渡す方式がよく使われます。

イベントオブジェクトを使って実装すると、次のようになります。

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

**`TransferObserver.java`**

```java
package example;

public interface TransferObserver {
    void onTransferred(TransferEvent event);
}
```

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

        notifyObservers(new TransferEvent(employee, oldDepartment, newDepartment));
    }

    private void notifyObservers(TransferEvent event) {
        for (TransferObserver observer: observers) {
            observer.onTransferred(event);
        }
    }
}
```

**`AccountPermissionService.java`**

```java
package example;

public class AccountPermissionService implements TransferObserver {
    @Override
    public void onTransferred(TransferEvent event) {
        Employee employee = event.getEmployee();
        System.out.println("[アクセス権限] " + employee.getName() + "さんの権限を" + event.getNewDepartment() + "用に更新しました");
    }
}
```

**`MailingListService.java`**

```java
package example;

public class MailingListService implements TransferObserver {
    @Override
    public void onTransferred(TransferEvent event) {
        Employee employee = event.getEmployee();
        System.out.println("[メーリングリスト] " + employee.getName() + "さんを" + event.getOldDepartment() + "から" + event.getNewDepartment() + "のメーリングリストへ移動しました");
    }
}
```

※ `AttendanceApprovalService` クラスも `AccountPermissionService` クラスと同じように修正します。`Main` クラスと実行結果に変更はありません。

`onTransferred` メソッドの引数を `TransferEvent` クラス 1 つにしておけば、後から「異動日」などの情報が必要になっても、`TransferEvent` クラスにフィールドと getter メソッドを追加し、`TransferService` クラスでその値を渡すだけで済み、`onTransferred` メソッドのシグネチャ（メソッド名と引数の組み合わせ）は変わりません。そのため、既存の連携先のクラスを修正する必要もありません。<br>
また、各連携先のクラスは `TransferEvent` クラスから必要な情報だけを取り出せばよく、pull 型のように `TransferService` クラスを知る必要もありません。そのため、連携先のクラスの動作を確認する際も、`TransferEvent` クラスのインスタンスを生成して渡すだけで済みます。<br>
Java 標準ライブラリの `PropertyChangeEvent` クラスも、この考え方に基づいたイベントオブジェクトです（→ [【深堀り③】Java 標準ライブラリにおける Observer パターンの例](#深堀り3)）。

### 3 つの方式の使い分け

連携先が少なく、渡す情報が今後増える見込みも小さい場合は、本記事の正しい実装のように push 型で十分です。一方、連携先ごとに必要な情報が異なる場合や、今後渡す情報が増える見込みがある場合は、イベントオブジェクトを使うとよいでしょう。<br>
pull 型は、受け取る側と通知する側の結びつきが強くなっても問題ない場合に限って選ぶのが無難です。

<a id="深堀り2"></a>

## 【深堀り②】通知の途中で例外が発生した場合

正しい実装の `notifyObservers` メソッドは、`for` 文で登録済みの連携先を先頭から順番に呼び出し、それぞれの `onTransferred` メソッドを実行しています。本記事の実装ではどの連携先も正常に処理を終えていますが、実務では連携先のシステムに接続できないなどの理由で、`onTransferred` メソッドの途中で例外が発生することも考えられます。

では、通知の途中で例外が発生すると、どうなるのでしょうか？

具体例として、以下の状況を考えてみましょう。

- 経費精算システムの連携先が、勤怠システムの連携先（`AttendanceApprovalService` クラス）の後ろに追加で登録されている
- 何らかの原因で勤怠システムに接続できなくなり、`AttendanceApprovalService` クラスの `onTransferred` メソッドで例外が発生した

この場合、例外が発生した時点で `for` 文が中断されるため、後ろに登録されている経費精算システムの連携先には通知されません。つまり、勤怠システムの障害とは無関係なはずの経費精算システムまで、承認者が異動前のまま残ってしまいます。

このようなとき、すでに更新を終えた連携先も含めてすべてを異動前の状態に戻すという対処法も考えられます。しかし、異動は辞令によって決まる業務上の事実であり、勤怠システム 1 つの障害を理由に、異動そのものをなかったことにするのは不自然です。<br>
そこで、次のように連携先ごとに例外を捕捉し、どこか 1 つで失敗しても残りへの通知を最後まで続けるようにします。

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

これにより、勤怠システムの連携先で例外が発生しても、失敗した連携先を `[通知失敗]` として出力したうえで `for` 文が続行されるため、後ろに登録されている経費精算システムの連携先にも異動が通知されるようになります。

ただし、この実装では前の連携先が失敗しても後ろの連携先が呼び出されるため、通知の順番に意味を持たせると不具合につながります。

例えば、次のような状況を考えてみましょう。

- メーリングリストの連携先（`MailingListService` クラス）は、アクセス権限の連携先（`AccountPermissionService` クラス）の後ろに登録されている
- `MailingListService` クラスは、アクセス権限の更新が済んでいることを前提に、異動後の部署の権限を持っているかを確認せずにメーリングリストへ登録する作りになっていると仮定する
- 何らかの原因で、`AccountPermissionService` クラスの `onTransferred` メソッドで例外が発生した

この場合、アクセス権限の更新に失敗しても、メーリングリストの連携先は権限を確認しないまま登録してしまいます。その結果、異動後の部署のメーリングリストには登録されたのに、そこで共有される資料を開く権限がない、という食い違いが生じてしまいます。<br>
Observer パターンは、異動の知らせを受け取る側のクラスがお互いを知らないことを前提としているため、通知の順番に依存しない作りにすることが重要です。

ここまでの実装で解決できたのは、1 つの連携先の失敗によって残りの連携先への通知が止まってしまう問題だけです。例外が発生した勤怠システムの連携先そのものは、承認者が異動前のまま残ってしまう点に注意が必要です。<br>
こうした失敗への対処法として、実務では、失敗した通知を後から再送する仕組みや、通知そのものをメッセージキューを通じて非同期に行う仕組みと組み合わせることがあります。詳しくは「リトライ処理」や「メッセージキュー」で検索してみてください。

<a id="深堀り3"></a>

## 【深堀り③】Java 標準ライブラリにおける Observer パターンの例

Java には、JDK 1.0 の時代から `java.util.Observer` インターフェースと `java.util.Observable` クラスという、名前のとおり Observer パターンを実装するための仕組みが用意されていました。しかし、この 2 つは Java 9 で非推奨（`@Deprecated`）となっています。

`Observable` クラスの Javadoc には、非推奨とされた理由として「サポートしているイベントモデルが限定的であること」「通知の順番が規定されていないこと」「状態の変化と通知が 1 対 1 に対応していないこと」が挙げられており、代わりに `java.beans` パッケージなどの利用が案内されています（→ OpenJDK [Observable.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/Observable.java)）。また、`Observable` はインターフェースではなくクラスであるため、通知する側のクラスは `Observable` クラスを継承しなければならず、他のクラスを継承できなくなるという使いづらさもありました。

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

`PropertyChangeSupport` クラスの `addPropertyChangeListener` メソッドは、リスナーを `map` フィールド（プロパティ名ごとにリスナーを管理する内部クラス）に登録します。`PropertyChangeListenerProxy` の分岐は特定のプロパティだけを監視するリスナー向けの処理で、それ以外のリスナーは `null` をキーとして「すべてのプロパティの変化を受け取るリスナー」として登録されます。これは、本記事の `addObserver` メソッドにあたります。

`firePropertyChange` メソッドは、変化したプロパティ名と変化前後の値をまとめた `PropertyChangeEvent`（イベントオブジェクト）を受け取り、`fire` メソッドで登録済みのリスナーの `propertyChange` メソッドを順番に呼び出しています。これは、本記事の `notifyObservers` メソッドにあたります。値の変化 1 回ごとに、その変化の内容を持つ `PropertyChangeEvent` が 1 つ通知されるため、「状態の変化と通知が 1 対 1 に対応していない」という `Observable` クラスの弱点が解消されています。また、変化前後の値が等しい場合は、実際には変化していないものとして通知しない判定も組み込まれています。<br>
さらに、イベントオブジェクトがプロパティ名と変化前後の値を持ち、特定のプロパティだけを監視するリスナーも登録できるため、「サポートしているイベントモデルが限定的」という弱点も改善されています。一方で、上記のコードは登録順にリスナーを呼び出していますが、この順番は `PropertyChangeSupport` クラスの仕様として保証されたものではありません。そのため、通知の順番に依存しない作りにすることが重要な点は変わりません（→ [【深堀り②】通知の途中で例外が発生した場合](#深堀り2)）。

利用する側は、自分のクラスに `PropertyChangeSupport` クラスのインスタンスを持たせ、値を変更するたびに `firePropertyChange` メソッドを呼び出すだけで、登録済みのリスナーへの通知を任せられます。継承ではなくフィールドとして持たせる形のため、`Observable` クラスのように継承の枠を使ってしまうこともありません。

<a id="深堀り4"></a>

## 【深堀り④】OCP（オープン・クローズドの原則）

正しい実装を振り返ると、異動の連携先が増える（例えば「経費精算システム」を追加する）場合、インターフェース `TransferObserver` を実装したクラスを新たに 1 つ追加し、`Main` クラス側で `addObserver` メソッドを呼び出して登録するだけで、既存の `TransferService` クラスや、他の連携先のクラスには一切手を加える必要がありません。

この「既存コードを変えずに、新しいクラスを追加するだけで機能を拡張できる」という設計は、「**OCP（Open/Closed Principle：オープン・クローズドの原則）**」と呼ばれる設計原則の実践です。Observer パターンは OCP を実現するための設計手段の一つと言えます。

詳しくは「OCP」や「オープン・クローズドの原則」で検索してみてください。

<a id="深堀り5"></a>

## 【深堀り⑤】GoF デザインパターンとの位置づけ

今回使った Observer パターンは、GoF（Gang of Four）の 23 のデザインパターンのうち「振る舞いパターン」に分類されます。<br>
詳しくは「GoF」で検索してみてください。
