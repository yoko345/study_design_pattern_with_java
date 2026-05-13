package chapter05.practice01;


public class Main01 {
    public static void main(String[] args) throws Exception {
        /* 自分の解答 */
        // TicketMaker obj1 = TicketMaker.createTicket();
        // TicketMaker obj2 = TicketMaker.createTicket();
        // TicketMaker obj3 = TicketMaker.createTicket();

        // System.out.println("チケット番号：" + obj1.getTicketNumber());
        // System.out.println("チケット番号：" + obj2.getTicketNumber());
        // System.out.println("チケット番号：" + obj3.getTicketNumber());

        /* 模範解答 */
        for (int i = 0; i < 10; i++) {
            System.out.println("チケット番号" + i + "：" + TicketMaker.createTicket().getTicketNumber());
        }
    }
}
