import java.io.*;
import java.net.*;
import java.util.*;

public class MultiplexedServer {
    private static final int PORT = 9999; // 단일 포트 번호
    private static List<String> posts = new ArrayList<>(); // 게시판 글 저장
    private static Map<String, ClientHandler> clients = new HashMap<>(); // 사용자 ID와 핸들러 매핑

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("멀티플렉싱 서버가 시작되었습니다. 포트: " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("새 클라이언트 연결됨: " + socket.getInetAddress());
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("서버 오류: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private String clientId;

        public ClientHandler(Socket socket) {
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
                // 사용자 ID 요청 및 등록
                writer.println("사용자 ID를 입력하세요:");
                clientId = reader.readLine();

                if (clientId == null || clientId.trim().isEmpty()) {
                    writer.println("유효한 사용자 ID를 입력하세요.");
                    closeConnection();
                    return;
                }

                synchronized (clients) {
                    clients.put(clientId, this); // 사용자 등록
                }

                System.out.println(clientId + " 연결됨.");

                String message;
                while ((message = reader.readLine()) != null) {
                    if (message.startsWith("BOARD")) { // 게시판 관련 요청 처리
                        handleBoardRequest(message);
                    } else if (!message.trim().isEmpty()) { // 빈 메시지 무시
                        writer.println("알 수 없는 명령어입니다.");
                    }
                }
            } catch (IOException e) {
                System.err.println(clientId + " 통신 오류: " + e.getMessage());
            } finally {
                closeConnection();
            }
        }

        private void handleBoardRequest(String message) {
            if (message.startsWith("BOARD POST")) { // 새 게시글 추가 요청 처리
                String postContent = message.substring(11); // "BOARD POST " 이후 내용 추출
                synchronized (posts) {
                    posts.add(postContent); // 게시글 저장
                    broadcastToAll("새 게시글: " + postContent); // 모든 클라이언트에 알림 전송
                    writer.println("SUCCESS"); // 성공 메시지 반환
                }
            } else if (message.equalsIgnoreCase("BOARD GET")) { // 게시글 목록 요청 처리
                synchronized (posts) {
                    for (String post : posts) {
                        writer.println(post); // 클라이언트에 게시글 전송
                    }
                }
            } else {
                writer.println("알 수 없는 게시판 명령어입니다.");
            }
        }

        private void broadcastToAll(String message) {
            synchronized (clients) {
                for (ClientHandler client : clients.values()) {
                    client.sendMessage(message);
                }
            }
        }

        private void closeConnection() {
            try {
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (socket != null) socket.close();

                synchronized (clients) {
                    clients.remove(clientId); // 사용자 제거
                }

                System.out.println(clientId + " 연결 종료");
            } catch (IOException e) {
                System.err.println("연결 종료 오류: " + e.getMessage());
            }
        }

        public void sendMessage(String message) {
            writer.println(message);
        }
    }
}