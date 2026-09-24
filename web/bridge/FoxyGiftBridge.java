import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.smartcardio.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * FoxyGift ACR1581U SmartCard Local Bridge Server
 *
 * Provides a lightweight local HTTP REST API for web/admin_provisioning_tool.html
 * to communicate directly with ACS ACR1581U DualBoost II (and ACR122U / ACR1252U)
 * contactless smart card readers via standard PC/SC (javax.smartcardio).
 */
public class FoxyGiftBridge {

    private static final int PORT = 8989;
    private static TerminalFactory terminalFactory;

    public static void main(String[] args) {
        System.out.println("========================================================");
        System.out.println("     FoxyGift ACR1581U SmartCard PC/SC Bridge Server     ");
        System.out.println("========================================================");

        try {
            terminalFactory = TerminalFactory.getDefault();
            System.out.println("[PC/SC] Terminal factory initialized: " + terminalFactory.getType());
            listConnectedReaders();
        } catch (Exception e) {
            System.err.println("[PC/SC] Warning initializing PC/SC: " + e.getMessage());
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            // Endpoints
            server.createContext("/api/status", new StatusHandler());
            server.createContext("/api/readers", new ReadersHandler());
            server.createContext("/api/card", new CardStatusHandler());
            server.createContext("/api/transmit", new TransmitHandler());
            server.createContext("/api/preinit", new PreInitHandler());
            server.createContext("/api/read-balance", new ReadBalanceHandler());
            server.createContext("/api/diagnose", new DiagnoseHandler());
            server.createContext("/", new StaticFileHandler());

            server.start();
            System.out.println("[HTTP] Server listening on http://127.0.0.1:" + PORT);
            System.out.println("[READY] Open http://127.0.0.1:" + PORT + "/admin_provisioning_tool.html in your browser.");
            System.out.println("Press Ctrl+C to stop.");
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to start HTTP server on port " + PORT + ": " + e.getMessage());
        }
    }

    private static synchronized void resetPcscContext() {
        try {
            Class<?> pcsctClass = Class.forName("sun.security.smartcardio.PCSCTerminals");
            Field contextIdField = pcsctClass.getDeclaredField("contextId");
            contextIdField.setAccessible(true);
            contextIdField.setLong(null, 0L);

            Field terminalsField = pcsctClass.getDeclaredField("terminals");
            terminalsField.setAccessible(true);
            Map<?, ?> terminalsMap = (Map<?, ?>) terminalsField.get(null);
            if (terminalsMap != null) {
                terminalsMap.clear();
            }
        } catch (Throwable ignored) {}
        try {
            terminalFactory = TerminalFactory.getInstance("PC/SC", null);
        } catch (Exception e) {
            terminalFactory = TerminalFactory.getDefault();
        }
    }

    private static synchronized List<CardTerminal> getTerminalsList() throws CardException {
        try {
            if (terminalFactory == null) {
                resetPcscContext();
            }
            return terminalFactory.terminals().list();
        } catch (Exception e) {
            // Hot-plug recovery: if reader was unplugged/replugged or USB re-enumerated, reset static contextId
            resetPcscContext();
            try {
                return terminalFactory.terminals().list();
            } catch (Exception ex) {
                resetPcscContext();
                throw new CardException("No smart card readers available: " + ex.getMessage());
            }
        }
    }

