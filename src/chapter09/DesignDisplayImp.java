package chapter09;

public class DesignDisplayImp extends DisplayImp {
    private String headStr;
    private String designStr;
    private String tailStr;

    public DesignDisplayImp(String headStr, String designStr, String tailStr) {
        this.headStr = headStr;
        this.designStr = designStr;
        this.tailStr = tailStr;
    }

    @Override
    public void rawOpen() {
        System.out.print(headStr);
    }

    @Override
    public void rawPrint() {
        System.out.print(designStr);
    }

    @Override
    public void rawClose() {
        System.out.println(tailStr);
    }
}
