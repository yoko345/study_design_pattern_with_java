package chapter18.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Gamer {
    private int money;
    private List<String> fruits = new ArrayList<>();
    private Random random = new Random();
    private static String[] fruitNames = {"りんご", "ぶどう", "ばなな", "みかん"};

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
    public Memento createMemento() {
        Memento memento = new Memento(money);

        for (String fruit : fruits) {
            if (fruit.startsWith("おいしい")) {
                memento.addFruit(fruit);
            }
        }

        return memento;
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
