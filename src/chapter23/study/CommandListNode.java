package chapter23.study;

import java.util.ArrayList;
import java.util.List;

// 複数の command の解析及び end に当たる処理
public class CommandListNode extends Node {
    private List<Node> list = new ArrayList<>();

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

    @Override
    public String toString() {
        return list.toString();
    }
}
