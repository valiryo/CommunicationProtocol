import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Client implements Runnable{
    private Socket client;
    private BufferedReader in;
    private PrintWriter out;
    private boolean done;
    private Scanner sc;

    @Override
    public void run() {
        try {
            sc = new Scanner(System.in);
            System.out.println("Em qual servidor deseja conectar? (1/2/3)");
            int serverIndex = sc.nextInt();
            if(serverIndex == 1){
                client = new Socket("127.0.0.1", 9999);
            }
            else if(serverIndex == 2){
                client = new Socket("127.0.0.1", 9998);
            }
            else if(serverIndex == 3){
                client = new Socket("127.0.0.1", 9997);
            }

            in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            out = new PrintWriter(client.getOutputStream(), true);

            InputHandler inHandler = new InputHandler();
            Thread t = new Thread(inHandler);
            t.start();

            String inMessage;
            while((inMessage = in.readLine()) != null){
                System.out.println(inMessage);
            }
        }
        catch(IOException e){
            shutdown();
        }
    }

    public void shutdown(){
        done = true;
        try{

            in.close();
            out.close();
            if(!client.isClosed()){
                client.close();
            }
        } catch(IOException e) {
            //Ignore
        }
    }

    class InputHandler implements Runnable{
        @Override
        public void run() {
            try{
                BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
                while(!done){
                    String message = inReader.readLine();
                    if(message.equals("/quit")){
                        out.println(message);
                        inReader.close();
                        shutdown();

                    }
                    else{
                        out.println(message);
                    }
                }
            }
            catch (IOException e){
                shutdown();
            }
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }
}
