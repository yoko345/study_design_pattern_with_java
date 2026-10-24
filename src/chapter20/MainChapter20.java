package chapter20;

public class MainChapter20 {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java -cp bin chapter20.MainChapter20 digits");
            System.out.println("Example: java -cp bin chapter20.MainChapter20 1234509876");
            System.exit(0);
        }

        BigString bigString = new BigString(args[0]);
        bigString.print();
    }
}
