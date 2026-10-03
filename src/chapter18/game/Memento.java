package chapter18.game;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// 練習問題18-4
// public class Memento {
public class Memento implements Serializable {
    // 練習問題18-4
    private static final long serialVersionUID = 1L;
    static final String BASE_FILE_PATH = "src/chapter18/tmp/";

    private int money;
    private List<String> fruits;

    // gameパッケージ外（Main）にも公開しているが、値を読むだけなので中身は変更されない
    public int getMoney() {
        return money;
    }

    // 練習問題18-4
    Memento(String filename) {
        Memento memento = loadFromFile(filename);

        if (memento != null) {
            this.money = memento.getMoney();
            this.fruits = memento.getFruits();
        } else {
            this.money = 100;
            this.fruits = new ArrayList<>();
        }
    }

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
    boolean saveToFile(String filename, Memento memento) {
        Path path = Path.of(BASE_FILE_PATH + filename);

        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(path))) {
            out.writeObject(memento);

            return true;
        } catch (IOException e) {
            e.printStackTrace();

            return false;
        }
    }

    // 練習問題18-4
    // saveToFileで保存したファイルの情報を元にMementoのインスタンスを生成する
    Memento loadFromFile(String filename) {
        Path path = Path.of(BASE_FILE_PATH + filename);

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(path))) {
            return (Memento) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();

            return null;
        }
    }
}
