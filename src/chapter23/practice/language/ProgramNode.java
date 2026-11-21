package chapter23.practice.language;

/**
 * program の解析を行うクラスです。
 *
 * <p>プログラム全体（「program」と、それに続く命令列）を表す構文木のルートです。
 */
public class ProgramNode extends Node {
    /**
     * プログラム本体の命令列
     */
    private Node commandListNode;

    /**
     * 「program」を読み取り、続く命令列を解析します。
     *
     * @param context 解析対象のトークンを保持する Context
     * @throws ParseException 先頭が「program」でない、または命令列の構文が正しくない場合
     */
    @Override
    public void parse(Context context) throws ParseException {
        context.skipToken("program");
        commandListNode = new CommandListNode();
        commandListNode.parse(context);
    }

    /**
     * プログラム本体の命令列を実行します。
     */
    @Override
    public void execute() {
        commandListNode.execute();
    }

    /**
     * プログラム全体の構文木を表す文字列を返します。
     *
     * @return 構文木の内容を表す文字列
     */
    @Override
    public String toString() {
        return "[program " + commandListNode + "]";
    }
}
