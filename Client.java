import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.*;

public class Client extends JFrame {
    private JTextField portField = new JTextField("4554", 4);
    private JTextField xField = new JTextField("0", 3), yField = new JTextField("0", 3), msgField = new JTextField(15);
    private JComboBox<String> colorBox = new JComboBox<>(new String[]{"red", "blue", "green", "yellow", "white"});
    private JTextArea logArea = new JTextArea(8, 40);
    private BoardPanel boardPanel = new BoardPanel();
    private PrintWriter out; private BufferedReader in;

    // Constructor: Initialize window, set up UI, and display for Bulletin Board client
    public Client() {
        super("Bulletin Board");
        setupUI();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setVisible(true);
    }

    // Setup UI: Build control panels, board display, and log area
    private void setupUI() {
        setLayout(new BorderLayout());
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        JPanel conn = new JPanel(); conn.add(new JLabel("Port:")); conn.add(portField);
        JButton connectBtn = new JButton("Connect"); connectBtn.addActionListener(e -> connect());
        conn.add(connectBtn); controls.add(conn);
        JPanel post = new JPanel(new GridLayout(0,1));
        post.setBorder(BorderFactory.createTitledBorder("Post Note"));
        post.add(new JLabel("X, Y:"));
        JPanel pos = new JPanel(); pos.add(xField); pos.add(yField); post.add(pos);
        post.add(new JLabel("Message:")); post.add(msgField); post.add(colorBox);
        JButton postBtn = new JButton("POST"); postBtn.addActionListener(e -> send("POST " + xField.getText() + " " + yField.getText() + " " + colorBox.getSelectedItem() + " " + msgField.getText()));
        post.add(postBtn); controls.add(post);

        JButton clearBtn = new JButton("Clear Board"); clearBtn.addActionListener(e -> send("CLEAR"));
        controls.add(clearBtn);

        add(new JScrollPane(boardPanel), BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
        logArea.setBackground(Color.BLACK); logArea.setForeground(Color.GREEN);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);
    }

    // Connect to server: Establish socket connection and fetch initial board state
    private void connect() {
        try {
            Socket s = new Socket("127.0.0.1", Integer.parseInt(portField.getText()));
            out = new PrintWriter(s.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            logArea.append("Connected: " + in.readLine() + "\n");
            refresh();
        } catch (Exception e) { logArea.append("Connection failed\n"); }
    }

    // Send command: Send command to server, log response, refresh if successful
    private void send(String cmd) {
        if (out == null) return;
        out.println(cmd);
        try {
            String resp = in.readLine();
            logArea.append("Server: " + resp + "\n");
            if (resp != null && resp.startsWith("OK")) refresh();
        } catch (Exception e) {}
    }

    // Refresh board: Request current notes from server and update display
    private void refresh() {
        if (out == null) return;
        out.println("GET");
        try {
            String line = in.readLine();
            if (line == null || !line.startsWith("OK")) return;
            int count = Integer.parseInt(line.split(" ")[1]);
            java.util.List<NoteData> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String n = in.readLine();
                // FIXED: Split with limit 6 to keep the message and the PINNED flag separate
                String[] d = n.split(" ", 6); 
                list.add(new NoteData(Integer.parseInt(d[1]), Integer.parseInt(d[2]), d[3], d[4]));
            }
            boardPanel.update(list);
        } catch (Exception e) {}
    }

    // Inner class: Renders the bulletin board with notes
    class BoardPanel extends JPanel {
        private java.util.List<NoteData> notes = new ArrayList<>();
        // Constructor: Set panel size and background
        public BoardPanel() { setPreferredSize(new Dimension(750, 500)); setBackground(Color.WHITE); }
        // Update: Store new notes and trigger redraw
        public void update(java.util.List<NoteData> n) { this.notes = n; repaint(); }
        // Paint component: Draw board border and all notes with colors and text
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int scale = 5; int off = 20;
            g.drawRect(off, off, 150*scale, 100*scale);
            for (NoteData n : notes) {
                g.setColor(getColor(n.c));
                g.fillRect(off+n.x*scale, off+n.y*scale, 15*scale, 10*scale);
                g.setColor(Color.BLACK);
                g.drawRect(off+n.x*scale, off+n.y*scale, 15*scale, 10*scale);
                // The message will now render with spaces
                g.drawString(n.m, off+n.x*scale + 5, off+n.y*scale + 20);
            }
        }
        // Get color: Convert color name string to Color object
        private Color getColor(String c) {
            if (c.equals("red")) return Color.RED; if (c.equals("blue")) return Color.BLUE;
            if (c.equals("green")) return Color.GREEN; if (c.equals("yellow")) return Color.YELLOW;
            return Color.WHITE;
        }
    }
    record NoteData(int x, int y, String c, String m) {}
    public static void main(String[] args) { new Client(); }
}