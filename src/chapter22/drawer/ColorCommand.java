package chapter22.drawer;

import java.awt.Color;

import chapter22.command.Command;

// 練習問題22-1
public class ColorCommand implements Command {
    // 描画対象
    protected Drawable drawable;

    // 描画色
    private Color color;

    public ColorCommand(Drawable drawable, Color color) {
        this.drawable = drawable;
        this.color = color;
    }

    // 実行
    @Override
    public void execute() {
        drawable.setColor(color);
    }
}
