package chapter10.practice;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

// 練習問題10-4
public class Main {
    public static void main(String[] args) {
        List<String> list = Arrays.asList("D", "B", "C", "E", "A");

        // 辞書式で小さい順
        list.sort(new Comparator<String>() {
            @Override
            public int compare(String str1, String str2) {
                return str1.compareTo(str2);
            }

        });
        // 模範解答のもう一つ
        // list.sort((a, b) -> a.compareTo(b));
        System.out.println(list);

        // 辞書式で大きい順
        list.sort(new Comparator<String>() {
            @Override
            public int compare(String str1, String str2) {
                return -str1.compareTo(str2);
            }

        });
        // 模範解答のもう一つ
        // list.sort((a, b) -> b.compareTo(a));
        System.out.println(list);
    }
}
