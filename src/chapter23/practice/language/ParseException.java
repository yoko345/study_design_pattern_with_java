package chapter23.practice.language;

/**
 * 構文解析に失敗したことを表す例外です。
 *
 * <p>メッセージには、利用者が入力を直せるように、失敗の原因を含めます。
 */
public class ParseException extends Exception {
    /**
     * 失敗の原因を示すメッセージを指定して生成します。
     *
     * @param msg 失敗の原因を示すメッセージ
     */
    public ParseException(String msg) {
        super(msg);
    }
}
