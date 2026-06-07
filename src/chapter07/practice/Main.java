package chapter07.practice;


public class Main {
    public static void main(String[] args) {
        if (args.length > 1) {
            usage();
            System.exit(0);
        }

        if (args[0].equals("MarkDown")) {
            MarkDownBuilder markDownBuilder = new MarkDownBuilder();
            Director director = new Director(markDownBuilder);
            director.construct();
        } else {
            usage();
            System.exit(0);
        }
    }

    private static void usage() {
        System.out.println("Usage: java -cp bin chapter07.practice.Main MarkDown");
    }
}
