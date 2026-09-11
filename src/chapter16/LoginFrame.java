package chapter16;

import java.awt.CheckboxGroup;
import java.awt.Color;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LoginFrame extends Frame implements ActionListener, Mediator {
    private ColleagueCheckbox checkGuest;
    private ColleagueCheckbox checkLogin;
    private ColleagueTextField textUser;
    private ColleagueTextField textPass;
    private ColleagueButton buttonOk;
    private ColleagueButton buttonCancel;

    // Colleagueたちを生成し、配置後に表示する
    public LoginFrame(String title) {
        super(title);

        setBackground(Color.lightGray);
        setLayout(new GridLayout(4, 2));

        // Colleagueたちを生成する
        createColleagues();
        // 配置する
        add(checkGuest);
        add(checkLogin);
        add(new Label("Username: "));
        add(textUser);
        add(new Label("Password: "));
        add(textPass);
        add(buttonOk);
        add(buttonCancel);
        // 有効/無効の初期指定をする
        colleaguesChanged();

        // 表示する
        pack();
        setVisible(true);
    }

    @Override
    public void createColleagues() {
        CheckboxGroup checkboxGroup = new CheckboxGroup();
        checkGuest = new ColleagueCheckbox("Guest", checkboxGroup, true);
        checkLogin = new ColleagueCheckbox("Login", checkboxGroup, false);

        textUser = new ColleagueTextField("", 10);
        textPass = new ColleagueTextField("", 10);
        textPass.setEchoChar('*');

        buttonOk = new ColleagueButton("OK");
        buttonCancel = new ColleagueButton("Cancel");

        // Mediatorを設定する
        checkGuest.setMediator(this);
        checkLogin.setMediator(this);
        textUser.setMediator(this);
        textPass.setMediator(this);
        buttonOk.setMediator(this);
        buttonCancel.setMediator(this);

        // Listenerのセット
        checkGuest.addItemListener(checkGuest);
        checkLogin.addItemListener(checkLogin);
        textUser.addTextListener(textUser);
        textPass.addTextListener(textPass);
        buttonOk.addActionListener(this);
        buttonCancel.addActionListener(this);
    }

    @Override
    public void colleaguesChanged() {
        if (checkGuest.getState()) {
            // ゲストログイン
            textUser.setColleagueEnabled(false);
            textPass.setColleagueEnabled(false);
            buttonOk.setColleagueEnabled(true);
        } else {
            // ユーザーログイン
            textUser.setColleagueEnabled(true);
            userPassChanged();
        }
    }

    // textUserまたはtextPassの変更があった各Colleagueの有効/無効を判定する
    private void userPassChanged() {
        if (textUser.getText().length() > 0) {
            textPass.setColleagueEnabled(true);

            if (textPass.getText().length() > 0) {
                buttonOk.setColleagueEnabled(true);
            } else {
                buttonOk.setColleagueEnabled(false);
            }
        } else {
            textPass.setColleagueEnabled(false);
            buttonOk.setColleagueEnabled(false);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        System.out.println(e.toString());
        System.exit(0);
    }
}
