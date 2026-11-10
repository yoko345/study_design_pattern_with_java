package chapter22.drawer;

import java.awt.Color;

public interface Drawable {
    // 練習問題22-1（模範解答）
    public abstract void init();

    public abstract void draw(int x, int y);

    // 練習問題22-1
    public abstract void setColor(Color color);
}
