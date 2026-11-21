package chapter23.practice.language;

/**
 * 解析対象のトークン列と、現在の解析位置を保持するクラスです。
 *
 * <p>
 * 各 Node の parse() は、このクラスを通してトークンを読み進めます。
 * また、primitive command に対応する Executor の生成は、設定された ExecutorFactory に委譲します。
 */
public class Context implements ExecutorFactory {
    /**
     * primitive command に対応する Executor を生成するファクトリ
     */
    private ExecutorFactory factory;

    /**
     * 空白で区切ったトークンの配列
     */
    private String[] tokens;

    /**
     * 現在位置のトークン。末尾を超えた場合は null
     */
    private String lastToken;

    /**
     * 次に読み込むトークンの位置
     */
    private int index;

    /**
     * 解析対象の文字列をトークン列に分割し、先頭のトークンを現在位置にします。
     *
     * @param text 解析対象のプログラム文字列
     */
    public Context(String text) {
        // 先頭に空白があると空文字のトークンが作られてしまうため、前後の空白を取り除いてから分割する
        // \s+ : 空白文字が1個以上連続する箇所
        this.tokens = text.trim().split("\\s+");
        this.index = 0;
        nextToken();
    }

    /**
     * 現在位置のトークンを返します。位置は進めません。
     *
     * @return 現在位置のトークン。末尾を超えている場合は null
     */
    public String currentToken() {
        return lastToken;
    }

    /**
     * 次のトークンを読み込み、現在位置を 1 つ進めます。
     *
     * @return 読み込んだトークン。末尾を超えている場合は null
     */
    public String nextToken() {
        if (index < tokens.length) {
            lastToken = tokens[index++];
        } else {
            lastToken = null;
        }

        return lastToken;
    }

    /**
     * 現在のトークンが指定した文字列であることを確認し、現在位置を 1 つ進めます。
     *
     * @param token 期待するトークン
     * @throws ParseException 現在のトークンが指定した文字列と異なる、または末尾を超えている場合
     */
    public void skipToken(String token) throws ParseException {
        if (currentToken() == null) {
            throw new ParseException("Error: \"" + token + "\" is expected, but no more token is found.");
        } else if (!token.equals(currentToken())) {
            throw new ParseException("Error: \"" + token + "\" is expected, but \"" + currentToken() + "\" is found.");
        }

        nextToken();
    }

    /**
     * 現在のトークンを整数として返します。位置は進めません。
     *
     * @return 現在のトークンを整数に変換した値
     * @throws ParseException トークンがない、または整数に変換できない場合
     */
    public int currentNumber() throws ParseException {
        if (currentToken() == null) {
            throw new ParseException("Error: No more token.");
        }

        int number = 0;
        try {
            number = Integer.parseInt(currentToken());
        } catch (NumberFormatException e) {
            throw new ParseException("Error: Integer is expected, but \"" + currentToken() + "\" is found.");
        }

        return number;
    }

    /**
     * primitive command の Executor を生成するときに使う ExecutorFactory を設定します。
     *
     * @param factory 設定する ExecutorFactory
     */
    public void setExecutorFactory(ExecutorFactory factory) {
        this.factory = factory;
    }

    /**
     * 設定された ExecutorFactory に生成を委譲します。
     *
     * @param name primitive command の名前（go, right, left など）
     * @return 名前に対応する Executor
     */
    @Override
    public Executor createExecutor(String name) {
        return factory.createExecutor(name);
    }
}
