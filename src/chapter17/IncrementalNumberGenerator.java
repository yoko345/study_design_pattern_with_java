package chapter17;

public class IncrementalNumberGenerator extends NumberGenerator {
    private int number;
    // private int startNumber;
    private int endNumber;
    private int incrementNumber;

    // public IncrementalNumberGenerator(int startNumber, int endNumber, int incrementNumber) {
    public IncrementalNumberGenerator(int number, int endNumber, int incrementNumber) {
        // this.startNumber = startNumber;
        this.number = number;
        this.endNumber = endNumber;
        this.incrementNumber = incrementNumber;
    }

    @Override
    public int getNumber() {
        return number;
    }

    @Override
    public void execute() {
        // for (int i = startNumber; i < endNumber;) {
        //     number = i;
        //     i += incrementNumber;
        //     notifyObservers();
        // }
        while (number < endNumber) {
            notifyObservers();
            number += incrementNumber;
        }
    }
}
