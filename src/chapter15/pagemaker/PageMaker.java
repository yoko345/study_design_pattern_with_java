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
            emailProperties.forEach((email, userName) -> {
                try {
                    writer.mailTo((String) email, (String) userName);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            writer.close();

            System.out.println(fileName + " is created.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
