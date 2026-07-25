package chapter10;

public class Player {
    private String name;
    private Strategy strategy;
    private int winCount;
    private int loseCount;
    private int gameCount;

    public Player(String name, Strategy strategy) {
        this.name = name;
        this.strategy = strategy;
    }

    // 戦略にお伺いを立てて次の手を決める
    public Hand nextHand() {
        return strategy.nextHand();
    }

    // 勝った
    public void win() {
        strategy.study(true);
        winCount++;
        gameCount++;
    }

    // 負けた
    public void lose() {
        strategy.study(false);
        loseCount++;
        gameCount++;
    }

    // 引き分け
    public void even() {
        gameCount++;
    }

    @Override
    public String toString() {
        return "[" + name + ": " + gameCount + " games, " + winCount + " wins, " + loseCount
                + " losses, " + (gameCount - (winCount + loseCount)) + " even" + "]";
    }
}
