package chapter09;

public class DesignPattern2Display extends Display {
    public DesignPattern2Display(DisplayImp displayImp) {
        super(displayImp);
    }

    public void patternDisplay(int times) {
        for (int i = 0; i < times; i++) {
            open();
            for (int j = 0; j < i; j++) {
                print();
                print();
            }
            close();
        }
    }
}
