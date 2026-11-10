package chapter22;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;

import chapter22.command.Command;
import chapter22.command.MacroCommand;
import chapter22.drawer.ColorCommand;
import chapter22.drawer.DrawCanvas;
import chapter22.drawer.DrawCommand;

public class MainChapter22 extends JFrame implements MouseMotionListener, WindowListener {
    // 描画履歴
    private MacroCommand history = new MacroCommand();

    // 描画領域
    private DrawCanvas canvas = new DrawCanvas(400, 400, history);

    // 消去ボタン
    private JButton clearButton = new JButton("clear");

    // 練習問題22-1
    // 描画色変更ボタン
    // 練習問題22-1（模範解答）
    // private JButton changeColorButton = new JButton("changeColor");
    private JButton redButton = new JButton("red");
    private JButton blueButton = new JButton("blue");
    private JButton greenButton = new JButton("green");

    public MainChapter22(String title) {
        super(title);

        this.addWindowListener(this);
        canvas.addMouseMotionListener(this);
        clearButton.addActionListener(e -> {
            history.clear();
            // 練習問題22-1（模範解答）
            canvas.init();
            canvas.repaint();
        });
        // 練習問題22-1
        // 練習問題22-1（模範解答）
        // changeColorButton.addActionListener(e -> {
        // Command command = new ColorCommand(canvas, Color.BLUE);
        // history.append(command);
        // history.execute();
        // });
        redButton.addActionListener(e -> {
            Command command = new ColorCommand(canvas, Color.RED);
            history.append(command);
            command.execute();
        });
        blueButton.addActionListener(e -> {
            Command command = new ColorCommand(canvas, Color.BLUE);
            history.append(command);
            command.execute();
        });
        greenButton.addActionListener(e -> {
            Command command = new ColorCommand(canvas, Color.GREEN);
            history.append(command);
            command.execute();
        });

        Box buttonBox = new Box(BoxLayout.X_AXIS);
        buttonBox.add(clearButton);
        // 練習問題22-1
        // 練習問題22-1（模範解答）
        // buttonBox.add(changeColorButton);
        buttonBox.add(redButton);
        buttonBox.add(blueButton);
        buttonBox.add(greenButton);

        Box mainBox = new Box(BoxLayout.Y_AXIS);
        mainBox.add(buttonBox);
        mainBox.add(canvas);

        getContentPane().add(mainBox);

        pack();
        setVisible(true);
    }

    public static void main(String[] args) {
        new MainChapter22("Command Pattern Sample.");
    }

    // MouseMotionListener周り
    @Override
    public void mouseDragged(MouseEvent e) {
        Command command = new DrawCommand(canvas, e.getPoint());
        history.append(command);
        command.execute();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    // WindowListener周り
    @Override
    public void windowActivated(WindowEvent e) {
    }

    @Override
    public void windowClosed(WindowEvent e) {
    }

    @Override
    public void windowClosing(WindowEvent e) {
        System.exit(0);
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
    }

    @Override
    public void windowIconified(WindowEvent e) {
    }

    @Override
    public void windowOpened(WindowEvent e) {
    }
}
