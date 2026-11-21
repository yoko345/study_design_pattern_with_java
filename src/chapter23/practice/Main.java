package chapter23.practice;

import java.awt.BorderLayout;
import java.awt.TextField;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import chapter23.practice.language.InterpreterFacade;
import chapter23.practice.language.ParseException;
import chapter23.practice.turtle.TurtleCanvas;
import chapter23.practice.turtle.TurtleExecutorFactory;

/**
 * Interpreter パターンの動作を確認する画面を表示するクラスです。
 *
 * <p>
 * 上部のテキストフィールドにプログラムを入力して Enter キーを押すと、そのプログラムを解析し、
 * 下部のキャンバスでタートルを動かして描画します。
 * 解析に失敗した場合は、エラーメッセージをダイアログで表示します。
 */
public class Main extends JFrame {
    /**
     * タートルの動きを描画するキャンバス
     */
    private TurtleCanvas canvas = new TurtleCanvas(400, 400);

    /**
     * プログラムの解析と実行の窓口。canvas の初期化後に生成されるよう、canvas より後に宣言しています
     */
    private InterpreterFacade facade = new InterpreterFacade(new TurtleExecutorFactory(canvas));

    /**
     * 実行するプログラムを入力するテキストフィールド。初期値として、サンプルのプログラムを入れています
     */
    private TextField programTextField = new TextField("program repeat 3 go right go left end end");

    /**
     * 画面を組み立てて表示し、初期値のプログラムを実行します。
     *
     * @param title ウィンドウのタイトル
     */
    public Main(String title) {
        super(title);

        // setVisible() の時点で最初の描画が行われ、そのとき実行対象が必要になるため、先に設定しておく
        canvas.setExecutor(facade);

        setLayout(new BorderLayout());

        programTextField.addActionListener(e -> parseAndExecute());

        this.addWindowListener(new WindowAdapter() {
            /**
             * ウィンドウが閉じられたときに、アプリケーションを終了します。
             *
             * @param e ウィンドウのイベント
             */
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        add(programTextField, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        pack();
        setVisible(true);

        parseAndExecute();
    }

    /**
     * テキストフィールドのプログラムを解析し、成功したらキャンバスを描き直します。
     * 描画のたびにプログラムが実行されるため、解析の成功後に再描画を要求します。
     * 解析に失敗した場合は描画せず、エラーをダイアログで表示します。
     */
    private void parseAndExecute() {
        String programText = programTextField.getText();
        System.out.println("programText = " + programText);

        try {
            facade.parse(programText);
            canvas.repaint();
        } catch (ParseException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * アプリケーションの起動処理です。
     *
     * @param args コマンドライン引数（使用しません）
     */
    public static void main(String[] args) {
        new Main("Interpreter Pattern Sample");
    }
}
