package chapter10;

public enum Hand {
    ROCK("グー", 0), SCISSORS("チョキ", 1), PAPER("パー", 2);

    private String name;
    private int handValue;

    private static Hand[] hands = {ROCK, SCISSORS, PAPER};

    private Hand(String name, int handValue) {
        this.name = name;
        this.handValue = handValue;
    }

    public static Hand getHand(int handValue) {
        return hands[handValue];
    }

    public boolean isStrongerThan(Hand hand) {
        return fight(hand) == 1;
    }

    /**
     * 0: 引き分け、1: thisの勝ち、-1: handの勝ち<br>
     * this は呼び出し元の列挙定数（例: ROCK.isStrongerThan(...) なら this == ROCK）
     */
    private int fight(Hand hand) {
        if (this == hand) {
            return 0;
        } else if ((this.handValue + 1) % 3 == hand.handValue) {
            return 1;
        } else {
            return -1;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
