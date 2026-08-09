package chapter12;

import java.util.ArrayList;
import java.util.List;

// 練習問題12-2
public class MultiStringDisplay extends Display {
    private int maxStrLength = 0;
    private List<String> strList = new ArrayList<>();

    @Override
    public int getColumns() {
        return maxStrLength;
    }

    @Override
    public int getRows() {
        return strList.size();
    }

    @Override
    public String getRowText(int row) {
        return getAddPadding(strList.get(row));
    }

    public void add(String str) {
        strList.add(str);

        if (str.length() > maxStrLength) {
            maxStrLength = str.length();
        }
    }

    private String getAddPadding(String str) {
        StringBuilder sb = new StringBuilder(str);
        for (int i = 0; i < maxStrLength - str.length(); i++) {
            sb.append(" ");
        }
        return sb.toString();
    }
}
