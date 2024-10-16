import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
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
            if (ch != null) {
                ch.sendMessage(message);
            }
        }
    }

    //Função para mensagens privadas
    public Boolean sendPrivateMessage(String message, String recieveNickname, String senderNickname) {
        for (ConectionHandler ch : connections) {
            if (ch != null && ch.getNickname().equals(recieveNickname)) {
                ch.sendMessage("[Private from " + senderNickname + "]: " + message);
                return true;
            }
        }
        return false;
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

        public String getNickname() {
            return nickname;
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

                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                out.println("Please enter a nickname: ");
                nickname = in.readLine();
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
                            broadcast(nickname + " rename themselves to " + messageSplit[1]);
                            System.out.println(nickname + " rename themselves to " + messageSplit[1]);
                            nickname = messageSplit[1];
                            out.println("Sucessfully change nickname to " + nickname);
                        } else {
                            out.println("No nickname provided");
                        }
                    } else if (message.startsWith("/quit")) {
                        out.println("You have been disconnected");
                        broadcast(nickname + " left the chat!");
                        System.out.println(nickname + "left the chat!");
                        shutdown();
                    } else if (message.startsWith("/timeout")) {
                        broadcast(nickname + " disconnected due to inactivity");
                        System.out.println(nickname + " disconnected!");
                        shutdown();
                    } else if (message.startsWith("/private")) {
                        {
                            String[] messageSplit = message.split(" ", 3);
                            if (messageSplit.length == 3) {
                                String recieveNickname = messageSplit[1];
                                String privateMessage = messageSplit[2];
                                if(sendPrivateMessage(privateMessage, recieveNickname, nickname)){
                                    out.println("Private message sent!");
                                }
                                else{
                                    out.println("Invalid nickname provided");
                                }


                            } else {
                                out.println("Incorrect private message format. Use /msg <nickname> <message>");
                            }

                        }
                    } else if (message.startsWith("/history ")) {

                        String[] messageSplit = message.split(" ", 2);
                        if (messageSplit.length == 2) {
                            String historyNick = messageSplit[1];
                            if(sendUserHistory(historyNick)){
                                out.println();
                                out.println("History sent");
                            }
                            else{
                                out.println("No messages found");
                            }
                        } else {
                            out.println("Incorrect history format. Use /history <nickname>");
                        }

                    } else if (message.startsWith("/msg")) {
                        out.println("[Transmitting...]");
                        broadcast(nickname + ": " + message.substring(5));
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
                in.close();
                out.close();
                if (!client.isClosed()) {
                    client.close();
                }
            } catch (IOException e) {
                //ignore
            }
        }

    }

    public static void main(String[] args) {
        Server server = new Server();
        server.run();
    }

}
