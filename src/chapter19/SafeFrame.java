package chapter19;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
// 練習問題19-1（模範解答）
// import java.awt.event.ActionEvent;
// import java.awt.event.ActionListener;

// 練習問題19-1（模範解答）
// public class SafeFrame extends Frame implements ActionListener, Context {
public class SafeFrame extends Frame implements Context {
    private TextField textClock = new TextField();
    private TextArea textScreen = new TextArea(10, 60);
    private Button buttonUse = new Button("金庫使用");
    private Button buttonAlarm = new Button("非常ベル");
    private Button buttonPhone = new Button("通常通話");
    private Button buttonExit = new Button("終了");

    private State state = DayState.getInstance();

    public SafeFrame(String title) {
        super(title);
        setBackground(Color.lightGray);
        setLayout(new BorderLayout());
        add(textClock, BorderLayout.NORTH);
        textClock.setEditable(false);
        add(textScreen, BorderLayout.CENTER);
        textScreen.setEditable(false);
        Panel panel = new Panel();
        panel.add(buttonUse);
        panel.add(buttonAlarm);
        panel.add(buttonPhone);
        panel.add(buttonExit);
        add(panel, BorderLayout.SOUTH);
        pack();
        setVisible(true);
        // リスナーの設定
        // 練習問題19-1
        // buttonUse.addActionListener(this);
        // buttonAlarm.addActionListener(this);
        // buttonPhone.addActionListener(this);
        // buttonExit.addActionListener(this);
        buttonUse.addActionListener(e -> state.doUse(this));
        buttonAlarm.addActionListener(e -> state.doAlarm(this));
        buttonPhone.addActionListener(e -> state.doPhone(this));
        buttonExit.addActionListener(e -> System.exit(0));
    }

    // ボタン押下後
    // 練習問題19-1（模範解答）
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //     System.out.println(e.toString());

    //     // 練習問題19-1
    //     if (e.getSource() == buttonUse) {
    //         state.doUse(this);
    //     } else if (e.getSource() == buttonAlarm) {
    //         state.doAlarm(this);
    //     } else if (e.getSource() == buttonPhone) {
    //         state.doPhone(this);
    //     } else if (e.getSource() == buttonExit) {
    //         System.exit(0);
    //     } else {
    //         System.out.println("不明");
    //     }
    // }

    // 時刻の設定
    @Override
    public void setClock(int hour) {
        String clockString = String.format("現在の時刻は%02d:00", hour);
        System.out.println(clockString);
        textClock.setText(clockString);
        state.doClock(this, hour);
    }

    // 状態変化
    @Override
    public void changeState(State state) {
        System.out.println(this.state + "から" + state + "へ状態が変化しました。");
        this.state = state;
    }

    // 警備センター警備員呼び出し
    @Override
    public void callSecurityCenter(String msg) {
        textScreen.append("call! " + msg + "\n");
    }

    // 警備センター記録
    @Override
    public void recordLog(String msg) {
        textScreen.append("record..." + msg + "\n");
    }
}
