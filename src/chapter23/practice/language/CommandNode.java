package chapter23.practice.language;

/**
 * repeat もしくは primitive の command を格納するクラスです。
 *
 * <p>先頭のトークンを見て、解析するクラスを切り替えます。
 */
public class CommandNode extends Node {
    /**
     * 解析した command。RepeatCommandNode または PrimitiveCommandNode
     */
    private Node node;

    /**
     * 先頭のトークンが「repeat」なら RepeatCommandNode、それ以外なら PrimitiveCommandNode で解析します。
     *
     * @param context 解析対象のトークンを保持する Context
     * @throws ParseException command の構文が正しくない場合
     */
    @Override
    public void parse(Context context) throws ParseException {
        if ("repeat".equals(context.currentToken())) {
            node = new RepeatCommandNode();
            node.parse(context);
        } else {
            node = new PrimitiveCommandNode();
            node.parse(context);
        }
    }

    /**
     * 格納している command を実行します。
     */
    @Override
    public void execute() {
        node.execute();
    }

    /**
     * 格納している command の内容を表す文字列を返します。
     *
     * @return 構文木の内容を表す文字列
     */
    @Override
    public String toString() {
        return node.toString();
    }
}
