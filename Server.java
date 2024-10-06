import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements  Runnable{
    private ArrayList<ConectionHandler> connections;
    private ServerSocket server;
    private boolean done;
    private ExecutorService pool;
    private Scanner sc;

    public Server(){
        sc = new Scanner(System.in);
        connections = new ArrayList<>();
        done = false;
    }

    @Override
    public void run() {
        try {
            System.out.println("Qual servidor deseja iniciar? (1/2/3)");
            int serverIndex = sc.nextInt();
            if(serverIndex == 1){
                server = new ServerSocket(9999);
            }
            else if(serverIndex == 2){
                server = new ServerSocket(9998);
            }
            else if(serverIndex == 3){
                server = new ServerSocket(9997);
            }
            pool = Executors.newCachedThreadPool();
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

    public void broadcast(String message){
        for(ConectionHandler ch : connections){
            if(ch != null){
                ch.sendMessage(message);
            }
        }
    }

    public void shutdown(){
        try{
            done = true;
            pool.shutdown();
            if(!server.isClosed()){
                server.close();
            }
            for(ConectionHandler ch: connections){
                ch.shutdown();
            }
        } catch(IOException e) {
            //ignore
        }
    }

    class ConectionHandler implements Runnable{
        private Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private String nickname;


        public ConectionHandler(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            try{
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                out.println("Please enter a nickname: ");
                nickname = in.readLine();
                System.out.println(nickname + " Connected!");
                broadcast(nickname + " joined the chat!");
                String message;
                while((message = in.readLine()) != null){
                    if(message.startsWith("/nick ")) {
                        String[] messageSplit = message.split(" ", 2);
                        if (messageSplit.length == 2) {
                            broadcast(nickname + " rename themselves to " + messageSplit[1]);
                            System.out.println(nickname + " rename themselves to " + messageSplit[1]);
                            nickname = messageSplit[1];
                            out.println("Sucessfully change nickname to " + nickname);
                        } else {
                            out.println("No nickname provided");
                        }
                    }
                    else if(message.startsWith("/quit")){
                            broadcast(nickname + " left the chat!");
                            System.out.println(nickname + "left the chat!");
                            shutdown();
                    }
                    else if(message.startsWith("/timeout")){
                        broadcast(nickname + " desconectado por inatividade");
                        System.out.println(nickname + " desconectado!");
                        shutdown();
                    }
                    else{
                        broadcast(nickname + ": " + message);
                    }

                }
            } catch (IOException e){
                shutdown();
            }
        }

        public void sendMessage(String message){
            out.println(message);
        }

        public void shutdown(){
            try{
                in.close();
                out.close();
                if(!client.isClosed()){
                    client.close();
                }
            }
            catch(IOException e){
                //ignore
            }
        }

    }

    public static void main(String[] args) {
        Server server = new Server();
        server.run();
    }

}
