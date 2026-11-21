package chapter23.study;

// primitive に当たる処理
public class PrimitiveCommandNode extends Node {
    private String name;

    @Override
    public void parse(Context context) throws ParseException {
        name = context.currentToken();

        if (name == null) {
            throw new ParseException("Error: Missing <primitive command>");
        } else if (!"go".equals(name) && !"right".equals(name) && !"left".equals(name)) {
            throw new ParseException("Error: Unknown <primitive command>:\"" + name + "\"");
        }

        context.skipToken(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
