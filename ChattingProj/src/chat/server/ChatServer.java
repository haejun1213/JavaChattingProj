package chat.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    final String quitCommand = "quit";
    ServerSocket serverSocket;
    Map<String, ClientService> chatClientInfo = new ConcurrentHashMap<>();

    Map<String, byte[]> imageStore = new ConcurrentHashMap<>();

    public void start(int portNo) {
        try {
            serverSocket = new ServerSocket(portNo);
            System.out.println("[채팅서버] 시작 (" + InetAddress.getLocalHost() + ":" + portNo + ")");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void connectClient() {
        Thread thread = new Thread(() -> {
            try {
                while (true) {
                    Socket socket = serverSocket.accept();
                    new ClientService(this, socket);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        try {
            serverSocket.close();
            System.out.println("[채팅서버] 종료");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized boolean addClient(ClientService clientService) {
        if (chatClientInfo.containsKey(clientService.chatName)) {
            return false;
        }
        chatClientInfo.put(clientService.chatName, clientService);
        return true;
    }

    public synchronized void updateClientName(ClientService clientService, String newName) {
        chatClientInfo.remove(clientService.chatName);
        clientService.chatName = newName;
        chatClientInfo.put(newName, clientService);
        System.out.println("[변경] " + clientService.chatName + "(채팅 참여자 수 : " + chatClientInfo.size() + ")");
    }


    
    public synchronized boolean isChatNameUnique(String chatName) {
        return !chatClientInfo.containsKey(chatName);
    }

    public synchronized void addClientInfo(ClientService clientService) {
        chatClientInfo.put(clientService.chatName, clientService);
        System.out.println("[입장] " + clientService.chatName + "(채팅 참여자 수 : " + chatClientInfo.size() + ")");
    }

    public synchronized void sendToAll(ClientService clientService, String message) {
        for (ClientService cs : chatClientInfo.values()) {
        	if(cs != clientService)
        		cs.send(message);
        }
    }

    public synchronized void sendPrivateMessage(ClientService clientService, String recipientName, String message) {
        ClientService recipient = chatClientInfo.get(recipientName);
        if (recipient != null) {
            String formattedMessage = "[" + clientService.chatName + " -> " + recipientName + "] : " + message;
            recipient.send(formattedMessage);
        } else {
        	 for (ClientService cs : chatClientInfo.values()) {
             	if(cs == clientService)
             		cs.send("상대방이 오프라인입니다.");
             }
        }
    }

    public synchronized void removeClientInfo(ClientService clientService) {
        chatClientInfo.remove(clientService.chatName);
        System.out.println("[퇴장] " + clientService.chatName + "(채팅 참여자 수 : " + chatClientInfo.size() + ")");
    }

    public void addImage(String imageId, byte[] imageData) {
        imageStore.put(imageId, imageData);
    }

    public byte[] getImage(String imageId) {
        return imageStore.get(imageId);
    }

    public static void main(String[] args) {

        final int portNo = 50005;

        ChatServer chatServer = new ChatServer();

        chatServer.start(portNo);
        chatServer.connectClient();

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("서버를 종료하려면 quit을 입력하고 Enter를 치세요");
            String command = sc.nextLine();
            if (command.equalsIgnoreCase(chatServer.quitCommand))
                break;
        }

        chatServer.stop();
    }

	public String getClientList() {
		return "온라인 목록 : " + chatClientInfo.keySet().toString();
	}
}
