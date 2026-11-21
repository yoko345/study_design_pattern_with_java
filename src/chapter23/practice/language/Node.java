package chapter23.practice.language;

/**
 * 構文木のノードを表現する抽象クラスです。
 *
 * <p>各ノードは parse() で自分の構文を読み取って構文木を組み立て、execute() で実行します。
 * 実行の仕方は Executor インタフェースで表します。
 */
public abstract class Node implements Executor {
    /**
     * Context のトークンを読み取り、このノードの構文木を組み立てます。
     *
     * @param context 解析対象のトークンを保持する Context
     * @throws ParseException 構文が正しくない場合
     */
    public abstract void parse(Context context) throws ParseException;
}
