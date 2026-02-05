import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.*;

public class Client extends JFrame {
    private JTextField ipF = new JTextField("127.0.0.1", 8), portF = new JTextField("4554", 4);
    private JTextField xF = new JTextField("0", 3), yF = new JTextField("0", 3), msgF = new JTextField("", 10);
    private JComboBox<String> colors = new JComboBox<>(new String[]{"red", "blue", "green", "yellow", "white"});
    private JTextField fCol = new JTextField(5), fX = new JTextField(3), fY = new JTextField(3), fSub = new JTextField(10);
    private JTextArea log = new JTextArea(8, 45);
    private BoardPanel board = new BoardPanel();
    private PrintWriter out; 
    private BufferedReader in;

    public Client() {
        super("CP372 Bulletin Board - Bryce & Caleb");
        setupUI();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack(); setVisible(true);
    }

    private void setupUI() {
        setLayout(new BorderLayout());
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createTitledBorder("CP372 A1 Controls"));

        JPanel connP = new JPanel();
        connP.add(new JLabel("Port:")); connP.add(portF);
        JButton cBtn = new JButton("CONNECT");
        cBtn.addActionListener(e -> connect());
        connP.add(cBtn);
        side.add(connP);

        JPanel postArea = new JPanel(new GridLayout(0, 1));
        postArea.setBorder(BorderFactory.createTitledBorder("Post / Pin"));
        postArea.add(new JLabel("Pos (X,Y):"));
        JPanel pIn = new JPanel(); pIn.add(xF); pIn.add(yF); postArea.add(pIn);
        postArea.add(new JLabel("Message:")); postArea.add(msgF);
        postArea.add(colors);
        JButton postB = new JButton("POST NOTE");
        postB.addActionListener(e -> send("POST " + xF.getText() + " " + yF.getText() + " " + colors.getSelectedItem() + " " + msgF.getText()));
        postArea.add(postB);
        
        JPanel pinRow = new JPanel(new GridLayout(1, 2));
        JButton pinB = new JButton("PIN"); pinB.addActionListener(e -> send("PIN " + xF.getText() + " " + yF.getText()));
        JButton unB = new JButton("UNPIN"); unB.addActionListener(e -> send("UNPIN " + xF.getText() + " " + yF.getText()));
        pinRow.add(pinB); pinRow.add(unB);
        postArea.add(pinRow);
        side.add(postArea);

        JPanel getArea = new JPanel(new GridLayout(0, 1));
        getArea.setBorder(BorderFactory.createTitledBorder("Search Filters"));
        getArea.add(new JLabel("Color:")); getArea.add(fCol);
        getArea.add(new JLabel("Contains (X Y):")); 
        JPanel cIn = new JPanel(); cIn.add(fX); cIn.add(fY); getArea.add(cIn);
        getArea.add(new JLabel("refersTo:")); getArea.add(fSub);
        
        JButton filterBtn = new JButton("RUN FILTERED GET");
        filterBtn.addActionListener(e -> {
            StringBuilder cmd = new StringBuilder("GET");
            if (!fCol.getText().isEmpty()) cmd.append(" color=").append(fCol.getText());
            if (!fX.getText().isEmpty() && !fY.getText().isEmpty()) 
                cmd.append(" contains=").append(fX.getText()).append(" ").append(fY.getText());
            if (!fSub.getText().isEmpty()) cmd.append(" refersTo=").append(fSub.getText());
            refresh(cmd.toString(), true);
        });
        getArea.add(filterBtn);
        side.add(getArea);

        JButton shakeB = new JButton("SHAKE"); shakeB.addActionListener(e -> send("SHAKE"));
        JButton clearB = new JButton("CLEAR"); clearB.addActionListener(e -> send("CLEAR"));
        side.add(shakeB); side.add(clearB);

        add(new JScrollPane(board), BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
        log.setEditable(false); log.setBackground(Color.BLACK); log.setForeground(Color.GREEN);
        add(new JScrollPane(log), BorderLayout.SOUTH);
    }

