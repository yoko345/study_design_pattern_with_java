package chapter05.practice02;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Triple {
    /*
     * 自分の解答（わからなかった）
     */
    // private Triple triple = new Triple();
    // private static Map<String, Triple> map = new HashMap<>();
    // private static String instanceName;

    // private Triple() {
    //     map.put("ALPHA", triple);
    //     map.put("BETA", triple);
    //     map.put("GANMA", triple);
    // }

    // public static Triple getInstance(String name) {
    //     if (map.containsKey(name)) {
    //         Triple.instanceName = name;
    //         return map.get(name);
    //     } else {
    //         throw new RuntimeException();
    //     }
    // }

    // public String getInstanceName() {
    //     return instanceName;
    // }

    /* 模範解答 */
    private static Map<String, Triple> map = new HashMap<>();
    // static初期化子の利用
    static {
        String[] names = {"ALPHA", "BETA", "GANMA"};
        Arrays.stream(names).forEach(str -> map.put(str, new Triple(str)));
    }

    private String instanceName;

    private Triple(String name) {
        System.out.println("インスタンス名：" + name + " のインスタンスを生成しました。");
        this.instanceName = name;
    }

    public static Triple getInstance(String name) {
        return map.get(name);
    }

    @Override
    public String toString() {
        return this.instanceName;
    }
}
