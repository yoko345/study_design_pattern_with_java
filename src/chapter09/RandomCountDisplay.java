package chapter09;

import java.util.Random;

public class RandomCountDisplay extends CountDisplay {
    private Random random = new Random();

    public RandomCountDisplay(DisplayImp displayImp) {
        super(displayImp);
    }

    public void randomDisplay(int times) {
        multiDisplay(random.nextInt(times));
    }
}
