package chapter10;

// import java.util.Arrays;
import java.util.Random;

public class ProbStrategy extends Strategy {
    private Random random;
    private int prevHandValue = 0;
    private int currentHandValue = 0;
    /**
     * history[前回に出した手][今回出す手]<br>
     *
     * - history[0][0] グー、グーと出したときの過去の勝ち数<br>
     * - history[0][1] グー、チョキと出したときの過去の勝ち数<br>
     * - history[0][2] グー、パーと出したときの過去の勝ち数<br>
     */
    private int[][] history = {{1, 1, 1}, {1, 1, 1}, {1, 1, 1}};

    public ProbStrategy(int randomSeed) {
        random = new Random(randomSeed);
    }

    @Override
    public Hand nextHand() {
        int betHandValue = random.nextInt(getSum(currentHandValue));
        int handValue = 0;

        if (betHandValue < history[currentHandValue][0]) {
            handValue = 0;
        } else if (betHandValue < history[currentHandValue][0] + history[currentHandValue][1]) {
            handValue = 1;
        } else {
            handValue = 2;
        }

        prevHandValue = currentHandValue;
        currentHandValue = handValue;
        return Hand.getHand(handValue);
    }

    @Override
    public void study(boolean win) {
        if (win) {
            history[prevHandValue][currentHandValue]++;
        } else {
            history[prevHandValue][(currentHandValue + 1) % 3]++;
            history[prevHandValue][(currentHandValue + 2) % 3]++;
        }

        // System.out.println("ProbStrategyの戦略: " + Arrays.deepToString(history));
    }

    private int getSum(int handValue) {
        int sum = 0;
        for (int i = 0; i < 3; i++) {
            sum += history[handValue][i];
        }

        return sum;
    }
}
