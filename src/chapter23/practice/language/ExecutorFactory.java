package chapter23.practice.language;

/**
 * 基本コマンドを生成するインタフェースです。
 *
 * <p>実際に何を実行するかは実装側が決めます。言語側は、名前から Executor を受け取るだけです。
 */
public interface ExecutorFactory {
    /**
     * 名前に対応する Executor を生成します。
     *
     * @param name 基本コマンドの名前（go, right, left など）
     * @return 名前に対応する Executor
     */
    public abstract Executor createExecutor(String name);
}
