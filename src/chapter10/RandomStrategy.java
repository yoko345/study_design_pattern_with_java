package chapter10;

import java.util.Random;

public class RandomStrategy extends Strategy {
    private Random random;
    // 模範解答
    // private boolean won;

    public RandomStrategy(int randomSeed) {
        random = new Random(randomSeed);
    }

    @Override
    public Hand nextHand() {
        return Hand.getHand(random.nextInt(3));
    }

    @Override
    public void study(boolean win) {
        // 模範解答：でたらめな手を出すため、空で問題ない
        // won = win;
    }
}
