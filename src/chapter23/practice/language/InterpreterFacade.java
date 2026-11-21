package chapter23.practice.language;

/**
 * インタプリタを使いやすくするファサードクラスです。
 *
 * <p>
 * 利用側は parse() でプログラムを読み込み、execute() で実行するだけで済みます。
 * Context の準備や構文木の組み立ては、このクラスの内部で行います。
 */
public class InterpreterFacade implements Executor {
    /**
     * primitive command に対応する Executor を生成するファクトリ
     */
    private ExecutorFactory factory;

    /**
     * 直近で正常に解析できたプログラムの構文木。解析に失敗しても、この値は変わりません
     */
    private Node programNode;

    /**
     * 構文木の生成に使う ExecutorFactory を受け取ります。
     *
     * @param factory primitive command に対応する Executor を生成するファクトリ
     */
    public InterpreterFacade(ExecutorFactory factory) {
        this.factory = factory;
    }

    /**
     * 文字列を解析して構文木を作り、次に execute() で実行するプログラムとして保持します。
     *
     * <p>
     * 構文木は解析が成功したときだけ差し替えます。そのため、解析に失敗しても、直前に正常に解析したプログラムを実行できます。
     *
     * @param text 解析するプログラムの文字列
     * @throws ParseException 構文が正しくない場合
     */
    public void parse(String text) throws ParseException {
        Context newContext = new Context(text);
        newContext.setExecutorFactory(factory);

        Node newProgramNode = new ProgramNode();
        newProgramNode.parse(newContext);

        this.programNode = newProgramNode;
        System.out.println(programNode.toString());
    }

    /**
     * 保持しているプログラムを実行します。まだ一度も解析に成功していない場合は、何もしません。
     */
    @Override
    public void execute() {
        if (programNode != null) {
            programNode.execute();
        }
    }
}
