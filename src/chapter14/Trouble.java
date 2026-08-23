package chapter14;

public class Trouble {
    private int troubleNumber;

    public Trouble(int troubleNumber) {
        this.troubleNumber = troubleNumber;
    }

    public int getTroubleNumber() {
        return troubleNumber;
    }

    @Override
    public String toString() {
        return "[Trouble " + troubleNumber + "]";
    }
}
