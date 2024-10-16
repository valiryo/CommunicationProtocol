import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements Runnable {
    private ArrayList<ConectionHandler> connections;  // Lista de conexões de clientes
    private ServerSocket server;  // Socket do servidor
    private boolean done;  // Indicador de estado do servidor
    private ExecutorService pool;  // Pool de threads para gerenciar os clientes
    private Scanner sc;  // Scanner para entrada do usuário

    public Server() {
        sc = new Scanner(System.in);  // Inicializa o Scanner
        connections = new ArrayList<>();  // Inicializa a lista de conexões
        messageHistory = new ArrayList<>();  // Inicializa o histórico de mensagens
        done = false;  // Define o estado do servidor como ativo
    }

    private ArrayList<String> messageHistory;  // Histórico de mensagens
    private static List<String> activeNicknames = new ArrayList<>();  // Lista de nicknames ativos

    @Override
    public void run() {
        try {
            System.out.println("Which server do you want to start? (1/2/3)");
            int serverIndex = sc.nextInt();  // Recebe a escolha do servidor
            if (serverIndex == 1) {
                server = new ServerSocket(9999);
            } else if (serverIndex == 2) {
                server = new ServerSocket(9998);
            } else if (serverIndex == 3) {
                server = new ServerSocket(9997);
            }
            pool = Executors.newCachedThreadPool();  // Cria um pool de threads
            while (!done) {
                Socket client = server.accept();  // Aceita nova conexão de cliente
                ConectionHandler handler = new ConectionHandler(client);
                connections.add(handler);  // Adiciona o cliente à lista de conexões
                pool.execute(handler);  // Executa o manipulador de conexão em uma nova thread
            }
        } catch (Exception e) {
            shutdown();
        }
    }

    public void broadcast(String message) {
        messageHistory.add(message);  // Adiciona a mensagem ao histórico
        for (ConectionHandler ch : connections) {
            if (ch != null) {
                ch.sendMessage(message);  // Envia a mensagem para todos os clientes
            }
        }
    }

    public void sendPrivateMessage(String message, String recieveNickname, String senderNickname) {
        for (ConectionHandler ch : connections) {
            if (ch != null && ch.getNickname().equals(recieveNickname)) {
                ch.sendMessage("[Private from " + senderNickname + "]: " + message);  // Envia mensagem privada
            }
        }
    }

    public void shutdown() {
        try {
            done = true;
            pool.shutdown();  // Encerra o pool de threads
            if (!server.isClosed()) {
                server.close();  // Fecha o socket do servidor
            }
            for (ConectionHandler ch : connections) {
                ch.shutdown();  // Fecha as conexões dos clientes
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    class ConectionHandler implements Runnable {
        private Socket client;  // Socket do cliente
        private BufferedReader in;  // Leitor de entrada do cliente
        private PrintWriter out;  // Escritor de saída para o cliente
        private String nickname;  // Nickname do cliente

        public String getNickname() {
            return nickname;  // Retorna o nickname do cliente
        }
        
        public ConectionHandler(Socket client) {
            this.client = client;  // Inicializa o socket do cliente
        }
        
        public void sendUserHistory(String targetNickname) {
            for (String message : messageHistory) {
                if (message.startsWith(targetNickname + ": ") || message.contains(" " + targetNickname + " ")) {
                    out.println(message);  // Envia o histórico de mensagens do usuário solicitado
                }
            }
        }
        
        @Override
        public void run() {
            try {
                out = new PrintWriter(client.getOutputStream(), true);  // Inicializa o escritor para enviar dados ao cliente
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));  // Inicializa o leitor para receber dados do cliente
                out.println("Please enter a nickname: ");
                nickname = in.readLine();  // Lê o nickname do cliente
        
                // Verifica se o nickname já está em uso e solicita um novo, se necessário
                synchronized (activeNicknames) {
                    while (activeNicknames.stream().anyMatch(nick -> nick.equalsIgnoreCase(nickname))) {
                        out.println("Nickname already in use, please choose another one.");
                        nickname = in.readLine();
                    }
                    activeNicknames.add(nickname);
                }
        
                System.out.println(nickname + " Connected!");
                broadcast(nickname + " joined the chat!");  // Anuncia que o cliente se juntou ao chat
        
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("/nick ")) {
                        String[] messageSplit = message.split(" ", 2);
                        if (messageSplit.length == 2) {
                            String newNickname = messageSplit[1];
                            // Verifica se o novo nickname já existe
                            synchronized (activeNicknames) {
                                if (activeNicknames.contains(newNickname)) {
                                    out.println("Nickname already in use, please choose another one.");
                                } else {
                                    broadcast(nickname + " renamed themselves to " + newNickname);
                                    System.out.println(nickname + " renamed themselves to " + newNickname);
                                    activeNicknames.remove(nickname);
                                    nickname = newNickname;
                                    activeNicknames.add(nickname);
                                    out.println("Successfully changed nickname to " + nickname);
                                }
                            }
                        } else {
                            out.println("No nickname provided");
                        }
                    } else if (message.startsWith("/quit")) {
                        broadcast(nickname + " left the chat!");
                        System.out.println(nickname + " left the chat!");
                        shutdown();  // Encerra a conexão do cliente
                    } else if (message.startsWith("/timeout")) {
                        broadcast(nickname + " disconnected due to inactivity");
                        System.out.println(nickname + " disconnected!");
                        shutdown();  // Encerra a conexão do cliente por inatividade
                    } else if (message.startsWith("/msg ")) {
                        String[] messageSplit = message.split(" ", 3);
                        if (messageSplit.length == 3) {
                            String recieveNickname = messageSplit[1];
                            String privateMessage = messageSplit[2];
                            sendPrivateMessage(privateMessage, recieveNickname, nickname);  // Envia mensagem privada
                        } else {
                            out.println("Incorrect private message format. Use /msg <nickname> <message>");
                        }
                    } else if (message.startsWith("/history")) {
                        String[] messageSplit = message.split(" ", 2);
                        if (messageSplit.length == 2) {
                            String historyNick = messageSplit[1];
                            sendUserHistory(historyNick);  // Envia histórico de mensagens do usuário especificado
                        } else {
                            out.println("Incorret history format. Use /history <nickname>");
                        }
                    } else {
                        broadcast(nickname + ": " + message);  // Envia a mensagem para todos os clientes
                    }
                }
            } catch (IOException e) {
                shutdown();  // Encerra a conexão em caso de erro
            }
        }
        
        public void sendMessage(String message) {
            out.println(message);  // Envia uma mensagem ao cliente
        }
        
        public void shutdown() {
            try {
                synchronized (activeNicknames) {
                    activeNicknames.remove(nickname);  // Remove o nickname da lista de ativos
                }
                in.close();  // Fecha o leitor de entrada
                out.close();  // Fecha o escritor de saída
                if (!client.isClosed()) {
                    client.close();  // Fecha o socket do cliente
                }
            } catch (IOException e) {
                // Ignore
            }
        }
        
        public static void main(String[] args) {
            Server server = new Server();
            server.run();  // Inicia o servidor
        }
    }

}
        