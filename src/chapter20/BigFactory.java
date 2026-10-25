package chapter20;

import java.util.HashMap;
import java.util.Map;

public class BigFactory {
    // すでに作ったBigCharのインスタンスを管理
    private Map<String, BigChar> pool = new HashMap<>();
    private static BigFactory singleton = new BigFactory();

    private BigFactory() {
    }

    public static BigFactory getInstance() {
        return singleton;
    }

    // BigCharのインスタンス生成（共有）
    // 練習問題20-3
    // synchronizedは「同期処理/非同期処理」の意味ではなく、複数スレッドが同時にこのメソッドへ入らないようにするロック。
    // ロックがないと、複数スレッドが同時にpoolを確認してどちらもnullを受け取り、同じ文字のBigCharを重複して生成してしまう
    // （共有が崩れるうえ、スレッドセーフでないHashMapへの同時書き込みで中身が壊れるおそれもある）。
    // シングルスレッドで動くMainChapter20では、付けても付けなくても結果は変わらない。
    public synchronized BigChar getBigChar(char charName) {
        String strName = String.valueOf(charName);
        BigChar bigChar = pool.get(strName);

        if (bigChar == null) {
            // ここでBigCharのインスタンス生成
            bigChar = new BigChar(charName);
            pool.put(strName, bigChar);
        }

        return bigChar;
    }
}
