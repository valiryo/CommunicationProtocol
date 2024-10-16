import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

public class Client implements Runnable {
    private Socket client;
    private BufferedReader in;
    private PrintWriter out;
    private boolean done;
    private Scanner sc;

    @Override
    public void run() {
        try {
            sc = new Scanner(System.in);
            System.out.println("Enter the server IP:");
            String serverIP = sc.nextLine();


            System.out.println("Which server do you want to connect to? (1/2/3)");

            int serverIndex = sc.nextInt();
            if (serverIndex == 1) {
                client = new Socket(serverIP, 9999);
            } else if (serverIndex == 2) {
                client = new Socket(serverIP, 9998);
            } else if (serverIndex == 3) {
                client = new Socket(serverIP, 9997);
            }

            in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            out = new PrintWriter(client.getOutputStream(), true);

            InputHandler inHandler = new InputHandler();
            Thread t = new Thread(inHandler);
            t.start();

            String inMessage;
            while ((inMessage = in.readLine()) != null) {
                System.out.println(inMessage);
            }
        } catch (IOException e) {
            shutdown();
        }
    }

    public void shutdown() {
        done = true;
        try {

            in.close();
            out.close();
            if (!client.isClosed()) {
                client.close();
            }
        } catch (IOException e) {
            //Ignore
        }
    }

    class InputHandler implements Runnable {

        private static final long TIMEOUT = 3 * 60 * 1000;
        private Timer timer = new Timer();
        private int strike = 0;

        @Override
        public void run() {
            try {
                BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
                startInactivityTimer();
                while (!done) {
                    String message = inReader.readLine();
                    restartInactivityTimer();
                    strike = 0;
                    if (message.equals("/quit")) {
                        out.println(message);
                        inReader.close();
                        shutdown();

                    } else {
                        out.println(message);
                    }
                }
            } catch (IOException e) {
                shutdown();
            }
        }

        private void startInactivityTimer() {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (strike < 3) {
                        System.out.println("Timeout!!");
                        strike++;
                        if (strike >= 3) {
                            timer.cancel();
                            out.println("/timeout");
                        }
                        restartInactivityTimer();
                    } else {
                        timer.cancel();
                        Client.this.shutdown();
                    }

                }
            }, TIMEOUT); // Aguarda 3 minutos antes de enviar o sinal de inatividade
        }


        private void restartInactivityTimer() {
            timer.cancel(); // Cancela o timer anterior
            timer = new Timer(); // Cria um novo timer
            startInactivityTimer(); // Inicia o novo timer
        }


    }


    public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }
}
