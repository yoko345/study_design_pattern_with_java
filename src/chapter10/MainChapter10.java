package chapter10;

public class MainChapter10 {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java -cp bin Main randomSeed1 randomSeed2");
            System.out.println("Example: java -cp bin chapter10.MainChapter10 314 15");
            System.exit(0);
        }

        int randomSeed1 = Integer.parseInt(args[0]);
        int randomSeed2 = Integer.parseInt(args[1]);

        Player player1 = new Player("Taro", new WinningStrategy(randomSeed1));
        // Player player2 = new Player("Hana", new ProbStrategy(randomSeed2));
        // 練習問題10-1
        Player player2 = new Player("Jiro", new RandomStrategy(randomSeed2));

        for (int i = 0; i < 10000; i++) {
            Hand nextHand1 = player1.nextHand();
            Hand nextHand2 = player2.nextHand();

            if (nextHand1.isStrongerThan(nextHand2)) {
                System.out.println("Winner: " + player1);
                player1.win();
                player2.lose();
            } else if (nextHand2.isStrongerThan(nextHand1)) {
                System.out.println("Winner: " + player2);
                player1.lose();
                player2.win();
            } else {
                System.out.println("Even... ");
                player1.even();
                player2.even();
            }
        }

        System.out.println("Total result: ");
        System.out.println(player1);
        System.out.println(player2);
    }
}
