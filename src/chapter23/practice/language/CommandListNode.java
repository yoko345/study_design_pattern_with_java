package chapter23.practice.language;

import java.util.ArrayList;
import java.util.List;

/**
 * 複数の command の解析と end までの処理を行うクラスです。
 *
 * <p>「end」が現れるまで command を読み続け、読み取った順に実行します。
 */
public class CommandListNode extends Node {
    /**
     * 解析した command の一覧。読み取った順に並びます
     */
    private List<Node> list = new ArrayList<>();

    /**
     * 「end」が現れるまで command を解析し、一覧に追加します。「end」は読み飛ばします。
     *
     * <p>command の解析は、その command を読み終えた位置までトークンを進めるだけで、次のトークンが「end」かどうかは判定しません。
     * 判定は、ループの先頭に戻ったときにこのメソッドで行います。
     *
     * @param context 解析対象のトークンを保持する Context
     * @throws ParseException 「end」が見つからない、または command の構文が正しくない場合
     */
    @Override
    public void parse(Context context) throws ParseException {
        while (true) {
            if (context.currentToken() == null) {
                throw new ParseException("Error: Missing \"end\"");
            } else if ("end".equals(context.currentToken())) {
                context.skipToken("end");
                break;
            } else {
                Node commandNode = new CommandNode();
                commandNode.parse(context);
                list.add(commandNode);
            }
        }
    }

    /**
     * 一覧の command を先頭から順に実行します。
     */
    @Override
    public void execute() {
        for (Node node : list) {
            node.execute();
        }
    }

    /**
     * 一覧の内容を表す文字列を返します。
     *
     * @return 構文木の内容を表す文字列
     */
    @Override
    public String toString() {
        return list.toString();
    }
}
