package chapter19;

public class MainChapter19 {
    public static void main(String[] args) {
        SafeFrame safeFrame = new SafeFrame("State Sample");

        while (true) {
            for (int hour = 0; hour < 24; hour++) {
                safeFrame.setClock(hour);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
