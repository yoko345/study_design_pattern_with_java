package chapter18.game;

import java.util.ArrayList;
import java.util.List;

public class Memento {
    private int money;
    private List<String> fruits;

    // gameパッケージ外（Main）にも公開しているが、値を読むだけなので中身は変更されない
    public int getMoney() {
        return money;
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
}
