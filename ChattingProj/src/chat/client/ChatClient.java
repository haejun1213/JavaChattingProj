package chat.client;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ChatClient {
	String chatName;
	Socket socket;

	DataInputStream dis;
	DataOutputStream dos;

	final String quitCommand = "/quit";
	final String renameCommand = "/rename";
	final String privateMessageCommand = "/to";
	final String imageCommand = "/img";
	final String getImageCommand = "/getimg";
	
	private List<String> messageHistory = new ArrayList<>(); 

    public void addMessageToHistory(String message) {
        messageHistory.add(message);
    }
    
    public void saveChatHistory() {
        try {
            File chatDir = new File("./downloads/chat");
            if (!chatDir.exists()) {
                chatDir.mkdirs();
            }
            String fileName = "./downloads/chat/chat_history.txt";
            FileWriter fileWriter = new FileWriter(fileName, true);
            BufferedWriter writer = new BufferedWriter(fileWriter);
            
            for (String message : messageHistory) {
                writer.write(message);
                writer.newLine();
            }
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String currentTime = LocalDateTime.now().format(formatter);
            writer.write("--------------------------------------------------(" + currentTime + ")--------------------------------------------------\n");
            
            
            writer.close();
            System.out.println("대화 내용이 저장되었습니다.");
        } catch (IOException e) {
            System.out.println("대화 내용 저장 실패: " + e.getMessage());
        }
    }


	public void connect(String serverIP, int portNo, String chatName) {
		try {
			socket = new Socket(serverIP, portNo);
			dis = new DataInputStream(socket.getInputStream());
			dos = new DataOutputStream(socket.getOutputStream());

			this.chatName = chatName;
			send(chatName);

		} catch (IOException e) {
			System.out.println("서버에 연결할 수 없습니다: " + e.getMessage());
			System.exit(1);
		}
	}


	public void send(String message) {
		try {
			dos.writeUTF(message);
			dos.flush();
		} catch (IOException e) {
			System.out.println("메시지 전송 실패: " + e.getMessage());
		}
	}
	
	public void receive(Scanner sc) {
        new Thread(() -> {
            try {
                while (true) {
                    String message = dis.readUTF();
                    addMessageToHistory(message);

                    if (message.startsWith("[이미지 전송]")) {
                        System.out.println(message);
                        System.out.println("이미지를 다운로드하려면 '/getimg 이미지 ID'를 입력하세요.");
                        System.out.print("> ");
                    } else if (message.startsWith("/imgdata ")) {
                        String[] parts = message.split(" ", 2);
                        if (parts.length == 2) {
                            String imageId = parts[1].trim();
                            int size = dis.readInt(); 
                            byte[] imageData = new byte[size];
                            dis.readFully(imageData);
                            saveImage(imageId, imageData);
                        }
                    } else {
                        System.out.println(message);
                        System.out.print("> ");
                    }
                }
            } catch (IOException e) {
                System.out.println("서버와의 연결이 끊어졌습니다.");
                quit();
                System.exit(0);
            }
        }).start();
    }

	private void saveImage(String imageId, byte[] imageData) {
		try {
			String downloadsDir = "downloads";
			File dir = new File(downloadsDir);
			if (!dir.exists()) {
				dir.mkdirs();
			}

			String fileName = "image_" + imageId + ".jpg";
			File file = new File(dir, fileName);

			FileOutputStream fos = new FileOutputStream(file);
			fos.write(imageData);
			fos.close();

			System.out.println("이미지를 다운로드 받았습니다: " + file.getAbsolutePath());
			System.out.print("> ");
		} catch (IOException e) {
			System.out.println("이미지 저장 실패: " + e.getMessage());
			System.out.print("> ");
		}
	}

	public void quit() {
		try {
			if (dis != null)
				dis.close();
			if (dos != null)
				dos.close();
			if (socket != null && !socket.isClosed())
				socket.close();
		} catch (IOException e) {
			System.out.println("종료 중 오류 발생: " + e.getMessage());
		}
	}

	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);

		System.out.print("서버 IP를 입력하세요 (기본값: localhost): ");
		String serverIP = sc.nextLine().trim();
		if (serverIP.isEmpty()) {
			serverIP = "localhost";
		}

		System.out.print("서버 포트 번호를 입력하세요 (기본값: 50005): ");
		String portInput = sc.nextLine().trim();
		int portNo = 50005;
		if (!portInput.isEmpty()) {
			try {
				portNo = Integer.parseInt(portInput);
			} catch (NumberFormatException e) {
				System.out.println("포트 번호가 유효하지 않습니다. 기본 포트 50005 사용.");
			}
		}

		System.out.print("대화명을 입력하세요 : ");
		String chatName = sc.nextLine().trim();

		if (chatName.isEmpty()) {
			System.out.println("대화명은 공백일 수 없습니다.");
			sc.close();
			return;
		}

		ChatClient chatClient = new ChatClient();
		chatClient.connect(serverIP, portNo, chatName);
		chatClient.receive(sc);

		while (true) {
			System.out.print("> ");
			String message = sc.nextLine().trim();
			chatClient.addMessageToHistory(message);
			if (message.isEmpty())
				continue;

			if (message.startsWith(chatClient.renameCommand)) {
				chatClient.send(message);
			} else if (message.startsWith(chatClient.privateMessageCommand)) {
				chatClient.send(message);
			} else if (message.startsWith(chatClient.imageCommand)) {
				String[] parts = message.split(" ", 2);
				if (parts.length == 2) {
					String filePath = parts[1].trim();
					if (!filePath.isEmpty()) {
						chatClient.sendFile(filePath);
					} else {
						System.out.println("파일명을 입력해주세요.");
					}
				} else {
					System.out.println("사용법: /img 전송할파일명");
				}
			} else if (message.startsWith(chatClient.getImageCommand)) {
				chatClient.send(message);
			} else if (message.startsWith("/save")) {
				chatClient.saveChatHistory();
			} else {
				chatClient.send(message);
			}

			if (message.equals(chatClient.quitCommand))
				break;
		}

		chatClient.quit();
		sc.close();
	}

	public void sendFile(String filePath) {
		try {
			File file = new File(filePath);
			if (!file.exists()) {
				System.out.println("파일이 존재하지 않습니다: " + filePath);
				return;
			}

			String fileName = file.getName();

			dos.writeUTF("/img " + fileName);
			dos.flush();

			int fileSize = (int) file.length();
			dos.writeInt(fileSize);
			dos.flush();

			FileInputStream fis = new FileInputStream(file);
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = fis.read(buffer)) != -1) {
				dos.write(buffer, 0, bytesRead);
			}
			fis.close();
			dos.flush();

			System.out.println("이미지 전송 완료: " + filePath);

		} catch (IOException e) {
			System.out.println("이미지 전송 실패: " + e.getMessage());
		}
	}
	
	
}
