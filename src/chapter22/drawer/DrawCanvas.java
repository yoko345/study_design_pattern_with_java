package chapter22.drawer;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;

import chapter22.command.MacroCommand;

public class DrawCanvas extends Canvas implements Drawable {
    // 描画色
    private Color color = Color.red;

    // 描画する点の半径
    private int radius = 6;

    // 履歴
    private MacroCommand history;

    public DrawCanvas(int width, int height, MacroCommand history) {
        setSize(width, height);
        setBackground(Color.white);
        this.history = history;
    }

    // 履歴にある命令全てを再描画
    @Override
    public void paint(Graphics graphics) {
        history.execute();
    }

    // 描画
    @Override
    public void draw(int x, int y) {
        Graphics graphics = getGraphics();
        graphics.setColor(color);
        graphics.fillOval(x - radius, y - radius, radius * 2, radius * 2);
    }

    // 練習問題22-1
    // 描画色の設定
    @Override
    public void setColor(Color color) {
        this.color = color;
    }
}
