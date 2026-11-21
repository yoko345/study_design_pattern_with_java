package chapter23.practice.language;

/**
 * primitive の command（go, right, left）を解析するクラスです。
 *
 * <p>解析の時点で ExecutorFactory から Executor を受け取っておき、execute() では受け取った Executor に処理を委譲します。
 */
public class PrimitiveCommandNode extends Node {
    /**
     * 解析した command の名前
     */
    private String name;

    /**
     * command の名前に対応して生成された Executor
     */
    private Executor executor;

    /**
     * 現在のトークンが primitive command であることを確認し、対応する Executor を生成します。
     *
     * @param context 解析対象のトークンを保持する Context
     * @throws ParseException トークンがない、または未知の command の場合
     */
    @Override
    public void parse(Context context) throws ParseException {
        name = context.currentToken();

        if (name == null) {
            throw new ParseException("Error: Missing <primitive command>");
        } else if (!"go".equals(name) && !"right".equals(name) && !"left".equals(name)) {
            throw new ParseException("Error: Unknown <primitive command>: \"" + name + "\"");
        }

        context.skipToken(name);

        executor = context.createExecutor(name);
    }

    /**
     * 解析時に生成した Executor を実行します。
     */
    @Override
    public void execute() {
        executor.execute();
    }

    /**
     * command の名前を返します。
     *
     * @return command の名前
     */
    @Override
    public String toString() {
        return name;
    }
}
