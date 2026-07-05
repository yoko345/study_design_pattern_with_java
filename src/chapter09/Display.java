package chapter09;

public class Display {
    private DisplayImp displayImp;

    public Display(DisplayImp displayImp) {
        this.displayImp = displayImp;
    }

    protected void open() {
        displayImp.rawOpen();
    }

    protected void print() {
        displayImp.rawPrint();
    }

    protected void close() {
        displayImp.rawClose();
    }

    public final void display() {
        open();
        print();
        close();
    }
}
