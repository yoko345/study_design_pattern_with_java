package chapter17;

public class MainChapter17 {
    public static void main(String[] args) {
        // 練習問題17-1
        // NumberGenerator generator = new IncrementalNumberGenerator(10, 50, 5);

        // 練習問題17-2
        NumberGenerator generator = new RandomNumberGenerator();
        Observer observer1 = new DigitObserver();
        Observer observer2 = new GraphObserver();
        Observer observer3 = new FrameObserver();

        generator.addObserver(observer1);
        generator.addObserver(observer2);
        generator.addObserver(observer3);

        generator.execute();
    }
}
