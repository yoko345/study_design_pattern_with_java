package chapter15;

import chapter15.pagemaker.PageMaker;

public class MainChapter15 {
    public static void main(String[] args) {
        PageMaker.makeWelcomPage("hyuki@example.com", "welcome.html");
        PageMaker.makeWelcomPage("tomura@example.com", "hello.html");
    }
}
