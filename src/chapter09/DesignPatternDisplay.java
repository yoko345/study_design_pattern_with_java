package chapter09;

public class DesignPatternDisplay extends CountDisplay {
    private int step;

    public DesignPatternDisplay(DisplayImp displayImp, int step) {
        super(displayImp);
        this.step = step;
    }

    public void patternDisplay(int times) {
        int count = 0;

        for (int i = 0; i < times; i++) {
            multiDisplay(count);
            count += step;
        }
    }
}
