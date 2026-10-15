package chapter19;

// 練習問題19-4
public class EmergencyState implements State {
    private static EmergencyState singleton = new EmergencyState();

    private EmergencyState() {
    }

    public static State getInstance() {
        return singleton;
    }

    @Override
    public void doClock(Context context, int hour) {
    }

    @Override
    public void doUse(Context context) {
        context.callSecurityCenter("非常事態時の金庫使用");
    }

    @Override
    public void doAlarm(Context context) {
        context.callSecurityCenter("非常ベル(非常事態時)");
    }

    @Override
    public void doPhone(Context context) {
        context.callSecurityCenter("非常事態時の通話");
    }

    @Override
    public String toString() {
        return "[非常事態時]";
    }
}