    private static List<String> listConnectedReaders() {
        List<String> result = new ArrayList<>();
        try {
            List<CardTerminal> list = getTerminalsList();
            if (list != null && !list.isEmpty()) {
                for (CardTerminal t : list) {
                    result.add(t.getName());
                    System.out.println("  * Found reader: " + t.getName());
                }
            } else {
                resetPcscContext();
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static CardTerminal getTerminal(String requestedName) throws CardException {
        List<CardTerminal> list = getTerminalsList();
        if (list == null || list.isEmpty()) {
            throw new CardException("No smart card readers detected.");
        }

        // 1. If an explicit reader was chosen by user in the UI, prioritize it
        if (requestedName != null && !requestedName.trim().isEmpty()) {
            String req = requestedName.trim();
            for (CardTerminal t : list) {
                if (t.getName().equalsIgnoreCase(req)) return t;
            }
            for (CardTerminal t : list) {
                if (t.getName().toLowerCase().contains(req.toLowerCase())) return t;
            }
        }

        // 2. Strict preference for Contactless / PICC interface
        for (CardTerminal t : list) {
            String name = t.getName().toUpperCase();
            if (name.contains("ACR1581") && name.contains("PICC")) return t;
        }
        for (CardTerminal t : list) {
            String name = t.getName().toUpperCase();
            if (name.contains("PICC") || name.contains("CONTACTLESS") || name.contains(" NFC") || name.contains(" CL ") || name.endsWith(" CL 0")) return t;
        }
        for (CardTerminal t : list) {
            String name = t.getName().toUpperCase();
            if (!name.contains("ICC") && !name.contains("SAM") && name.contains("ACR")) return t;
        }

        // 3. If only ICC or SAM slots exist, warn user that Contactless PICC driver is required
        for (CardTerminal t : list) {
            String name = t.getName().toUpperCase();
            if (name.contains("ICC") || name.contains("SAM")) {
                throw new CardException("Обнаружен только контактный слот (" + t.getName() + "). Для бесконтактных меток FoxyGift требуется интерфейс PICC. Убедитесь, что драйвер бесконтактного модуля ACR1581U активен.");
            }
        }

        return list.get(0);
    }

    private static String getQueryParam(String query, String param) {
        if (query == null || query.isEmpty()) return null;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = pair.substring(0, idx).trim();
                String val = pair.substring(idx + 1).trim();
                if (key.equalsIgnoreCase(param)) {
                    try {
                        return java.net.URLDecoder.decode(val, StandardCharsets.UTF_8.name());
                    } catch (Exception e) {
                        return val;
                    }
                }
            }
        }
        return null;
    }

    private static class CardTechInfo {
        final String chipType;
        final boolean compatible;
        final String notice;

        CardTechInfo(String chipType, boolean compatible, String notice) {
            this.chipType = chipType;
            this.compatible = compatible;
            this.notice = notice;
        }
    }

    private static CardTechInfo identifyCardTech(byte[] atr, byte[] uid) {
        int uidLen = (uid != null) ? uid.length : 0;

        if (uidLen == 7) {
            return new CardTechInfo("NTAG", true, "Чип NTAG полностью совместим с FoxyGift");
        }
        if (uidLen == 4) {
            return new CardTechInfo("MIFARE Classic (4 байта)", false,
                "Обнаружена карта MIFARE Classic с 4-байтным UID. Для карт лояльности FoxyGift требуются чипы NXP NTAG213/215/216.");
        }
        String atrHex = (atr != null) ? bytesToHex(atr).replace(" ", "").toUpperCase() : "";
        if (atrHex.contains("0028")) {
            return new CardTechInfo("ISO 14443-4 (DESFire / SmartCard)", false,
                "Обнаружена микропроцессорная карта ISO 14443-4. Для подарочных карт требуются NXP NTAG213/215/216.");
        }
        return new CardTechInfo("UNKNOWN (" + uidLen + " bytes UID)", false, "Неизвестный тип карты (длина UID: " + uidLen + " байт).");
    }

    // ─────────────────────── CORS & HTTP Helpers ─────────────────────────

    private static void handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        handleCors(exchange);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    // ─────────────────────── Handlers ─────────────────────────────────────

    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String json = "{\"status\":\"ok\",\"bridge\":\"FoxyGift ACR1581U PC/SC Bridge\",\"version\":\"2.0\"}";
            sendJsonResponse(exchange, 200, json);
        }
    }

    static class ReadersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            List<String> readers = listConnectedReaders();
            StringBuilder sb = new StringBuilder();
            sb.append("{\"readers\":[");
            for (int i = 0; i < readers.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(readers.get(i))).append("\"");
            }
            sb.append("]}");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    // Global mutex lock for all PC/SC card operations to prevent WinSCard sharing violations
    private static final Object CARD_LOCK = new Object();

    static class CardStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                synchronized (CARD_LOCK) {
                    String query = exchange.getRequestURI().getQuery();
                    String reqReader = getQueryParam(query, "reader");
                    CardTerminal terminal;
                    try {
                        terminal = getTerminal(reqReader);
                    } catch (CardException ce) {
                        sendJsonResponse(exchange, 200, "{\"present\":false,\"reader\":null,\"error\":\"" + escapeJson(ce.getMessage()) + "\"}");
                        return;
                    }

                    boolean present = false;
                    try {
                        present = terminal.isCardPresent();
                    } catch (Exception ce) {
                        // ignore
                    }

                    Card card = null;
                    if (present) {
                        try {
                            card = terminal.connect("*");
                        } catch (Exception e) {
                            present = false;
                        }
                    } else {
                        // Fallback: for some contactless PC/SC drivers where isCardPresent() cache
                        // is lazy, attempt connect directly
                        try {
                            card = terminal.connect("*");
                            present = true;
                        } catch (Exception ignored) {
                            present = false;
                        }
                    }

                    String slotType = terminal.getName().toUpperCase().contains("PICC") ? "PICC" : (terminal.getName().toUpperCase().contains("ICC") ? "ICC" : "SAM");

                    if (!present || card == null) {
                        sendJsonResponse(exchange, 200, "{\"present\":false,\"reader\":\"" + escapeJson(terminal.getName()) + "\",\"slotType\":\"" + slotType + "\"}");
                        return;
                    }

                    try {
                        CardChannel channel = card.getBasicChannel();
                        byte[] atrBytes = (card.getATR() != null) ? card.getATR().getBytes() : new byte[0];
                        String atrHex = bytesToHex(atrBytes);

                        // Get UID: FF CA 00 00 00
                        CommandAPDU getUid = new CommandAPDU(new byte[]{(byte) 0xFF, (byte) 0xCA, 0x00, 0x00, 0x00});
                        ResponseAPDU respUid = channel.transmit(getUid);
                        byte[] uidBytes = respUid.getData();
                        String uidHex = bytesToHex(uidBytes);
                        String cardDec = toDecimalString(uidBytes);

                        CardTechInfo tech = identifyCardTech(atrBytes, uidBytes);
                        String chipType = tech.chipType;
                        boolean isCompatible = tech.compatible;
                        String compatibilityNotice = tech.notice;

                        System.out.printf("[CARD_POLL] Card on %s: UID=%s (%d bytes), Tech=%s, Compatible=%b\n",
                                terminal.getName(), uidHex, (uidBytes != null ? uidBytes.length : 0), chipType, isCompatible);

                        // Read Page 3 (Capability Container CC) - strictly 4 bytes for NTAG213/215/216
                        String ccHex = "";
                        if (uidBytes != null && uidBytes.length == 7) {
                            try {
                                CommandAPDU readCC = new CommandAPDU(new byte[]{(byte) 0xFF, (byte) 0xB0, 0x00, 0x03, 0x04});
                                ResponseAPDU respCC = channel.transmit(readCC);
                                byte[] ccData = respCC.getData();
                                if (respCC.getSW() == 0x9000 && ccData != null && ccData.length >= 4) {
                                    ccHex = bytesToHex(ccData);
                                    int cc0 = ccData[0] & 0xFF;
                                    int cc2 = ccData[2] & 0xFF;
                                    if (cc0 == 0xE1) {
                                        if (cc2 == 0x12) chipType = "NTAG213";
                                        else if (cc2 == 0x3E || cc2 == 0x3F) chipType = "NTAG215";
                                        else if (cc2 == 0x6D || cc2 == 0x6E) chipType = "NTAG216";
                                        else chipType = "NTAG213";
                                    } else {
                                        chipType = "NTAG213";
                                    }
                                    isCompatible = true;
                                } else {
                                    chipType = "NTAG213";
                                    isCompatible = true;
                                }
                            } catch (Exception ignored) {
                                chipType = "NTAG213";
                                isCompatible = true;
                            }
                        }

                        // Read Page 4 (Magic and Merchant)
                        String page4Hex = "";
                        boolean isFoxy = false;
                        boolean isLocked = false;
                        if (isCompatible) {
                            try {
                                CommandAPDU readP4 = new CommandAPDU(new byte[]{(byte) 0xFF, (byte) 0xB0, 0x00, 0x04, 0x04});
                                ResponseAPDU respP4 = channel.transmit(readP4);
                                int p4Sw = respP4.getSW();
                                if (p4Sw == 0x6982 || p4Sw == 0x6300) {
                                    isLocked = true;
                                }
                                byte[] p4Data = respP4.getData();
                                if (p4Data != null && p4Data.length >= 4) {
                                    page4Hex = bytesToHex(p4Data);
                                    isFoxy = "464F5859".equalsIgnoreCase(page4Hex);
                                }
                            } catch (Exception ignored) {}
                        }

                        String json = String.format(
                                "{\"present\":true,\"reader\":\"%s\",\"slotType\":\"%s\",\"uid\":\"%s\",\"uidLength\":%d,\"cardNumberDec\":\"%s\",\"chipType\":\"%s\",\"isCompatible\":%b,\"compatibilityNotice\":\"%s\",\"cc\":\"%s\",\"page4\":\"%s\",\"isFoxy\":%b,\"isLocked\":%b,\"atr\":\"%s\"}",
                                escapeJson(terminal.getName()), slotType, uidHex, uidBytes != null ? uidBytes.length : 0, cardDec, chipType, isCompatible, escapeJson(compatibilityNotice), ccHex, page4Hex, isFoxy, isLocked, escapeJson(atrHex)
                        );
                        sendJsonResponse(exchange, 200, json);
                    } finally {
                        card.disconnect(false);
                    }
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 200, "{\"present\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class TransmitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                Map<String, String> map = parseSimpleJson(body);
                String apduHex = map.get("apdu");
                if (apduHex == null || apduHex.trim().isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Missing apdu parameter\"}");
                    return;
                }

                synchronized (CARD_LOCK) {
                    CardTerminal terminal = getTerminal(map.get("reader"));
                    Card card = terminal.connect("*");
                    try {
                        CardChannel channel = card.getBasicChannel();
                        byte[] apduBytes = hexToBytes(apduHex);
                        CommandAPDU cmd = new CommandAPDU(apduBytes);
                        ResponseAPDU resp = channel.transmit(cmd);

                        String dataHex = bytesToHex(resp.getData());
                        String swHex = String.format("%04X", resp.getSW());
                        boolean ok = resp.getSW() == 0x9000;

                        String json = String.format("{\"success\":%b,\"sw\":\"%s\",\"data\":\"%s\"}", ok, swHex, dataHex);
                        sendJsonResponse(exchange, 200, json);
                    } finally {
                        card.disconnect(false);
                    }
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class ChipException extends Exception {
        final String stage;
        final int page;
        final int sw;
        final String userHint;

        ChipException(String stage, int page, int sw, String message, String userHint) {
            super(message);
            this.stage = stage;
            this.page = page;
            this.sw = sw;
            this.userHint = userHint;
        }

        ChipException(String stage, int page, int sw, String message) {
            this(stage, page, sw, message, null);
        }
    }

    static class PreInitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                Map<String, String> map = parseSimpleJson(body);

                String masterKeyHex = map.get("masterKeyHex");
                String merchantIdStr = map.get("merchantId");
                String terminalId = map.get("terminalId");
                if (terminalId == null || terminalId.isEmpty()) terminalId = "TERM-001";
                String merchantIdHashHex = map.get("merchantIdHash");
                String chipType = map.get("chipType"); // "NTAG213", "NTAG215", "NTAG216", or "AUTO"

                if (masterKeyHex == null || merchantIdStr == null || merchantIdHashHex == null) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"stage\":\"VALIDATION\",\"error\":\"Missing required provisioning parameters\",\"userHint\":\"Не указаны параметры торговца или мастер-ключ.\"}");
                    return;
                }

                synchronized (CARD_LOCK) {
                    String requestedReader = map.get("reader");
                    CardTerminal terminal = getTerminal(requestedReader);
                    System.out.println("[PREINIT_REQ] Incoming preinit on " + terminal.getName() + " for merchant=" + merchantIdStr + ", chip=" + chipType);
                    if (!terminal.isCardPresent()) {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"stage\":\"CARD_REMOVED\",\"error\":\"No card present on reader\",\"userHint\":\"Карта отсутствует на ридере. Приложите карту к антенне ридера " + escapeJson(terminal.getName()) + ".\"}");
                        return;
                    }

                    Card card = terminal.connect("*");
                    try {
                        CardChannel channel = card.getBasicChannel();

                        // 1. Get UID
                        CommandAPDU getUid = new CommandAPDU(new byte[]{(byte) 0xFF, (byte) 0xCA, 0x00, 0x00, 0x00});
                        ResponseAPDU respUid = channel.transmit(getUid);
                        int swUid = respUid.getSW();
                        byte[] rawUid = respUid.getData();
                        if (swUid != 0x9000 || rawUid == null || rawUid.length < 4) {
                            throw new ChipException("GET_UID", 0, swUid,
                                "Failed to get tag UID: SW=" + String.format("%04X", swUid),
                                "Не удалось считать UID чипа (SW=" + String.format("%04X", swUid) + "). Убедитесь, что карта ровно прилегает к антенне ридера.");
                        }

                        if (rawUid.length == 4) {
                            String uid4 = bytesToHex(rawUid);
                            throw new ChipException("INCOMPATIBLE_MIFARE_CLASSIC", 0, swUid,
                                "MIFARE Classic card detected (4-byte UID: " + uid4 + ")",
                                "Обнаружена карта MIFARE Classic 1K/4K (4-байтный UID: " + uid4 + "). Система FoxyGift POS работает только с чипами NXP NTAG213/215/216 (7-байтный UID). Карты MIFARE Classic не поддерживаются.");
                        }

                        if (rawUid.length != 7) {
                            throw new ChipException("INCOMPATIBLE_UID_LENGTH", 0, swUid,
                                "Incompatible UID length: " + rawUid.length + " bytes",
                                "Неподдерживаемый чип: длина UID составляет " + rawUid.length + " байт. Требуются метки NXP NTAG213, NTAG215 или NTAG216 с 7-байтным UID.");
                        }

                        byte[] uidBytes = rawUid;
                        String uidHex = bytesToHex(uidBytes);
                        String cardDec = toDecimalString(uidBytes);

                        // 2. Identify Chip Type from CC (Page 3) - strictly 4 bytes
                        CommandAPDU readCC = new CommandAPDU(new byte[]{(byte) 0xFF, (byte) 0xB0, 0x00, 0x03, 0x04});
                        ResponseAPDU respCC = channel.transmit(readCC);
                        int swCC = respCC.getSW();
                        byte[] ccData = respCC.getData();
                        String detectedChip = null;
                        boolean needWriteCc = false;

                        if (swCC == 0x9000 && ccData != null && ccData.length >= 4) {
                            int cc0 = ccData[0] & 0xFF;
                            int cc2 = ccData[2] & 0xFF;
                            if (cc0 == 0xE1) {
                                if (cc2 == 0x12) detectedChip = "NTAG213";
                                else if (cc2 == 0x3E || cc2 == 0x3F) detectedChip = "NTAG215";
                                else if (cc2 == 0x6D || cc2 == 0x6E) detectedChip = "NTAG216";
                            } else if (cc0 == 0x00 && ccData[1] == 0x00 && ccData[2] == 0x00 && ccData[3] == 0x00) {
                                needWriteCc = true; // Brand new raw factory card without CC
                            }
                        }

                        String requestedChip = map.get("chipType");
                        if (requestedChip != null && !requestedChip.isEmpty() 
                                && !"AUTO".equalsIgnoreCase(requestedChip) 
                                && !"NTAG".equalsIgnoreCase(requestedChip) 
                                && !requestedChip.startsWith("NTAG (")) {
                            chipType = requestedChip;
                        } else if (detectedChip != null) {
                            chipType = detectedChip;
                        } else {
                            chipType = "NTAG213"; // Safe default for 7-byte tags (smallest capacity 144 bytes)
                        }

                        // Normalize to supported models (strictly default to NTAG213 to avoid memory boundary overflow)
                        if (!"NTAG215".equalsIgnoreCase(chipType) && !"NTAG216".equalsIgnoreCase(chipType)) {
                            chipType = "NTAG213";
                        }

                        System.out.println("[PREINIT] Target chip type: " + chipType + " (needWriteCc=" + needWriteCc + ")");

                        // 3. Derive PWD & PACK for NTAG:
                        byte[] derivedPwdPack = computeHmacSha256(hexToBytes(masterKeyHex), ("PWD:" + merchantIdStr + ":" + terminalId).getBytes(StandardCharsets.UTF_8));
                        byte[] pwd = new byte[4];
                        byte[] pack = new byte[2];
                        System.arraycopy(derivedPwdPack, 0, pwd, 0, 4);
                        System.arraycopy(derivedPwdPack, 4, pack, 0, 2);

                        // If brand new card without CC container, initialize Page 3
                        if (needWriteCc) {
                            byte ccSize = "NTAG216".equalsIgnoreCase(chipType) ? (byte) 0x6D : ("NTAG215".equalsIgnoreCase(chipType) ? (byte) 0x3E : (byte) 0x12);
                            try {
                                writePage(channel, 3, new byte[]{(byte) 0xE1, 0x10, ccSize, 0x00}, "WRITE_CC_CONTAINER", pwd);
                            } catch (Exception ignored) {}
                        }

                        // 4. Pre-check: If card is already password protected, unlock with the correct password.
                        // For forceReinit, the card may be locked with a DIFFERENT merchant's password —
                        // try the auth key first (old merchant), then fall back to the new merchant's key.
                        String forceReinit   = map.get("forceReinit");
                        String authKeyHex    = map.get("authMasterKeyHex");
                        String authMerchId   = map.get("authMerchantId");
                        String authTermId    = map.get("authTerminalId");
                        if (authTermId == null || authTermId.isEmpty()) authTermId = terminalId;

                        // unlockPwd = the password that actually opened the card for this session.
                        // CRITICAL: writePage() retries auth with whatever pwd we pass it — so we MUST
                        // pass the pwd that the card currently accepts (old merchant or new merchant).
                        byte[] unlockPwd = pwd; // default: new merchant's pwd (fresh card or same merchant)
                        boolean unlocked = authenticateWithPwd(channel, pwd); // Try new merchant's PWD first
                        if (!unlocked && "true".equalsIgnoreCase(forceReinit)
                                && authKeyHex != null && authMerchId != null) {
                            // Derive old merchant's PWD and try to unlock
                            byte[] authDerived = computeHmacSha256(hexToBytes(authKeyHex),
                                ("PWD:" + authMerchId + ":" + authTermId).getBytes(StandardCharsets.UTF_8));
                            byte[] authPwd = new byte[]{authDerived[0], authDerived[1], authDerived[2], authDerived[3]};
                            unlocked = authenticateWithPwd(channel, authPwd);
                            if (unlocked) {
                                unlockPwd = authPwd; // Card is locked with OLD pwd — use it for all writes
                                System.out.println("[REINIT] Unlocked with old-merchant PWD (authMerchId=" + authMerchId + "). Using old PWD for write retries.");
                            } else {
                                System.err.println("[REINIT] Both new-merchant and old-merchant PWD auth failed. Attempting writes anyway.");
                            }
                        }

                        // 5. Compute 16-byte HMAC digital signature for canonical card fields:
                        ByteBuffer msgBuf = ByteBuffer.allocate(7 + 4 + 4 + 4 + 1).order(ByteOrder.BIG_ENDIAN);
                        msgBuf.put(uidBytes);
                        msgBuf.putInt(0); // nominalCents = 0
                        msgBuf.putInt(0); // balanceCents = 0
                        msgBuf.putInt(0); // expiryEpoch = 0
                        msgBuf.put((byte) 0x03); // STATUS_PRE_INIT
                        byte[] fullHmac = computeHmacSha256(hexToBytes(masterKeyHex), msgBuf.array());
                        byte[] truncatedHmac = new byte[16];
                        System.arraycopy(fullHmac, 0, truncatedHmac, 0, 16);

                        // Prepare expected map of pages 4..16 for verify-after-write
                        Map<Integer, byte[]> expectedPages = new HashMap<>();
                        expectedPages.put(4, new byte[]{0x46, 0x4F, 0x58, 0x59}); // "FOXY"
                        expectedPages.put(5, hexToBytes(merchantIdHashHex));      // merchantIdHash
                        expectedPages.put(6, new byte[]{0, 0, 0, 0});
                        expectedPages.put(7, new byte[]{0, 0, 0, 0});
                        expectedPages.put(8, new byte[]{0, 0, 0, 0});
                        expectedPages.put(9, new byte[]{0, 0, 0, 0});
                        expectedPages.put(10, new byte[]{0, 0, 0, 0});
                        expectedPages.put(11, new byte[]{0, 0, 0, 0});
                        expectedPages.put(12, new byte[]{0x03, 0, 0, 0});        // STATUS_PRE_INIT
                        for (int i = 0; i < 4; i++) {
                            byte[] p = new byte[4];
                            System.arraycopy(truncatedHmac, i * 4, p, 0, 4);
                            expectedPages.put(13 + i, p);
                        }

                        // 6. Write FoxyGift core user pages (4..16).
                        // Use unlockPwd so writePage's internal retry authenticates with the correct password.
                        for (int page = 4; page <= 16; page++) {
                            byte[] pData = expectedPages.get(page);
                            writePage(channel, page, pData, "WRITE_USER_PAGE", unlockPwd);
                        }

                        // 7. Verify-After-Write: Read back pages 4..16 strictly 4 bytes per page
                        for (int page = 4; page <= 16; page++) {
                            CommandAPDU rApdu = new CommandAPDU(new byte[]{(byte) 0xFF, (byte) 0xB0, 0x00, (byte) page, 0x04});
                            ResponseAPDU rResp = channel.transmit(rApdu);
                            int sw = rResp.getSW();
                            if (sw != 0x9000 || rResp.getData() == null || rResp.getData().length < 4) {
                                throw new ChipException("VERIFY_READ_FAIL", page, sw,
                                    "Memory read-back verification failed on page " + page + ": SW=" + String.format("%04X", sw),
                                    "Не удалось перечитать данные со страницы " + page + " при верификации. Убедитесь, что карта ровно лежит на антенне.");
                            }
                            byte[] rData = rResp.getData();
                            byte[] exp = expectedPages.get(page);
                            if (exp != null) {
                                for (int b = 0; b < 4; b++) {
                                    if (rData[b] != exp[b]) {
                                        throw new ChipException("VERIFY_MISMATCH", page, sw,
                                            String.format("Flash verify mismatch on page %d: expected %s, got %s",
                                                page, bytesToHex(exp), bytesToHex(rData)),
                                            "Ошибка верификации ячейки памяти на странице " + page + ". Запись отклонена во избежание порчи карты.");
                                    }
                                }
                            }
                        }

                        // 8. Write Configuration & Security Lock (PWD, PACK, CFG1/PROT, CFG0/AUTH0)
                        int cfg0Page, cfg1Page, pwdPage, packPage;
                        if ("NTAG216".equalsIgnoreCase(chipType)) {
                            cfg0Page = 224; // 0xE0
                            cfg1Page = 225; // 0xE1
                            pwdPage  = 226; // 0xE2
                            packPage = 227; // 0xE3
                        } else if ("NTAG215".equalsIgnoreCase(chipType)) {
                            cfg0Page = 131; // 0x83
                            cfg1Page = 132; // 0x84
                            pwdPage  = 133; // 0x85
                            packPage = 134; // 0x86
                        } else {
                            // NTAG213: 45 pages (0..44)
                            chipType = "NTAG213";
                            cfg0Page = 41; // 0x29: [MIRROR, RFUI, MIRROR_PAGE, AUTH0]
                            cfg1Page = 42; // 0x2A: [ACCESS, RFUI, RFUI, RFUI]
                            pwdPage  = 43; // 0x2B: PWD (4 bytes)
                            packPage = 44; // 0x2C: PACK (2 bytes) + 0x00 0x00
                        }

                        // Write PWD and PACK first — use unlockPwd for auth retry (card still locked by old pwd).
                        // After writePage(pwdPage) succeeds, card's internal PWD register is updated to `pwd`
                        // (new merchant's), but the current RF session auth remains valid, so further writes work.
                        writePage(channel, pwdPage,  pwd, "WRITE_PWD",  unlockPwd);
                        writePage(channel, packPage, new byte[]{pack[0], pack[1], 0x00, 0x00}, "WRITE_PACK", unlockPwd);

                        // Write ACCESS: PROT=1 (bit7=0x80) -> Both READ and WRITE require password from AUTH0.
                        // Android NtagDriver always does PWD_AUTH before readPages(), so PROT=1 is required
                        // to prevent cloning via unauthenticated read.
                        writePage(channel, cfg1Page, new byte[]{(byte) 0x80, 0x00, 0x00, 0x00}, "WRITE_CFG1_PROT", unlockPwd);

                        // Write AUTH0: byte 3 = 0x04 -> Password protection starts from user page 4
                        writePage(channel, cfg0Page, new byte[]{0x00, 0x00, 0x00, 0x04}, "WRITE_CFG0_AUTH0", unlockPwd);

                        // 9. Post-lock validation: Test PWD_AUTH
                        boolean lockOk = authenticateWithPwd(channel, pwd);
                        if (!lockOk) {
                            System.err.println("[WARN] PWD_AUTH transparent check returned false on card " + cardDec + ", but configuration pages were written.");
                        }

                        System.out.println("[SUCCESS] Pre-initialized & locked card " + cardDec + " (" + chipType + ") for merchant " + merchantIdStr);

                        String json = String.format(
                                "{\"success\":true,\"uid\":\"%s\",\"cardNumberDec\":\"%s\",\"chipType\":\"%s\",\"merchantId\":%s}",
                                uidHex, cardDec, chipType, merchantIdStr
                        );
                        sendJsonResponse(exchange, 200, json);
                    } finally {
                        card.disconnect(false);
                    }
                }
            } catch (ChipException ce) {
                System.err.println("[CHIP_ERROR] Stage " + ce.stage + " (Page " + ce.page + "): " + ce.getMessage());
                String json = String.format(
                    "{\"success\":false,\"error\":\"%s\",\"stage\":\"%s\",\"page\":%d,\"sw\":\"%04X\",\"userHint\":\"%s\"}",
                    escapeJson(ce.getMessage()), escapeJson(ce.stage), ce.page, ce.sw, escapeJson(ce.userHint != null ? ce.userHint : ce.getMessage())
                );
                sendJsonResponse(exchange, 400, json);
            } catch (CardException ce) {
                System.err.println("[CARD_EXCEPTION] " + ce.getMessage());
                String msg = ce.getMessage() != null ? ce.getMessage() : "";
                String lower = msg.toLowerCase();
                String stage = "COMMUNICATION";
                String hint = "Ошибка связи с картой. Убедитесь, что карта плотно прилегает к ридеру.";
                if (lower.contains("removed") || lower.contains("no card") || lower.contains("smartcard")) {
                    stage = "CARD_TEAR_OFF";
                    hint = "Карта была убрана с ридера до завершения записи. Приложите карту и удерживайте до звукового сигнала.";
                } else if (lower.contains("unresponsive") || lower.contains("comm_data_lost")) {
                    stage = "RF_FIELD_LOSS";
                    hint = "Потеря радиочастотного поля (RF). Проверьте положение чипа на антенне ридера.";
                }
                String json = String.format(
                    "{\"success\":false,\"error\":\"%s\",\"stage\":\"%s\",\"userHint\":\"%s\"}",
                    escapeJson(ce.getMessage()), escapeJson(stage), escapeJson(hint)
                );
                sendJsonResponse(exchange, 400, json);
            } catch (Exception e) {
                System.err.println("[SYSTEM_ERROR] " + e.getMessage());
                String json = String.format(
                    "{\"success\":false,\"error\":\"%s\",\"stage\":\"SYSTEM\",\"userHint\":\"Внутренняя ошибка сервиса: %s\"}",
                    escapeJson(e.getMessage()), escapeJson(e.getMessage())
                );
                sendJsonResponse(exchange, 500, json);
            }
        }

        private void writePage(CardChannel channel, int page, byte[] data, String stage, byte[] pwd) throws ChipException, CardException {
            // Method 1: Standard PC/SC Update Binary (FF D6 00 [page] 04 [data])
            byte[] apdu = new byte[]{(byte) 0xFF, (byte) 0xD6, 0x00, (byte) (page & 0xFF), 0x04, data[0], data[1], data[2], data[3]};
            ResponseAPDU resp = channel.transmit(new CommandAPDU(apdu));
            int sw = resp.getSW();
            if (sw == 0x9000) return;

            // If security status not satisfied, try PWD_AUTH in case card was already write-protected
            if ((sw == 0x6982 || sw == 0x6300) && pwd != null) {
                if (authenticateWithPwd(channel, pwd)) {
                    ResponseAPDU retryResp = channel.transmit(new CommandAPDU(apdu));
                    if (retryResp.getSW() == 0x9000) return;
                    sw = retryResp.getSW();
                }
            }

            // Fallback Method 2: Direct ACS Transparent Exchange Ultralight WRITE (FF 00 00 00 06 A2 [page] [data])
            try {
                byte[] altApdu = new byte[]{(byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00, 0x06, (byte) 0xA2, (byte) (page & 0xFF), data[0], data[1], data[2], data[3]};
                ResponseAPDU altResp = channel.transmit(new CommandAPDU(altApdu));
                if (altResp.getSW() == 0x9000) return;
            } catch (Exception ignored) {}

            String msg = explainSw(sw, "write", page);
            String hint = getHintForSw(sw, page);
            throw new ChipException(stage, page, sw, "Failed to write page " + page + ": " + msg, hint);
        }

        private static boolean authenticateWithPwd(CardChannel channel, byte[] pwd) {
            try {
                // Method 1: ACS ACR pseudo-APDU Transparent Exchange: FF 00 00 00 Lc 1B P1 P2 P3 P4
                // This wraps the native NTAG PWD_AUTH (0x1B) command via the ACR1581U Transparent Exchange APDU.
                byte[] apdu = new byte[]{(byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00, 0x05, 0x1B, pwd[0], pwd[1], pwd[2], pwd[3]};
                ResponseAPDU resp = channel.transmit(new CommandAPDU(apdu));
                if (resp.getSW() == 0x9000) return true;

                // Method 2: Fallback via MANAGE SESSION escape APDU
                // Sends raw: FF 00 00 00 Lc A2 [page=E3/00 addr trick] — not applicable for auth.
                // Re-try with Lc=6 wrapping (some ACR firmware variants require exact framing)
                byte[] apdu2 = new byte[]{(byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00, 0x06, (byte) 0xD4, 0x40, 0x01, 0x1B, pwd[0], pwd[1], pwd[2], pwd[3]};
                ResponseAPDU resp2 = channel.transmit(new CommandAPDU(apdu2));
                if (resp2.getSW() == 0x9000) return true;
            } catch (Exception e) {
                System.err.println("[AUTH] PWD_AUTH exception: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * ReadBalance handler: authenticates with the card using the merchant's derived PWD,
     * then reads pages 4-12 to extract current balance, nominal, expiry and status.
     * Used by admin UI to warn about remaining balance before overwriting a card.
     *
     * POST /api/read-balance  { masterKeyHex, merchantId, terminalId, reader? }
     * Returns: { success, cardNumberDec, isFoxyCard, nominalCents, balanceCents,
     *            expiryEpoch, status, statusLabel, authResult }
     */
    static class ReadBalanceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try {
                String body = readRequestBody(exchange);
                Map<String, String> map = parseSimpleJson(body);
                String masterKeyHex  = map.get("masterKeyHex");
                String merchantIdStr = map.get("merchantId");
                String terminalId    = map.get("terminalId");
                if (terminalId == null || terminalId.isEmpty()) terminalId = "TERM-001";

                if (masterKeyHex == null || merchantIdStr == null) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"Missing masterKeyHex or merchantId\"}");
                    return;
                }

                synchronized (CARD_LOCK) {
                    CardTerminal terminal = getTerminal(map.get("reader"));
                    if (!terminal.isCardPresent()) {
                        sendJsonResponse(exchange, 400,
                            "{\"success\":false,\"error\":\"No card on reader\","
                            + "\"userHint\":\"Карта отсутствует. Приложите карту к ридеру и повторите.\"}" );
                        return;
                    }
                    Card card = terminal.connect("*");
                    try {
                        CardChannel ch = card.getBasicChannel();

                        // 1. GET UID
                        ResponseAPDU rUid = ch.transmit(
                            new CommandAPDU(new byte[]{(byte)0xFF,(byte)0xCA,0x00,0x00,0x00}));
                        byte[] uid = rUid.getData();
                        if (uid == null || uid.length < 4) {
                            sendJsonResponse(exchange, 500,
                                "{\"success\":false,\"error\":\"Failed to read UID\"}");
                            return;
                        }
                        String cardDec = toDecimalString(uid);

                        // 2. Derive PWD & PACK
                        byte[] derivedPwdPack = computeHmacSha256(
                            hexToBytes(masterKeyHex),
                            ("PWD:" + merchantIdStr + ":" + terminalId).getBytes(StandardCharsets.UTF_8));
                        byte[] pwd  = new byte[]{derivedPwdPack[0], derivedPwdPack[1],
                                                  derivedPwdPack[2], derivedPwdPack[3]};
                        byte[] pack = new byte[]{derivedPwdPack[4], derivedPwdPack[5]};

                        // 3. Attempt PWD_AUTH (card may be unprotected if brand new)
                        String authResult = "NONE";
                        try {
                            byte[] authApdu = new byte[]{(byte)0xFF,(byte)0x00,(byte)0x00,(byte)0x00,
                                                         0x05, 0x1B, pwd[0], pwd[1], pwd[2], pwd[3]};
                            ResponseAPDU rAuth = ch.transmit(new CommandAPDU(authApdu));
                            if (rAuth.getSW() == 0x9000) {
                                byte[] gotPack = rAuth.getData();
                                boolean packOk = gotPack != null && gotPack.length >= 2
                                    && gotPack[0] == pack[0] && gotPack[1] == pack[1];
                                authResult = packOk ? "AUTH_OK" : "AUTH_OK_PACK_MISMATCH";
                            } else {
                                authResult = "AUTH_FAIL";
                            }
                        } catch (Exception ae) {
                            authResult = "AUTH_EXCEPTION";
                        }

                        // 4. Read pages 4-12 (FOXY, merchant hash, nominal, balance, expiry, status)
                        byte[] p4  = readPageRaw(ch, 4);
                        byte[] p6  = readPageRaw(ch, 6);
                        byte[] p8  = readPageRaw(ch, 8);
                        byte[] p10 = readPageRaw(ch, 10);
                        byte[] p12 = readPageRaw(ch, 12);

                        // 5. Check FOXY magic
                        boolean isFoxyCard = (p4 != null && p4.length >= 4
                            && p4[0] == 0x46 && p4[1] == 0x4F && p4[2] == 0x58 && p4[3] == 0x59);

                        // 6. Parse values (big-endian int32 from first 4 bytes of each page-pair)
                        int nominalCents = 0, balanceCents = 0, expiryEpoch = 0;
                        byte statusByte = 0;
                        if (p6 != null && p6.length >= 4) {
                            nominalCents = ((p6[0]&0xFF)<<24)|((p6[1]&0xFF)<<16)|((p6[2]&0xFF)<<8)|(p6[3]&0xFF);
                        }
                        if (p8 != null && p8.length >= 4) {
                            balanceCents = ((p8[0]&0xFF)<<24)|((p8[1]&0xFF)<<16)|((p8[2]&0xFF)<<8)|(p8[3]&0xFF);
                        }
                        if (p10 != null && p10.length >= 4) {
                            expiryEpoch = ((p10[0]&0xFF)<<24)|((p10[1]&0xFF)<<16)|((p10[2]&0xFF)<<8)|(p10[3]&0xFF);
                        }
                        if (p12 != null && p12.length >= 1) statusByte = p12[0];

                        String statusLabel;
                        switch (statusByte & 0xFF) {
                            case 0x01: statusLabel = "ACTIVE";    break;
                            case 0x02: statusLabel = "EXHAUSTED"; break;
                            case 0x03: statusLabel = "PRE_INIT";  break;
                            case 0x04: statusLabel = "PROLONGED"; break;
                            default:   statusLabel = "UNKNOWN";   break;
                        }

                        String json = String.format(
                            "{\"success\":true,\"cardNumberDec\":\"%s\",\"isFoxyCard\":%b,"
                            + "\"nominalCents\":%d,\"balanceCents\":%d,\"expiryEpoch\":%d,"
                            + "\"status\":%d,\"statusLabel\":\"%s\",\"authResult\":\"%s\"}",
                            cardDec, isFoxyCard, nominalCents, balanceCents,
                            expiryEpoch, statusByte & 0xFF, statusLabel, authResult);
                        sendJsonResponse(exchange, 200, json);
                    } finally {
                        card.disconnect(false);
                    }
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 500,
                    "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }

        /** Read exactly 4 bytes from a page, returns null on failure. */
        private byte[] readPageRaw(CardChannel ch, int page) {
            try {
                ResponseAPDU r = ch.transmit(
                    new CommandAPDU(new byte[]{(byte)0xFF,(byte)0xB0,0x00,(byte)page,0x04}));
                if (r.getSW() == 0x9000 && r.getData() != null && r.getData().length >= 4)
                    return r.getData();
            } catch (Exception ignored) {}
            return null;
        }
    }

    /**
     * Diagnose handler: reads raw card memory pages 0-16 and CFG pages without authentication,
     * then attempts PWD_AUTH with the provided masterKey+merchantId+terminalId, then reads again.
     * Used by the admin UI to debug why an initialized card is not recognized by Android.
     */
    static class DiagnoseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try {
                String body = readRequestBody(exchange);
                Map<String, String> map = parseSimpleJson(body);
                String masterKeyHex = map.get("masterKeyHex");
                String merchantIdStr = map.get("merchantId");
                String terminalId = map.get("terminalId");
                if (terminalId == null || terminalId.isEmpty()) terminalId = "TERM-001";

                synchronized (CARD_LOCK) {
                    CardTerminal terminal = getTerminal(map.get("reader"));
                    if (!terminal.isCardPresent()) {
                        sendJsonResponse(exchange, 400, "{\"error\":\"No card on reader\"}");
                        return;
                    }
                    Card card = terminal.connect("*");
                    try {
                        CardChannel ch = card.getBasicChannel();

                        // GET UID
                        ResponseAPDU rUid = ch.transmit(new CommandAPDU(new byte[]{(byte)0xFF,(byte)0xCA,0x00,0x00,0x00}));
                        byte[] uid = rUid.getData();
                        String uidHex = bytesToHex(uid);

                        // Read pages 0..16 WITHOUT auth
                        StringBuilder pages = new StringBuilder("[");
                        for (int p = 0; p <= 16; p++) {
                            ResponseAPDU r = ch.transmit(new CommandAPDU(new byte[]{(byte)0xFF,(byte)0xB0,0x00,(byte)p,0x04}));
                            if (p > 0) pages.append(",");
                            pages.append(String.format("{\"page\":%d,\"sw\":\"%04X\",\"data\":\"%s\"}", p, r.getSW(), bytesToHex(r.getData())));
                        }
                        pages.append("]");

                        // Attempt PWD_AUTH if masterKey provided
                        String authResult = "SKIPPED";
                        String packHex = "";
                        if (masterKeyHex != null && merchantIdStr != null) {
                            try {
                                byte[] derivedPwdPack = computeHmacSha256(
                                    hexToBytes(masterKeyHex),
                                    ("PWD:" + merchantIdStr + ":" + terminalId).getBytes(StandardCharsets.UTF_8));
                                byte[] pwd = new byte[]{derivedPwdPack[0], derivedPwdPack[1], derivedPwdPack[2], derivedPwdPack[3]};
                                byte[] pack = new byte[]{derivedPwdPack[4], derivedPwdPack[5]};

                                byte[] authApdu = new byte[]{(byte)0xFF,(byte)0x00,(byte)0x00,(byte)0x00,0x05,0x1B,pwd[0],pwd[1],pwd[2],pwd[3]};
                                ResponseAPDU rAuth = ch.transmit(new CommandAPDU(authApdu));
                                packHex = bytesToHex(rAuth.getData());
                                String swHex = String.format("%04X", rAuth.getSW());

                                if (rAuth.getSW() == 0x9000) {
                                    byte[] gotPack = rAuth.getData();
                                    boolean packMatch = gotPack != null && gotPack.length >= 2 && gotPack[0] == pack[0] && gotPack[1] == pack[1];
                                    authResult = packMatch ? "AUTH_OK_PACK_MATCH" : "AUTH_OK_PACK_MISMATCH (got=" + packHex + " expected=" + bytesToHex(pack) + ")";
                                } else {
                                    authResult = "AUTH_FAIL SW=" + swHex;
                                }
                            } catch (Exception ae) {
                                authResult = "AUTH_EXCEPTION: " + ae.getMessage();
                            }
                        }

                        // Read CFG pages (NTAG213: 41-44)
                        StringBuilder cfgPages = new StringBuilder("[");
                        for (int p = 41; p <= 44; p++) {
                            ResponseAPDU r = ch.transmit(new CommandAPDU(new byte[]{(byte)0xFF,(byte)0xB0,0x00,(byte)p,0x04}));
                            if (p > 41) cfgPages.append(",");
                            cfgPages.append(String.format("{\"page\":%d,\"sw\":\"%04X\",\"data\":\"%s\"}", p, r.getSW(), bytesToHex(r.getData())));
                        }
                        cfgPages.append("]");

                        String json = String.format(
                            "{\"uid\":\"%s\",\"cardNumberDec\":\"%s\",\"authResult\":\"%s\",\"pack\":\"%s\",\"pages\":%s,\"cfgPages\":%s}",
                            uidHex, toDecimalString(uid), escapeJson(authResult), packHex, pages, cfgPages);
                        sendJsonResponse(exchange, 200, json);
                    } finally {
                        card.disconnect(false);
                    }
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static String explainSw(int sw, String operation, int page) {
        String swHex = String.format("%04X", sw);
        switch (sw) {
            case 0x9000:
                return "OK (Success)";
            case 0x6300:
                return "Card NAK / Auth failed / Write failed (SW=6300)";
            case 0x6982:
                return "Security status not satisfied (SW=6982). Page " + page + " is password-protected.";
            case 0x6986:
                return "Command not allowed (SW=6986). Page " + page + " is read-only or hardware lock bits are set.";
            case 0x6A82:
                return "Page not found (SW=6A82). Page " + page + " exceeds tag memory capacity.";
            case 0x6A86:
                return "Incorrect parameters P1-P2 (SW=6A86).";
            case 0x6800:
                return "Function not supported by card or reader (SW=6800).";
            case 0x6282:
                return "End of memory reached before reading requested bytes (SW=6282).";
            default:
                return "Chip rejected " + operation + " on page " + page + ": SW=" + swHex;
        }
    }

    private static String getHintForSw(int sw, int page) {
        switch (sw) {
            case 0x6982:
                return "Страница " + page + " защищена паролем. Карта уже заблокирована.";
            case 0x6986:
                return "Запись на страницу " + page + " запрещена аппаратными lock-битами (чип заблокирован на запись).";
            case 0x6A82:
                return "Страница " + page + " находится за пределами памяти данного чипа.";
            case 0x6300:
                return "Чип памяти отклонил команду записи (NAK). Проверьте качество контакта с ридером.";
            default:
                return "Ошибка записи в чип (SW=" + String.format("%04X", sw) + ").";
        }
    }

    // ─────────────────────── Cryptography & Conversion Helpers ────────────

    private static byte[] computeHmacSha256(byte[] key, byte[] message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(message);
    }

    private static String toDecimalString(byte[] uidBytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : uidBytes) {
            sb.append(String.format("%02x", b));
        }
        return new java.math.BigInteger(sb.toString(), 16).toString(10);
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("[^0-9A-Fa-f]", "");
        if (hex.length() % 2 != 0) hex = "0" + hex;
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        // Split on commas that are NOT inside a quoted string
        String[] parts = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String part : parts) {
            // Split on first colon that is NOT inside a quoted string
            String[] kv = part.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
            if (kv.length == 2) {
                String k = kv[0].trim().replaceAll("^\"|\"$", "");
                // Strip quotes if present; also handle bare numbers/booleans/null
                String rawV = kv[1].trim();
                String v = rawV.replaceAll("^\"|\"$", "");
                map.put(k, v);
            }
        }
        return map;
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/admin_provisioning_tool.html";
            }
            path = path.replace("..", "").replace("//", "/");
            if (path.startsWith("/")) path = path.substring(1);

            java.io.File file = null;
            java.io.File[] candidates = new java.io.File[]{
                new java.io.File("web", path),
                new java.io.File(path),
                new java.io.File("..", path),
                new java.io.File("../web", path)
            };
            for (java.io.File c : candidates) {
                if (c.exists() && c.isFile()) {
                    file = c;
                    break;
                }
            }

            if (file == null) {
                String notFound = "File not found: " + path;
                byte[] notFoundBytes = notFound.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(404, notFoundBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(notFoundBytes);
                os.close();
                return;
            }

            String contentType = "text/html; charset=utf-8";
            if (path.endsWith(".css")) contentType = "text/css; charset=utf-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=utf-8";
            else if (path.endsWith(".json")) contentType = "application/json; charset=utf-8";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";

            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}
