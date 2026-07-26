package chapter10;

import java.util.Random;

public class WinningStrategy extends Strategy {
    private Random random;
    private boolean won = false;
    private Hand prevHand;

    public WinningStrategy(int randomSeed) {
        random = new Random(randomSeed);
    }

    /**
     * - 前回の勝負に勝ったら次も同じ手<br>
     * - 前回の勝負に負けたら次はランダムな手
     */
    @Override
    public Hand nextHand() {
        if (!won) {
            prevHand = Hand.getHand(random.nextInt(3));
        }

        return prevHand;
    }

    @Override
    public void study(boolean win) {
        won = win;
    }
}
