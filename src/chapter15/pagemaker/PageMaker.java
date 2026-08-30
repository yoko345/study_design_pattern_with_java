package chapter15.pagemaker;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PageMaker {
    public static void makeWelcomPage(String email, String fileName) {
        try {
            Properties emailProperties = Database.getProperties("emailData");
            String userName = emailProperties.getProperty(email);

            HTMLWriter writer = new HTMLWriter(new FileWriter("src/chapter15/" + fileName));
            writer.title(userName + "'s Web Page");
            writer.paragraph("Welcome to " + userName + "'s Web Page!");
            writer.paragraph("Nice to meet you!");
            writer.mailTo(email, userName);
            writer.close();

            System.out.println(fileName + " is created for " + email + " (" + userName + ")");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 練習問題15-2
    public static void makeLinkPage(String fileName) {
        try {
            Properties emailProperties = Database.getProperties("emailData");

            HTMLWriter writer = new HTMLWriter(new FileWriter("src/chapter15/" + fileName));
            writer.title("Link Page");
            // emailProperties.forEach((email, userName) -> {
            //     try {
            //         writer.mailTo((String) email, (String) userName);
            //     } catch (IOException e) {
            //         e.printStackTrace();
            //     }
            // });
            // 模範解答
            for (String email : emailProperties.stringPropertyNames()) {
                String userName = emailProperties.getProperty(email, "(unknown)");
                writer.mailTo(email, userName);
            }
            // 模範解答（stringPropertyNames() + 拡張for文）が好ましい理由:
            // 1. forEach は Hashtable 由来のメソッドのため、Properties が new Properties(defaults) で親を持つ場合、defaults 側のエントリを列挙できない（getProperty(key) は「自分になければ親を見に行く」ロジックのため取得できるのに、全件列挙では漏れるという非対称な挙動になる）。 stringPropertyNames() は defaults を含めて正しく列挙できる。
            // 2. forEach のラムダは IOException を外に投げられず、ループ内で try-catch して握りつぶすしかない。外側の try-catch（52行目）と処理方針が二重になる。拡張 for 文なら例外はそのまま外側に伝播し一貫する。
            // 3. Properties は Hashtable<Object, Object> を継承しているため、forEach では (String) へのキャストが必要になる。stringPropertyNames() と getProperty() は どちらも String を直接返すためキャストが不要で型安全。
            writer.close();

            System.out.println(fileName + " is created.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
