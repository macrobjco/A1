import java.io.*;
import java.net.*;

public class BulletinBoardServer {
    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Usage: java BulletinBoardServer <port> <b_width> <b_height> <n_width> <n_height> <colors...>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is starting on port " + port + "...");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected!");
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
        }
    }
}