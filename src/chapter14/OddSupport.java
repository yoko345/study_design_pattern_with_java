package chapter14;

public class OddSupport extends Support {
    public OddSupport(String name) {
        super(name);
    }

    @Override
    protected boolean resolve(Trouble trouble) {
        return trouble.getTroubleNumber() % 2 == 1;
    }
}
