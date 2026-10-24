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
