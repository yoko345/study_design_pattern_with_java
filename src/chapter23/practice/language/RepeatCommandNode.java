package chapter23.practice.language;

/**
 * repeat の command を解析するクラスです。
 *
 * <p>「repeat 回数 命令列 end」の形式を解析し、命令列を指定した回数だけ実行します。
 */
public class RepeatCommandNode extends Node {
    /**
     * 繰り返す回数
     */
    private int number;

    /**
     * 繰り返す命令列
     */
    private Node commandListNode;

    /**
     * 「repeat」「回数」を読み取り、続く命令列を解析します。
     *
     * @param context 解析対象のトークンを保持する Context
     * @throws ParseException 回数が整数でない、または命令列の構文が正しくない場合
     */
    @Override
    public void parse(Context context) throws ParseException {
        context.skipToken("repeat");
        number = context.currentNumber();
        context.nextToken();
        commandListNode = new CommandListNode();
        commandListNode.parse(context);
    }

    /**
     * 命令列を、指定した回数だけ繰り返して実行します。
     */
    @Override
    public void execute() {
        for (int i = 0; i < number; i++) {
            commandListNode.execute();
        }
    }

    /**
     * 回数と命令列を含む文字列を返します。
     *
     * @return 構文木の内容を表す文字列
     */
    @Override
    public String toString() {
        return "[repeat " + number + " " + commandListNode + "]";
    }
}
