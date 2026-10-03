package chapter18.game;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

// 練習問題18-4
// 模範解答
public class Memento {
    // public class Memento implements Serializable {
    // 練習問題18-4
    // 模範解答
    // private static final long serialVersionUID = 1L;
    static final String BASE_FILE_PATH = "src/chapter18/tmp/";

    private int money;
    private List<String> fruits;

    // gameパッケージ外（Main）にも公開しているが、値を読むだけなので中身は変更されない
    public int getMoney() {
        return money;
    }

    // 練習問題18-4
    // 模範解答
    // Memento(String filename) {
    //     Memento memento = loadFromFile(filename);

    //     if (memento != null) {
    //         this.money = memento.getMoney();
    //         this.fruits = memento.getFruits();
    //     } else {
    //         this.money = 100;
    //         this.fruits = new ArrayList<>();
    //     }
    // }

    // gameパッケージ外には公開していないので、Mementoを作れるのはGamerだけ
    Memento(int money) {
        this.money = money;
        this.fruits = new ArrayList<>();
    }

    // gameパッケージ外には公開していないので、中身を変更する処理を記載することもできる
    void addFruit(String fruit) {
        fruits.add(fruit);
    }

    // 中身を取り出す操作なので、gameパッケージ外には公開せず、状態を復元するGamerだけが使う
    List<String> getFruits() {
        return new ArrayList<>(fruits);
    }

    // 練習問題18-4
    // Mementoのインスタンスが持つ情報をファイルに保存する
    // boolean saveToFile(String filename, Memento memento) {
    // 模範解答
    // saveToFile / loadFromFile を public にしてよい理由:
    // 公開しているのはファイルとのやり取りだけなので、呼び出し側は Memento の中身を見ることも変えることもできない。
    // そのため Main（Caretaker）から呼べるようにしても、中身に触れられるのが Gamer だけである状態は保たれる。
    public static boolean saveToFile(String filename, Memento memento) {

        // Path path = Path.of(BASE_FILE_PATH + filename);

        // try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(path))) {
        //     out.writeObject(memento);

        //     return true;
        // } catch (IOException e) {
        //     e.printStackTrace();

        //     return false;
        // }

        // 模範解答
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("%d", memento.getMoney()));
        sb.append("\n");
        for (String fruit : memento.getFruits()) {
            sb.append(fruit);
            sb.append("\n");
        }

        try {
            Files.writeString(Path.of(BASE_FILE_PATH + filename), sb, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            e.printStackTrace();

            return false;
        }

        return true;

    }

    // 練習問題18-4
    // saveToFileで保存したファイルの情報を元にMementoのインスタンスを生成する
    // 模範解答
    // Memento loadFromFile(String filename) {
    public static Memento loadFromFile(String filename) {
        // Path path = Path.of(BASE_FILE_PATH + filename);

        // try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(path))) {
        //     return (Memento) in.readObject();
        // } catch (IOException | ClassNotFoundException e) {
        //     e.printStackTrace();

        //     return null;
        // }

        // 模範解答
        try {
            List<String> lines = Files.readAllLines(Path.of(BASE_FILE_PATH + filename));
            if (lines.size() == 0) {
                System.out.println("Empty file");

                return null;
            }

            int money = 0;
            try {
                money = Integer.parseInt(lines.get(0));
            } catch (NumberFormatException e) {
                System.out.println("Format error: " + e);

                return null;
            }

            Memento memento = new Memento(money);

            for (int i = 1; i < lines.size(); i++) {
                memento.addFruit(lines.get(i));
            }

            return memento;
        } catch (IOException e) {
            e.printStackTrace();

            return null;
        }
    }
}
