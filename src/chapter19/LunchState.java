package chapter19;

// 練習問題19-3
public class LunchState implements State {
    private static LunchState singleton = new LunchState();

    private LunchState() {
    }

    public static State getInstance() {
        return singleton;
    }

    @Override
    public void doClock(Context context, int hour) {
        // 【補足】昼食時が昼間の時間帯の中にあるのは時刻の上での話。
        // 時刻が夜間へ飛んだときに DayState を経由させず、直接 NightState へ遷移させる。
        // 各状態は「今の時刻ならどの状態が正しいか」を下記の条件分岐を通して判断する。
        if (hour < 8 || hour >= 21) {
            context.changeState(NightState.getInstance());
        } else if ((hour >= 8 && hour < 12) || (hour >= 13 && hour < 21)) {
            context.changeState(DayState.getInstance());
        }
    }

    @Override
    public void doUse(Context context) {
        context.callSecurityCenter("非常：昼食時の金庫使用");
    }

    @Override
    public void doAlarm(Context context) {
        context.callSecurityCenter("非常ベル(昼食時)");
        // 練習問題19-4
        context.changeState(EmergencyState.getInstance());
    }

    @Override
    public void doPhone(Context context) {
        context.recordLog("昼食時の通話録音");
    }

    @Override
    public String toString() {
        return "[昼食時]";
    }
}