    private void connect() {
        try {
            Socket s = new Socket(ipF.getText(), Integer.parseInt(portF.getText()));
            out = new PrintWriter(s.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            log.append("Server: " + in.readLine() + "\n");
            out.println("SHAKE");
            log.append("Server: " + in.readLine() + "\n");
            refresh("GET", false);
        } catch (Exception e) { log.append("Connection Failed\n"); }
    }

    private void send(String cmd) {
        if (out == null) return;
        out.println(cmd);
        try { 
            String resp = in.readLine();
            log.append("Server: " + resp + "\n");
            refresh("GET", false);
        } catch (Exception e) {}
    }

    private void refresh(String getCmd, boolean showInLog) {
        if (out == null) return;
        out.println(getCmd);
        try {
            String line = in.readLine();
            if (showInLog) log.append("Server: " + line + "\n"); 
            if (line == null || !line.startsWith("OK")) return;
            
            int count = Integer.parseInt(line.split(" ")[1]);
            java.util.List<NoteInfo> tempNotes = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String noteLine = in.readLine();
                if (showInLog) log.append("  " + noteLine + "\n"); 
                int pinnedIdx = noteLine.lastIndexOf(" PINNED=");
                if (pinnedIdx < 0) throw new IllegalArgumentException("Missing PINNED");

                String left = noteLine.substring(0, pinnedIdx);        // "NOTE x y color message..."
                String pinnedPart = noteLine.substring(pinnedIdx + 1); // "PINNED=true/false"

                // Split left into 5 pieces max so message stays intact (with spaces)
                String[] head = left.split(" ", 5); // NOTE, x, y, color, message(with spaces)

                int x = Integer.parseInt(head[1]);
                int y = Integer.parseInt(head[2]);
                String col = head[3];
                String msg = (head.length >= 5) ? head[4] : "";

                boolean p = pinnedPart.split("=", 2)[1].equalsIgnoreCase("true");

                tempNotes.add(new NoteInfo(x, y, col, msg, p));
            }

            out.println("GET PINS");
            String pinLine = in.readLine();
            java.util.List<Point> tempPins = new ArrayList<>();
            if (pinLine != null && pinLine.startsWith("OK")) {
                int pCount = Integer.parseInt(pinLine.split(" ")[1]);
                for (int i = 0; i < pCount; i++) {
                    String[] pD = in.readLine().split(" ");
                    tempPins.add(new Point(Integer.parseInt(pD[1]), Integer.parseInt(pD[2])));
                }
            }
            board.updateBoard(tempNotes, tempPins);
        } catch (Exception e) {}
    }

    class BoardPanel extends JPanel {
        private java.util.List<NoteInfo> notes = new ArrayList<>();
        private java.util.List<Point> pins = new ArrayList<>();
        public BoardPanel() { setPreferredSize(new Dimension(850, 650)); setBackground(Color.WHITE); }
        public void updateBoard(java.util.List<NoteInfo> n, java.util.List<Point> p) { 
            this.notes = n; this.pins = p; repaint(); 
        }
        
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int S = 5; int O = 60; 
            g.setFont(new Font("Arial", Font.PLAIN, 10));
            g.setColor(Color.DARK_GRAY);
            for(int i=0; i<=150; i+=10) { g.drawLine(O + i*S, O-5, O + i*S, O); g.drawString(""+i, O+i*S-5, O-10); }
            for(int j=0; j<=100; j+=10) { g.drawLine(O-5, O + j*S, O, O + j*S); g.drawString(""+j, O-30, O+j*S+5); }
            
            g.setColor(Color.BLACK);
            g.drawRect(O, O, 150 * S, 100 * S);

            for (NoteInfo n : notes) {
                g.setColor(getColor(n.c));
                int nx = O + n.x * S; int ny = O + n.y * S;
                g.fillRect(nx, ny, 15 * S, 10 * S);
                g.setColor(Color.BLACK);
                g.drawRect(nx, ny, 15 * S, 10 * S);
                g.drawString(n.m, nx + 5, ny + 25);
            }
            for (Point p : pins) {
                g.setColor(Color.RED);
                g.fillOval(O + p.x * S - 4, O + p.y * S - 4, 8, 8);
                g.setColor(Color.BLACK);
                g.drawOval(O + p.x * S - 4, O + p.y * S - 4, 8, 8);
            }
        }
        private Color getColor(String c) {
            switch(c.toLowerCase()){
                case "red": return Color.RED; case "blue": return Color.BLUE;
                case "yellow": return Color.YELLOW; case "green": return Color.GREEN;
                default: return Color.WHITE;
            }
        }
    }
    record NoteInfo(int x, int y, String c, String m, boolean p) {}
    public static void main(String[] args) { new Client(); }
}
