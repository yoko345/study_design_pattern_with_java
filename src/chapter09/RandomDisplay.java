package chapter09;

import javax.management.RuntimeErrorException;

public class RandomDisplay extends Display {

    public RandomDisplay(DisplayImp displayImp) {
        super(displayImp);
    }

    public void randomDisplay(int times) {
        if (times < 0) {
            throw new RuntimeErrorException(new Error(), "0より大きい値を入れてください");
        } else if (times == 0) {
            return;
        }


        long random = Math.round(Math.random() * times);
        open();
        for (int i = 0; i < random; i++) {
            print();
        }
        close();
    }
}
