package chapter23.practice.turtle;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;

import chapter23.practice.language.Executor;

/**
 * タートル（亀）の動きを描画する Canvas です。
 *
 * <p>
 * タートルは現在位置と向きを持ち、go / left / right の命令で、進んだり向きを変えたりします。
 * 向きは 12 段階の値で表し、1 段階は 30 度です。
 * 描画のたびに、設定された Executor を最初から実行して描き直します。
 */
public class TurtleCanvas extends Canvas {
    /**
     * 動くときの単位長さ
     */
    static final int UNIT_LENGTH = 30;

    /**
     * 上向き
     */
    static final int DIRECTION_UP = 0;

    /**
     * 右向き
     */
    static final int DIRECTION_RIGHT = 3;

    /**
     * 下向き
     */
    static final int DIRECTION_DOWN = 6;

    /**
     * 左向き
     */
    static final int DIRECTION_LEFT = 9;

    /**
     * 右に向く（現在の向きから 3 段階、つまり 90 度回る）
     */
    static final int RELATIVE_DIRECTION_RIGHT = 3;

    /**
     * 左に向く（現在の向きから -3 段階、つまり -90 度回る）
     */
    static final int RELATIVE_DIRECTION_LEFT = -3;

    /**
     * タートルの位置を示す円の半径
     */
    static final int RADIUS = 3;

    /**
     * 現在の向き。0 以上 12 未満の値
     */
    private int direction = 0;

    /**
     * 現在位置。Canvas の座標系で表します
     */
    private Point position;

    /**
     * 描画のたびに実行する Executor。setExecutor() で設定します
     */
    private Executor executor;

    /**
     * 指定したサイズの Canvas を作り、タートルを初期状態にします。
     *
     * @param width  Canvas の幅
     * @param height Canvas の高さ
     */
    public TurtleCanvas(int width, int height) {
        setSize(width, height);
        initialize();
    }

    /**
     * タートルを Canvas の中央、上向きの状態に戻し、描画内容を消去します。
     */
    private void initialize() {
        Dimension size = getSize();
        position = new Point(size.width / 2, size.height / 2);
        direction = 0;
        setForeground(Color.red);
        setBackground(Color.white);

        // コンストラクタの時点では描画先がまだ無く、getGraphics() が null を返すため確認する
        Graphics graphics = getGraphics();
        if (graphics != null) {
            graphics.clearRect(0, 0, size.width, size.height);
        }
    }

    /**
     * 描画のたびに実行する Executor を設定します。
     *
     * @param executor 実行するプログラム（InterpreterFacade など）
     */
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    /**
     * 現在の向きから、指定した段階数だけ回転します。
     *
     * @param relativeDirection 回転する段階数。正の値は右回り、負の値は左回り
     */
    void setRelativeDirection(int relativeDirection) {
        setDirection(direction + relativeDirection);
    }

    /**
     * 向きを設定します。負の値は、12 段階の中の向きに直して保持します。
     * 例えば -3 は 9（左向き）になります。
     *
     * @param direction 設定する向き。12 段階の値（0 以上 12 未満）を想定しています
     */
    void setDirection(int direction) {
        // 負の値の剰余は負になるため、12 を足してから再度剰余を取り、0 以上 12 未満にそろえる
        this.direction = ((direction % 12) + 12) % 12;
    }

    /**
     * 現在の向きに、指定した長さだけ進みます。通った線を描画し、進んだ先を新しい現在位置にします。
     *
     * @param length 進む長さ
     */
    void go(int length) {
        int newX = position.x;
        int newY = position.y;

        switch (direction) {
            case DIRECTION_UP:
                newY -= length;
                break;
            case DIRECTION_RIGHT:
                newX += length;
                break;
            case DIRECTION_DOWN:
                newY += length;
                break;
            case DIRECTION_LEFT:
                newX -= length;
                break;

            default:
                break;
        }

        Graphics graphics = getGraphics();
        if (graphics != null) {
            graphics.drawLine(position.x, position.y, newX, newY);
            graphics.fillOval(newX - RADIUS, newY - RADIUS, RADIUS * 2 + 1, RADIUS * 2 + 1);
        }

        position.x = newX;
        position.y = newY;
    }

    /**
     * Canvas の再描画時に呼び出されます。タートルを初期状態に戻してから、設定された Executor を実行して描き直します。
     *
     * @param graphics 描画に使う Graphics。このメソッドでは使わず、描画先は getGraphics() で取得します
     */
    @Override
    public void paint(Graphics graphics) {
        initialize();

        if (executor != null) {
            executor.execute();
        }
    }
}
