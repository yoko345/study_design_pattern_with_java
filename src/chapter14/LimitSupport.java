package chapter14;

public class LimitSupport extends Support {
    private int limitNumber;

    public LimitSupport(String name, int limitNumber) {
        super(name);
        this.limitNumber = limitNumber;
    }

    @Override
    protected boolean resolve(Trouble trouble) {
        return trouble.getTroubleNumber() < limitNumber;
    }
}
