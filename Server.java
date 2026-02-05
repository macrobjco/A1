import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private final int bW = 150, bH = 100, nW = 15, nH = 10;
    private final List<Note> notes = Collections.synchronizedList(new ArrayList<>());
    private final List<Pin> pins = Collections.synchronizedList(new ArrayList<>());
    private final List<String> validColors = Arrays.asList("red", "blue", "green", "yellow", "white");
    private String noteKey(Note n) { return n.x + "," + n.y; }

    public void start(int port) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server active on port " + port);
            while (true) { new ClientHandler(serverSocket.accept()).start(); }
        }
    }

    private class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket s) { this.socket = s; }

        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out.println(String.format("BOARD %d %d NOTES %d %d COLORS %s", bW, bH, nW, nH, String.join(",", validColors)));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("SHAKE")) {
                        notes.removeIf(n -> !isNotePinned(n));
                        out.println("OK SHAKE_COMPLETE");
                    } else if (line.equalsIgnoreCase("DISCONNECT")) {
                        out.println("OK DISCONNECTING");
                        break;
                    } else processCommand(line);
                }
            } catch (Exception e) {} finally { try { socket.close(); } catch (IOException e) {} }
        }

        private void processCommand(String raw) {
            String[] args = raw.trim().split("\\s+");
            if (args.length == 0) return;
            String cmd = args[0].toUpperCase();
            synchronized(notes) {
                try {
                    switch(cmd) {
                        case "POST": handlePost(args); break;
                        case "GET": handleGet(args); break;
                        case "PIN": handlePin(args); break;
                        case "UNPIN": handleUnpin(args); break;
                        case "CLEAR": notes.clear(); pins.clear(); out.println("OK BOARD_CLEARED"); break;
                        default: out.println("ERROR UNKNOWN_COMMAND");
                    }
                } catch (Exception e) { out.println("ERROR BAD_SYNTAX - Unknown command or missing arguments"); }
            }
        }

        private void handlePost(String[] args) {
            int x = Integer.parseInt(args[1]), y = Integer.parseInt(args[2]);
            String color = args[3].toLowerCase();
            String msg = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
            if (x < 0 || y < 0 || x + nW > bW || y + nH > bH) { out.println("ERROR OUT_OF_BOUNDS - Note exceeds board boundaries"); return; }
            for (Note existing : notes) {
                if (existing.x == x && existing.y == y) {
                    out.println("ERROR COMPLETE_OVERLAP - Note overlaps an existing note entirely");
                    return;
                }
            }
            notes.add(new Note(x, y, color, msg));
            out.println("OK NOTE_POSTED");
        }

        private void handleGet(String[] args) {
            if (args.length > 1 && args[1].equalsIgnoreCase("PINS")) {
                out.println("OK " + pins.size());
                for (Pin p : pins) out.println("PIN " + p.x + " " + p.y);
                return;
            }

            String fCol = null, fSub = null;
            Integer cx = null, cy = null;

            for (int i = 1; i < args.length; i++) {

                if (args[i].startsWith("color=")) {
                    fCol = args[i].split("=", 2)[1];
                }

                else if (args[i].startsWith("contains=")) {
                    // expects: contains=<x> <y>
                    String[] parts = args[i].split("=", 2);
                    if (parts.length < 2 || i + 1 >= args.length) throw new IllegalArgumentException();

                    cx = Integer.parseInt(parts[1]);      // x inside contains=<x>
                    cy = Integer.parseInt(args[i + 1]);   // y is next token
                    i += 1;                               // consumed y token
                }

                else if (args[i].startsWith("refersTo=")) {
                    // allow spaces: refersTo=<substring with spaces>
                    String first = args[i].split("=", 2).length == 2 ? args[i].split("=", 2)[1] : "";
                    StringBuilder sb = new StringBuilder();
                    sb.append(first);

                    // append following tokens until we hit another filter or end
                    while (i + 1 < args.length
                            && !args[i + 1].startsWith("color=")
                            && !args[i + 1].startsWith("contains=")
                            && !args[i + 1].startsWith("refersTo=")) {
                        sb.append(" ").append(args[i + 1]);
                        i++;
                    }

                    fSub = sb.toString().trim();
                }

                else {
                    // unknown filter
                    throw new IllegalArgumentException();
                }
            }

            List<Note> res = new ArrayList<>();
            for (Note n : notes) {
                boolean match = true;
                if (fCol != null && !n.color.equalsIgnoreCase(fCol)) match = false;
                if (fSub != null && !n.msg.toLowerCase().contains(fSub.toLowerCase())) match = false;
                if (cx != null && cy != null) {
                    if (!(cx >= n.x && cx <= n.x + nW && cy >= n.y && cy <= n.y + nH)) match = false;
                }
                if (match) res.add(n);
            }
            
            out.println("OK " + res.size());
            for (Note n : res) out.println(String.format("NOTE %d %d %s %s PINNED=%b", n.x, n.y, n.color, n.msg, isNotePinned(n)));
        }

        private void handlePin(String[] args) {
            int px = Integer.parseInt(args[1]), py = Integer.parseInt(args[2]);

            // Prevent duplicate pins at the same coordinate
            for (Pin existing : pins) {
                if (existing.x == px && existing.y == py) {
                    out.println("ERROR COMPLETE_OVERLAP - Note overlaps existing note entirely");
                    return;
                }
            }
            Pin pin = new Pin(px, py);
            boolean hit = false;

            for (Note n : notes) {
                if (px >= n.x && px <= n.x + nW && py >= n.y && py <= n.y + nH) {
                    pin.appliesTo.add(noteKey(n)); // only notes that exist RIGHT NOW
                    hit = true;
                }
            }

            if (!hit) {
                out.println("ERROR NO_NOTE_AT_COORDINATE - No note contains this given point");
            } else {
                pins.add(pin);
                out.println("OK PIN_ADDED");
            }
        }

        private void handleUnpin(String[] args) {
            int px = Integer.parseInt(args[1]), py = Integer.parseInt(args[2]);
            boolean removed = pins.removeIf(p -> p.x == px && p.y == py);
            out.println(removed ? "OK" : "ERROR PIN_NOT_FOUND - No pin exists at the given coordinates");
        }

        private boolean isNotePinned(Note n) {
            String k = noteKey(n);
            for (Pin p : pins) {
                if (p.appliesTo.contains(k)) return true;
            }
            return false;
        }
    }
    public static void main(String[] args) throws IOException { new Server().start(4554); }
}

class Note {
    int x, y; String color, msg;
    Note(int x, int y, String c, String m) { this.x=x; this.y=y; this.color=c; this.msg=m; }
}
class Pin {
    int x, y;
    Set<String> appliesTo = new HashSet<>();
    Pin(int x, int y) { this.x = x; this.y = y; }
}
