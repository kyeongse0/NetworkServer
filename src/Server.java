import java.io.*;
import java.net.*;
import java.util.*;

// 서버 클래스
public class Server {
    private static final int PORT = 9999; // 서버 포트 번호
    private static Set<ClientHandler> clientHandlers = new HashSet<>(); // 연결된 클라이언트 관리
    private static List<String> posts = new ArrayList<>(); // 게시판 글 저장

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("서버가 시작되었습니다. 포트: " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("새 클라이언트 연결됨: " + socket.getInetAddress());
                ClientHandler clientHandler = new ClientHandler(socket);
                clientHandlers.add(clientHandler);
                new Thread(clientHandler).start(); // 클라이언트를 별도 스레드에서 처리
            }
        } catch (IOException e) {
            System.err.println("서버 오류: " + e.getMessage());
        }
    }

    // 게시글 추가 메서드
    public static synchronized void addPost(String post) {
        posts.add(post);
        broadcast("새 게시글: " + post); // 모든 클라이언트에 알림
    }

    // 모든 클라이언트에 메시지 전송
    public static synchronized void broadcast(String message) {
        for (ClientHandler client : clientHandlers) {
            client.sendMessage(message);
        }
    }

    // 특정 발신자를 제외한 모든 클라이언트에 메시지 전송 (채팅용)
    public static synchronized void broadcastToOthers(String message, ClientHandler sender) {
        for (ClientHandler client : clientHandlers) {
            if (client != sender) { // 발신자를 제외한 다른 클라이언트에게만 메시지 전송
                client.sendMessage(message);
            }
        }
    }

    // 클라이언트 핸들러 클래스
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private String clientId; // 클라이언트 ID

        public ClientHandler(Socket socket) {
            this.clientId = "Someone";
            this.socket = socket;
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                System.err.println("클라이언트 핸들러 초기화 오류: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                writer.println("서버에 연결되었습니다. 채팅을 시작헤요.");
                String message;

                while ((message = reader.readLine()) != null) {
                    if (message.isEmpty()) {
                        // 빈 메시지는 무시
                        continue;
                    }

                    if (message.startsWith("POST")) { // 게시글 작성 명령어
                        String postContent = message.substring(5).trim();
                        addPost(postContent);

                    } else if (message.startsWith("CHAT")) { // 채팅 명령어
                        String chatMessage = message.substring(5).trim();
                        broadcastToOthers(clientId + ": " + chatMessage, this);

                    } else if (message.equalsIgnoreCase("EXIT")) { // 종료 명령어
                        break;

                    } else {
                        continue;
                    }
                }
            } catch (IOException e) {
                System.err.println("클라이언트 통신 오류: " + e.getMessage());
            } finally {
                closeConnection(); // 예외 발생 시에도 연결 종료 처리
            }
        }

        // 클라이언트와의 연결 종료 메서드
        private void closeConnection() {
            try {
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (socket != null) socket.close();
                clientHandlers.remove(this);
                System.out.println("클라이언트 연결 종료");
            } catch (IOException e) {
                System.err.println("연결 종료 오류: " + e.getMessage());
            }
        }

        // 메시지 전송 메서드
        public void sendMessage(String message) {
            writer.println(message);
        }
    }
}
