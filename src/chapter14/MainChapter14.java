package chapter14;

import java.util.Random;

public class MainChapter14 {
    public static void main(String[] args) {

        Support A = new NoSupport("A");
        Support B = new LimitSupport("B", 100);
        Support C = new OddSupport("C");
        Support D = new LimitSupport("D", 200);
        Support E = new SpecialSupport("E", 410);
        Support F = new LimitSupport("F", 300);

        A.setNextSupport(B).setNextSupport(C).setNextSupport(D).setNextSupport(E).setNextSupport(F);

        int random = new Random().nextInt(100) + 1;
        for (int i = 0; i < 500; i += random) {
            A.support(new Trouble(i));
        }
    }
}
