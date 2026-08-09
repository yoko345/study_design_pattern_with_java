package chapter12;

public class SideBorder extends Border {
    private char symbol;

    public SideBorder(Display display, char symbol) {
        super(display);
        this.symbol = symbol;
    }

    @Override
    public int getColumns() {
        return 1 + display.getColumns() + 1;
    }

    @Override
    public int getRows() {
        return display.getRows();
    }

    @Override
    public String getRowText(int row) {
        return symbol + display.getRowText(row) + symbol;
    }
}
