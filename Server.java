import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private final int boardWidth = 150, boardHeight = 100, noteWidth = 15, noteHeight = 10;
    private final List<Note> notes = Collections.synchronizedList(new ArrayList<>());
    private final List<Point> pins = Collections.synchronizedList(new ArrayList<>());
    private final List<String> validColors = Arrays.asList("red", "blue", "green", "yellow", "white");

    // Start server: Listen for incoming client connections on specified port
    public void start(int port) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        }
    }

    // Inner class: Handles individual client connections in separate thread
    private class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) { this.socket = socket; }

        // Run: Process client commands until connection closes
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out.println(String.format("BOARD %d %d NOTES %d %d COLORS %s", boardWidth, boardHeight, noteWidth, noteHeight, String.join(",", validColors)));
                
                String line;
                while ((line = in.readLine()) != null) {
                    processCommand(line);
                }
            } catch (IOException e) {
            } finally {
                try { socket.close(); } catch (IOException e) {}
            }
        }

        // Process command: Parse and execute client request (GET, POST, PIN, etc.)
        private void processCommand(String request) {
            if (request == null || request.trim().isEmpty()) return;
            String trimmed = request.trim();

            // FIXED: Use split limit to preserve spaces in message
            if (trimmed.toUpperCase().startsWith("POST ")) {
                handlePost(trimmed.split(" ", 5)); 
                return;
            }

            String[] args = trimmed.split("\\s+");
            String command = args[0].toUpperCase();

            synchronized (notes) {
                try {
                    switch (command) {
                        case "GET": handleGet(args); break;
                        case "PIN": handlePin(args); break;
                        case "UNPIN": handleUnpin(args); break;
                        case "SHAKE":
                            notes.removeIf(n -> !isPinned(n));
                            out.println("OK SHAKE_COMPLETE");
                            break;
                        case "CLEAR":
                            notes.clear(); pins.clear();
                            out.println("OK BOARD_CLEARED");
                            break;
                        case "DISCONNECT":
                            out.println("OK DISCONNECTING");
                            break;
                        default: out.println("ERROR BAD_SYNTAX");
                    }
                } catch (Exception e) { out.println("ERROR BAD_SYNTAX"); }
            }
        }

        // Handle POST: Add a new note with validation (bounds, color, overlap)
        private void handlePost(String[] args) {
            if (args.length < 5) { out.println("ERROR BAD_SYNTAX"); return; }
            try {
                int x = Integer.parseInt(args[1]);
                int y = Integer.parseInt(args[2]);
                String color = args[3].toLowerCase();
                String message = args[4]; // Preserves spaces

                // FIXED: Complete Overlap Check
                for (Note n : notes) {
                    if (n.x == x && n.y == y) {
                        out.println("ERROR COMPLETE_OVERLAP Note exists at " + x + " " + y);
                        return;
                    }
                }

                if (x < 0 || y < 0 || x + noteWidth > boardWidth || y + noteHeight > boardHeight) {
                    out.println("ERROR OUT_OF_BOUNDS");
                    return;
                }
                if (!validColors.contains(color)) {
                    out.println("ERROR COLOR_NOT_SUPPORTED");
                    return;
                }

                notes.add(new Note(x, y, color, message));
                out.println("OK NOTE_POSTED");
            } catch (Exception e) { out.println("ERROR BAD_SYNTAX"); }
        }

        // Handle GET: Return count and details of all notes on board
        private void handleGet(String[] args) {
            // Simplified GET for stability
            out.println("OK " + notes.size());
            for (Note n : notes) {
                out.println(String.format("NOTE %d %d %s %s PINNED=%b", n.x, n.y, n.color, n.msg, isPinned(n)));
            }
        }

        // Handle PIN: Mark a coordinate as pinned (protects note from SHAKE removal)
        private void handlePin(String[] args) {
            int px = Integer.parseInt(args[1]), py = Integer.parseInt(args[2]);
            boolean hit = false;
            for (Note n : notes) if (px >= n.x && px <= n.x + noteWidth && py >= n.y && py <= n.y + noteHeight) hit = true;
            if (hit) { pins.add(new Point(px, py)); out.println("OK PIN_ADDED"); }
            else out.println("ERROR NO_NOTE_AT_COORDINATE");
        }

        // Handle UNPIN: Remove pin protection from a coordinate
        private void handleUnpin(String[] args) {
            int px = Integer.parseInt(args[1]), py = Integer.parseInt(args[2]);
            boolean removed = pins.removeIf(p -> p.x == px && p.y == py);
            out.println(removed ? "OK" : "ERROR PIN_NOT_FOUND");
        }

        // Is pinned: Check if note overlaps with any pinned coordinate
        private boolean isPinned(Note n) {
            for (Point p : pins) if (p.x >= n.x && p.x <= n.x + noteWidth && p.y >= n.y && p.y <= n.y + noteHeight) return true;
            return false;
        }
    }
    public static void main(String[] args) throws IOException { new Server().start(4554); }
}

class Note { int x, y; String color, msg; Note(int x, int y, String c, String m) { this.x=x; this.y=y; this.color=c; this.msg=m; } }
class Point { int x, y; Point(int x, int y) { this.x=x; this.y=y; } }