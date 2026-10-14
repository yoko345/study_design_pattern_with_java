package chapter19;

public class NightState implements State {
    private static NightState singleton = new NightState();

    private NightState() {
    }

    public static State getInstance() {
        return singleton;
    }

    @Override
    public void doClock(Context context, int hour) {
        // 練習問題19-2
        // if (hour >= 9 && hour < 17) {
        // 練習問題19-3
        // 【補足】時計は1時間ずつ連続して進むとは限らない（起動時刻・時刻変更などで飛ぶことがある）。
        // 夜間から12時台へ飛んだときに DayState を経由させる前提にすると、
        // その回の doClock では昼間の状態になってしまう。
        // そのため、夜間からも昼食時へ直接遷移できるようにする。
        // if (hour >= 8 && hour < 21) {
        if (hour >= 12 && hour < 13) {
            context.changeState(LunchState.getInstance());
        } else if (hour >= 8 && hour < 21) {
            context.changeState(DayState.getInstance());
        }
    }

    @Override
    public void doUse(Context context) {
        context.callSecurityCenter("非常：夜間の金庫使用");
    }

    @Override
    public void doAlarm(Context context) {
        context.callSecurityCenter("非常ベル(夜間)");
    }

    @Override
    public void doPhone(Context context) {
        context.recordLog("夜間の通話録音");
    }

    @Override
    public String toString() {
        return "[夜間]";
    }
}
