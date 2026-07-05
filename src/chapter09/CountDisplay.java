package chapter09;

public class CountDisplay extends Display {

    public CountDisplay(DisplayImp displayImp) {
        super(displayImp);
    }

    public void multiDisplay(int times) {
        open();
        for (int i = 0; i < times; i++) {
            print();
        }
        close();
    }
}
