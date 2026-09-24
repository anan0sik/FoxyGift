import javax.smartcardio.*;
import java.util.List;

public class TestNtagRead {
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    public static void main(String[] args) throws Exception {
        TerminalFactory tf = TerminalFactory.getDefault();
        List<CardTerminal> terminals = tf.terminals().list();
        CardTerminal picc = null;
        for (CardTerminal t : terminals) {
            if (t.getName().contains("PICC")) {
                picc = t;
                break;
            }
        }
        if (picc == null) {
            System.out.println("PICC reader not found");
            return;
        }

        System.out.println("Checking card on " + picc.getName() + "...");
        if (!picc.isCardPresent()) {
            System.out.println("Card not present right now. Please put the card on the reader and run again.");
            return;
        }

        Card card = picc.connect("*");
        CardChannel ch = card.getBasicChannel();
        System.out.println("Connected! Protocol=" + card.getProtocol());

        // 1. UID
        ResponseAPDU rUid = ch.transmit(new CommandAPDU(new byte[]{(byte)0xFF, (byte)0xCA, 0x00, 0x00, 0x00}));
        System.out.printf("UID: SW=%04X, Data=%s\n", rUid.getSW(), bytesToHex(rUid.getData()));

        // 2. Read 16 bytes from page 0
        ResponseAPDU r16 = ch.transmit(new CommandAPDU(new byte[]{(byte)0xFF, (byte)0xB0, 0x00, 0x00, 0x10}));
        System.out.printf("Read 16 bytes (FF B0 00 00 10): SW=%04X, Data=%s\n", r16.getSW(), bytesToHex(r16.getData()));

        // 3. Read 4 bytes from page 3 (CC)
        ResponseAPDU rP3 = ch.transmit(new CommandAPDU(new byte[]{(byte)0xFF, (byte)0xB0, 0x00, 0x03, 0x04}));
        System.out.printf("Read Page 3 (FF B0 00 03 04): SW=%04X, Data=%s\n", rP3.getSW(), bytesToHex(rP3.getData()));

        // 4. Read 4 bytes from page 4
        ResponseAPDU rP4 = ch.transmit(new CommandAPDU(new byte[]{(byte)0xFF, (byte)0xB0, 0x00, 0x04, 0x04}));
        System.out.printf("Read Page 4 (FF B0 00 04 04): SW=%04X, Data=%s\n", rP4.getSW(), bytesToHex(rP4.getData()));

        card.disconnect(false);
    }
}
