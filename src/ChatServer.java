import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345; // 서버가 사용할 포트 번호
    private static Set<ClientHandler> clientHandlers = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        System.out.println("서버가 시작되었습니다. 포트: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("새 클라이언트 연결됨: " + clientSocket.getInetAddress());

                    ClientHandler handler = new ClientHandler(clientSocket);
                    clientHandlers.add(handler);
                    new Thread(handler).start();
                } catch (IOException e) {
                    System.err.println("클라이언트 연결 오류: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("서버 소켓 오류: " + e.getMessage());
        } finally {
            System.out.println("서버가 종료되었습니다.");
        }
    }

    // 모든 클라이언트에게 메시지 브로드캐스트
    public static void broadcast(String message, ClientHandler sender) {
        synchronized (clientHandlers) {
            System.out.println("브로드캐스트 메시지: " + message); // 디버깅용 출력
            for (ClientHandler handler : clientHandlers) {
                if (handler != sender) { // 보낸 사람 제외
                    handler.sendMessage(message);
                }
            }
        }
    }

    // 클라이언트 핸들러 클래스
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("수신된 메시지: " + message);

                    if (message.startsWith("POST:")) {
                        String postContent = message.substring(5); // POST: 이후 게시글 내용 추출

                        if (isValidPostMessage(postContent)) {
                            broadcast("[게시글]" + postContent, this);
                        } else {
                            System.err.println("잘못된 POST 메시지 형식: " + postContent);
                        }
                    }
                }
            } catch (IOException e) {
                if ("Connection reset".equals(e.getMessage())) {
                    System.err.println("클라이언트가 연결을 종료했습니다.");
                } else {
                    System.err.println("클라이언트 통신 오류: " + e.getMessage());
                }
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("소켓 닫기 오류: " + e.getMessage());
                }
                clientHandlers.remove(this);
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        private boolean isValidPostMessage(String postContent) {
            String[] parts = postContent.split(";");
            return parts.length == 3 && !parts[0].isEmpty() && !parts[1].isEmpty(); // 제목과 내용만 필수
        }
    }
}