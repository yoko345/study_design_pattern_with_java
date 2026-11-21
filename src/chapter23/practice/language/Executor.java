package chapter23.practice.language;

/**
 * 「実行」を表現するインタフェースです。
 *
 * <p>構文木のノードも、このインタフェースを実装して実行を表します。
 */
public interface Executor {
    /**
     * 実行します。
     */
    public abstract void execute();
}
