package chapter12;

public class UpDownBorder extends Border {
    private char symbol;

    protected UpDownBorder(Display display, char symbol) {
        super(display);
        this.symbol = symbol;
    }

    @Override
    public int getColumns() {
        return display.getColumns();
    }

    @Override
    public int getRows() {
        return 1 + display.getRows() + 1;
    }

    @Override
    public String getRowText(int row) {
        if (row == 0 || row == display.getRows() + 1) {
            return makeLine(symbol, display.getColumns());
        } else {
            return display.getRowText(row - 1);
        }
    }


    private String makeLine(char symbol, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(symbol);
        }
        return sb.toString();
    }
}
