import javax.smartcardio.*;
import java.util.List;

public class WatchCard {
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== WATCHING CARDS ON ALL TERMINALS FOR 20 SECONDS ===");
        TerminalFactory tf = TerminalFactory.getDefault();
        List<CardTerminal> terminals = tf.terminals().list();
        for (int i = 0; i < terminals.size(); i++) {
            System.out.println("[" + i + "] " + terminals.get(i).getName());
        }

        long end = System.currentTimeMillis() + 20000;
        int lastState = -1;
        while (System.currentTimeMillis() < end) {
            for (int i = 0; i < terminals.size(); i++) {
                CardTerminal t = terminals.get(i);
                boolean present = false;
                try {
                    present = t.isCardPresent();
                } catch (Exception e) {
                    // ignore
                }

                if (present) {
                    System.out.println(">>> DETECTED isCardPresent=true on [" + i + "] " + t.getName());
                    try {
                        Card c = t.connect("*");
                        System.out.println("    Connected! Protocol=" + c.getProtocol() + ", ATR=" + bytesToHex(c.getATR().getBytes()));
                        CardChannel ch = c.getBasicChannel();
                        ResponseAPDU uid = ch.transmit(new CommandAPDU(new byte[]{(byte)0xFF, (byte)0xCA, 0x00, 0x00, 0x00}));
                        System.out.printf("    UID: SW=%04X, Data=%s\n", uid.getSW(), bytesToHex(uid.getData()));
                        c.disconnect(false);
                    } catch (Exception e) {
                        System.out.println("    connect/transmit failed: " + e.getMessage());
                    }
                    Thread.sleep(1000);
                }
            }
            Thread.sleep(300);
        }
        System.out.println("Watcher finished.");
    }
}
