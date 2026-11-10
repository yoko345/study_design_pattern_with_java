package chapter22.drawer;

import java.awt.Color;

public interface Drawable {
    public abstract void draw(int x, int y);

    // 練習問題22-1
    public abstract void setColor(Color color);
}
