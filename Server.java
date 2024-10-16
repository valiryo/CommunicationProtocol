import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
//import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements Runnable {
    private ArrayList<ConectionHandler> connections; //Lista dos clientes conectados no servidor
    private ServerSocket server;
    private boolean done;   //Booleana de apoio para monitorar se o servidor está funcionando ou não
    private ExecutorService pool; //pool de threads que contem os clientes
    private Scanner sc;
    private ArrayList<String> messageHistory; //lista de mensagens de cada cliente
    private static List<String> activeNicknames = new ArrayList<>();  // Lista de nicknames ativos


    public Server() {
        sc = new Scanner(System.in);
        connections = new ArrayList<>();
        messageHistory = new ArrayList<>();
        done = false;
    }




    @Override
    public void run() {
        try {
            System.out.println("Which room do you want to start? (1/2/3)");
            int serverIndex = sc.nextInt();

            //Oferecemos 3 salas de conversas. Cada uma é implementada com uma porta diferente.
            if (serverIndex == 1) {
                server = new ServerSocket(9999);
            } else if (serverIndex == 2) {
                server = new ServerSocket(9998);
            } else if (serverIndex == 3) {
                server = new ServerSocket(9997);
            }
            pool = Executors.newCachedThreadPool();  //Criando a pool de threads.

            /*  Enquanto o servidor não for finalizado: aceitamos qualquer cliente que requisitar conexão,
                criamos um objeto de gerenciamento para esse cliente, e inserimos o objeto de gerenciamento na lista de clientes conectados.
            */
            while (!done) {
                Socket client = server.accept();
                ConectionHandler handler = new ConectionHandler(client);
                connections.add(handler);
                pool.execute(handler); //Manda o cliente pra uma pool de threads reutilizáveis, que se aproveita da vida curta da implementação de Client
            }
        } catch (Exception e) {
            shutdown();
        }
    }

    //Transmite uma mensagem para todos os clientes conectados
    public void broadcast(String message) {
        messageHistory.add(message);
        for (ConectionHandler ch : connections) {
            if (ch != null && ch.getAway() == false) {
                ch.sendMessage(message);
            }
        }
    }

    //Função para mensagens privadas
    public int sendPrivateMessage(String message, String recieveNickname, String senderNickname) {
        //1: existe
        //2: existe mas está ausente
        //3: não existe
        for (ConectionHandler ch : connections) {
            if (ch != null && ch.getNickname().equals(recieveNickname)) {
                if(ch.getAway() == false){
                    ch.sendMessage("[Private from " + senderNickname + "]: " + message);
                    return 1;
                }
                else{
                    return 2;
                }
            }
        }
        return 3;
    }


    public void shutdown() {
        try {
            done = true;
            pool.shutdown();
            if (!server.isClosed()) {
                server.close();
            }
            for (ConectionHandler ch : connections) {
                ch.shutdown();
            }
        } catch (IOException e) {
            //ignore
        }
    }

    class ConectionHandler implements Runnable {
        private Socket client;
        private BufferedReader in; //Buffer para receber dados dos clientes
        private PrintWriter out; //Buffer para mandar dados para os clientes
        private String nickname; //nome do usuário
        private Boolean away; //Usuário está ausente?

        public String getNickname() {
            return nickname;
        }

        public void changeAway(){
            if(this.away == false){
                this.away = true;
                out.println("[Status successfully changed to away]");
                broadcast(this.getNickname() + " AFK!");

            }
            else{
                this.away = false;
                out.println("[[Status successfully changed to not away]]");
                broadcast(this.getNickname() + " NO LONGER AFK!");
            }
        }
        public Boolean getAway(){
            return this.away;
        }

        public ConectionHandler(Socket client) {
            this.client = client;
        }

        //Função de histórico de mensagens
        public Boolean sendUserHistory(String targetNickname) {
            Boolean hasMessage = false;
            for (String message : messageHistory) {
                if (message.startsWith(targetNickname + ": ") || message.contains(" " + targetNickname + " ")) {
                    out.println(message);
                    hasMessage = true;
                }
            }
            return hasMessage;
        }

        @Override
        public void run() {
            try {
                away = false;  //meu próprio estado
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                out.println("Please enter a nickname: ");
                nickname = in.readLine();

                synchronized (activeNicknames) {
                    while (activeNicknames.stream().anyMatch(nick -> nick.equalsIgnoreCase(nickname))) {
                        out.println("Nickname already in use, please choose another one.");
                        nickname = in.readLine();
                    }
                    activeNicknames.add(nickname);
                }

                System.out.println(nickname + " Connected!");
                broadcast(nickname + " joined the chat!");

                /*
                    Enquando houver mensagens: verifico se é um comando dentre os listados, e executo a devida
                    função para cada um. Se não, informo o cliente que
                */
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
                    } else if (message.equals("/quit")) {
                        out.println("You have been disconnected");
                        broadcast(nickname + " left the chat!");
                        System.out.println(nickname + "left the chat!");
                        shutdown();
                    } else if (message.equals("/timeout")) {
                        broadcast(nickname + " disconnected due to inactivity");
                        System.out.println(nickname + " disconnected!");
                        shutdown();
                    } else if (message.startsWith("/private ")) {
                        {
                            String[] messageSplit = message.split(" ", 3);
                            if (messageSplit.length == 3) {
                                String recieveNickname = messageSplit[1];
                                String privateMessage = messageSplit[2];
                                if(sendPrivateMessage(privateMessage, recieveNickname, nickname) == 1){
                                    out.println("[Private message sent!]");
                                }
                                else if(sendPrivateMessage(privateMessage, recieveNickname, nickname) == 2){
                                    out.println("[AFK recipient]");
                                }
                                else{
                                    out.println("[Invalid nickname!]");
                                }


                            } else {
                                out.println("Incorrect private message format. Use /msg <nickname> <message>");
                            }

                        }
                    } else if (message.startsWith("/history ")) {

                        String[] messageSplit = message.split(" ", 2);
                            String historyNick = messageSplit[1];
                            if(sendUserHistory(historyNick)){
                                out.println();
                                out.println("History sent");
                            }
                            else{
                                out.println("No messages found");
                            }

                    } else if (message.startsWith("/msg ")) {
                        out.println("[Transmitting...]");
                        broadcast(nickname + ": " + message.substring(5));
                    } else if(message.equals("/away")){
                        this.changeAway();
                    }
                    else{
                        out.println("Incorrect command format");
                    }

                }
            } catch (IOException e) {
                shutdown();
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        //Função de fechamento do servidor
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

    }

    public static void main(String[] args) {
        Server server = new Server();
        server.run();
    }

}
