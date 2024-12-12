package chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class ClientService {

	ChatServer chatServer;
	Socket socket;

	DataInputStream dis;
	DataOutputStream dos;

	String clientIP;
	String chatName;

	final String quitCommand = "/quit";
	final String renameCommand = "/rename";
	final String privateMessageCommand = "/to";
	final String getList = "/list";
	final String imageCommand = "/img";
	final String getImageCommand = "/getimg";

	public ClientService(ChatServer chatServer, Socket socket) throws IOException {
		this.chatServer = chatServer;
		this.socket = socket;

		dis = new DataInputStream(socket.getInputStream());
		dos = new DataOutputStream(socket.getOutputStream());

		clientIP = socket.getInetAddress().getHostAddress();

		boolean isUniqueName = false;
		while (!isUniqueName) {
			chatName = dis.readUTF();

			if (chatServer.isChatNameUnique(chatName)) {
				isUniqueName = true;
				chatServer.addClientInfo(this);
				chatServer.sendToAll(this, "[입장] " + chatName);
				send("[" + chatName + "] 채팅 서버 연결 성공");
				receive();
			} else {
				send("닉네임이 중복되었습니다. 다른 이름을 입력해주세요.");
			}
		}
	}

	public void receive() {
		new Thread(() -> {
			try {
				while (true) {
					String message = dis.readUTF();
					if (message.startsWith(renameCommand)) {
						handleRename(message);
					} else if (message.startsWith(privateMessageCommand)) {
						handlePrivateMessage(message);
					} else if (message.startsWith(imageCommand)) {
						handleImageReceive(message);
					} else if (message.startsWith(getImageCommand)) {
						handleGetImage(message);
					} else if (message.startsWith(getList)) {
						send(chatServer.getClientList());
					} else if (message.equals(quitCommand)) {
						break;
					} else {
						String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
						String formattedMessage = "[" + chatName + "](" + timestamp + ") : " + message;
						chatServer.sendToAll(this, formattedMessage);
					}
				}
			} catch (IOException e) {
				System.out.println("클라이언트와의 연결이 끊어졌습니다: " + chatName);
			}
			quit();
		}).start();
	}

	private void handleRename(String message) {
		String[] parts = message.split(" ", 2);
		if (parts.length == 2) {
			String newName = parts[1].trim();
			if (newName.isEmpty()) {
				send("닉네임은 공백일 수 없습니다.");
				return;
			}

			try {
				if (chatServer.isChatNameUnique(newName)) {
					chatServer.updateClientName(this, newName);
					this.chatName = newName;
					send("닉네임이 변경되었습니다: " + chatName);
				} else {
					send("닉네임이 중복되었습니다.");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			send("사용법: /rename 변경닉네임");
		}
	}

	private void handlePrivateMessage(String message) {
		String[] parts = message.split(" ", 3);
		if (parts.length == 3) {
			String recipientName = parts[1];
			String privateMessage = parts[2];
			chatServer.sendPrivateMessage(this, recipientName, privateMessage);
		} else {
			send("사용법: /to 닉네임 메시지");
		}
	}

	private void handleImageReceive(String message) {
		try {
			String[] parts = message.split(" ", 2);
			if (parts.length == 2) {
				String fileName = parts[1].trim();
				if (fileName.isEmpty()) {
					send("파일명이 공백일 수 없습니다.");
					return;
				}

				int fileSize = dis.readInt();
				if (fileSize <= 0) {
					send("유효하지 않은 파일 크기입니다.");
					return;
				}
				byte[] imageData = new byte[fileSize];
				dis.readFully(imageData);

				// 고유한 이미지 ID 생성
				String imageId = UUID.randomUUID().toString();
				chatServer.addImage(imageId, imageData);

				// 모든 클라이언트에게 이미지 전송 알림
				String notification = "[이미지 전송] " + chatName + "님이 이미지를 전송했습니다: " + fileName + " (ID: " + imageId + ")";
				chatServer.sendToAll(this, notification);
			}
		} catch (IOException e) {
			send("이미지 전송 실패: " + e.getMessage());
		}
	}

	private void handleGetImage(String message) {
		String[] parts = message.split(" ", 2);
		if (parts.length == 2) {
			String imageId = parts[1].trim();
			byte[] imageData = chatServer.getImage(imageId);
			if (imageData != null) {
				try {
					dos.writeUTF("/imgdata " + imageId);
					dos.flush();
					dos.writeInt(imageData.length);
					dos.write(imageData);
					dos.flush();
				} catch (IOException e) {
					send("이미지 전송 실패: " + e.getMessage());
				}
			} else {
				send("이미지를 찾을 수 없습니다: " + imageId);
			}
		} else {
			send("사용법: /getimg 이미지ID");
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

	public void quit() {
		chatServer.sendToAll(this, "[퇴장] " + chatName);
		chatServer.removeClientInfo(this);
		close();
	}

	public void close() {
		try {
			dis.close();
			dos.close();
			socket.close();
		} catch (IOException e) {
			System.out.println("종료 중 오류 발생: " + e.getMessage());
		}
	}
}
