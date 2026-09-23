package chapter17;

public class GraphObserver implements Observer {
    @Override
    public void update(NumberGenerator generator) {
        System.out.print("GraphObservers:");

        for (int i = 0; i < generator.getNumber(); i++) {
            System.err.print("*");
        }

        System.out.println();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.getStackTrace();
        }
    }
}
