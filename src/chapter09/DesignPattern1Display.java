package chapter09;

public class DesignPattern1Display extends Display {
    public DesignPattern1Display(DisplayImp displayImp) {
        super(displayImp);
    }

    public void patternDisplay(int times) {
        for (int i = 0; i < times; i++) {
            open();
            for (int j = 0; j < i; j++) {
                print();
            }
            close();
        }
    }
}
