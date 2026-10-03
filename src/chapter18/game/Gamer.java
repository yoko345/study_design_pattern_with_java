package chapter18.game;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Gamer {
    private int money;
    private List<String> fruits = new ArrayList<>();
    private Random random = new Random();
    private static String[] fruitNames = {"りんご", "ぶどう", "ばなな", "みかん"};

    // 練習問題18-4
    // 模範解答
    // public Gamer(String filename) {
    //     Path path = Path.of(Memento.BASE_FILE_PATH + filename);

    //     if (Files.exists(path)) {
    //         Memento memento = new Memento(filename);

    //         this.money = memento.getMoney();
    //         this.fruits = memento.getFruits();
    //     } else {
    //         this.money = 100;
    //     }
    // }

    public Gamer(int money) {
        this.money = money;
    }

    public int getMoney() {
        return money;
    }

    // 賭ける…ゲームの進行
    public void bet() {
        // サイコロを振る
        int dice = random.nextInt(6) + 1;

        if (dice == 1) {
            money += 100;

            System.out.println("所持金が増えました。");
        } else if (dice == 2) {
            money /= 2;

            System.out.println("所持金が半分になりました。");
        } else if (dice == 6) {
            String fruit = getFruit();

            System.out.println("フルーツ（" + fruit + "）をもらいました。");

            fruits.add(fruit);
        } else {
            System.out.println("何も起こりませんでした。");
        }
    }

    // スナップショットをとる
    // 練習問題18-4
    // 模範解答
    public Memento createMemento() {
    // public Memento createMemento(String filename) {
        Memento memento = new Memento(money);

        for (String fruit : fruits) {
            if (fruit.startsWith("おいしい")) {
                memento.addFruit(fruit);
            }
        }

        return memento;

        // 練習問題18-4
        // 模範解答
        // if (memento.saveToFile(filename, memento)) {
        //     return memento;
        // } else {
        //     return null;
        // }
    }

    // アンドゥを行う
    public void restoreMemento(Memento memento) {
        this.money = memento.getMoney();
        this.fruits = memento.getFruits();
    }

    @Override
    public String toString() {
        return "[money = " + money + ", fruits = " + fruits + "]";
    }

    private String getFruit() {
        String fruit = fruitNames[random.nextInt(fruitNames.length)];
        if (random.nextBoolean()) {
            return "おいしい" + fruit;
        } else {
            return fruit;
        }
    }
}

// 練習問題18-4 自分の解答の問題点（メモ）
// 1. Memento のメソッドは Gamer にしか見えてはいけないと思い込み、saveToFile / loadFromFile を public にしなかった。
// その結果、ファイルとのやり取りを Gamer の中で行うことになり、コンストラクタの追加と createMemento のシグネチャ変更まで必要になった。
// 「public にしたら中身が見えるか」ではなく「public にしたら呼び出し側に何ができるようになるか」で判断すべきだった。
// 2. createMemento がファイル保存まで行っていたため、保存に失敗すると null が返り、メモリ上では問題なく使えるはずのアンドゥまで止めてゲームを終了していた。
//
// 模範解答との比較
// - ファイルとのやり取りは Main（Caretaker）が Memento.saveToFile / loadFromFile を呼んで行い、Gamer は変更していない。
// → スナップショットを作って戻すのは Gamer、いつ・どこに保存するかを決めるのは Main という役割分担のままになっている。
// - ファイル保存とスナップショットの作成が分かれているため、保存に失敗してもメモリ上の Memento でゲームを続けられる。
// - 起動時の復元にも既存の restoreMemento を使っており、復元の経路が1つにまとまっている。
