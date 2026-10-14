package chapter19;

public class DayState implements State {
    private static DayState singleton = new DayState();

    private DayState() {
    }

    public static State getInstance() {
        return singleton;
    }

    @Override
    public void doClock(Context context, int hour) {
        // 練習問題19-2
        // if (hour < 9 || hour >= 17) {
        if (hour < 8 || hour >= 21) {
            context.changeState(NightState.getInstance());
        } else if (hour >= 12 && hour < 13) {
            context.changeState(LunchState.getInstance());
        }
    }

    @Override
    public void doUse(Context context) {
        context.recordLog("金庫使用(昼間)");
    }

    @Override
    public void doAlarm(Context context) {
        context.callSecurityCenter("非常ベル(昼間)");
    }

    @Override
    public void doPhone(Context context) {
        context.callSecurityCenter("通常の通話(昼間)");
    }

    @Override
    public String toString() {
        return "[昼間]";
    }
}
