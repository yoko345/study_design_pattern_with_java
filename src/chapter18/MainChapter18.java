package chapter18;

import chapter18.game.Gamer;
import chapter18.game.Memento;

public class MainChapter18 {
    // 模範解答
    public static final String SAVE_FILENAME = "game.dat";

    public static void main(String[] args) {
        // 練習問題18-4
        // 模範解答
        Gamer gamer = new Gamer(100);
        // // 最初の状態を保存
        // Memento memento = gamer.createMemento();
        // Gamer gamer = new Gamer("game.dat");
        // Memento memento = gamer.createMemento("game.dat");
        // if (memento == null) {
        //     System.out.println("保存に失敗しました。");
        //     return;
        // }
        Memento memento = Memento.loadFromFile(SAVE_FILENAME);
        if (memento == null) {
            System.out.println("新規にスタートします。");
            memento = gamer.createMemento();
        } else {
            System.out.println("前回保存した結果からゲームをスタートします。");
            gamer.restoreMemento(memento);
        }

        // ゲーム開始
        for (int i = 0; i < 100; i++) {
            System.out.println("===== " + i);
            System.out.println("現状：" + gamer);

            // ゲームを進める
            gamer.bet();

            System.out.println("所持金は" + gamer.getMoney() + "円になりました。");

            // Mementoの取り扱いの決定
            if (gamer.getMoney() > memento.getMoney()) {
                System.out.println("※だいぶ増えたので、現在の状態を保存しておこう！");
                // 練習問題18-4
                // 模範解答
                memento = gamer.createMemento();
                // memento = gamer.createMemento("game.dat");

                // 練習問題18-4
                // 模範解答
                // if (memento == null) {
                //     System.out.println("保存に失敗しました。");
                //     break;
                // }
                if (Memento.saveToFile(SAVE_FILENAME, memento)) {
                    System.out.println("現在の状態をファイルに保存しました。");
                }
            } else if (gamer.getMoney() < memento.getMoney() / 2) {
                System.out.println("※だいぶ減ったので、以前の状態を復元しよう！");
                gamer.restoreMemento(memento);
            }

            // 時間待ち
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }

            System.out.println();
        }
    }
}
