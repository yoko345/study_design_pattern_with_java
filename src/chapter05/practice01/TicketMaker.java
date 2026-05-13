package chapter05.practice01;

public class TicketMaker {
    private static TicketMaker ticketMaker = new TicketMaker();
    private int ticket = 1000;

    public static TicketMaker createTicket() {
        return ticketMaker;
    }

    /* 自分の解答 */
    // public int getTicketNumber() {
    //     return ticket++;
    // }
    /* 模範解答 */
    public synchronized int getTicketNumber() {
        return ticket++;
    }
}
