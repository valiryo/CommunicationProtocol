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
    private Socket client;  // Socket do cliente
    private BufferedReader in;  // Leitor de entrada do cliente
    private PrintWriter out;  // Escritor de saída para o cliente
    private boolean done;  // Indicador de estado do cliente
    private Scanner sc;  // Scanner para entrada do usuário

    @Override
    public void run() {
        try {
            sc = new Scanner(System.in);
            System.out.println("Enter the server IP:");
            String serverIP = sc.nextLine();  // Recebe o IP do servidor como entrada
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
            shutdown();  // Encerra a conexão em caso de erro
        }
    }

    public void shutdown() {
        done = true;  // Define o estado do cliente como concluído
        try {
            in.close();  // Fecha o leitor de entrada
            out.close();  // Fecha o escritor de saída
            if (!client.isClosed()) {
                client.close();  // Fecha o socket do cliente
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    class InputHandler implements Runnable {
        private static final long TIMEOUT = 3 * 60 * 1000;  // Definição de timeout de 3 minutos
        private Timer timer = new Timer();  // Inicializa um novo timer
        private int strike = 0;  // Contador de inatividade

        @Override
        public void run() {
            try {
                BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
                startInactivityTimer();  // Inicia o timer de inatividade
                while (!done) {
                    String message = inReader.readLine();  // Lê a mensagem do usuário
                    restartInactivityTimer();  // Reinicia o timer de inatividade
                    strike = 0;  // Reseta o contador de inatividade
                    if (message.equals("/quit")) {
                        out.println(message);
                        inReader.close();
                        shutdown();  // Encerra a conexão se o usuário digitar /quit
                    } else {
                        out.println(message);  // Envia a mensagem ao servidor
                    }
                }
            } catch (IOException e) {
                shutdown();  // Encerra a conexão em caso de erro
            }
        }

        private void startInactivityTimer() {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (strike < 3) {
                        // Incrementa o contador de inatividade e verifica o limite
                        System.out.println("Timeout!!" + LocalDateTime.now());
                        strike++;
                        if (strike >= 3) {
                            timer.cancel();
                            out.println("/timeout");  // Notifica o servidor de inatividade
                        }
                        restartInactivityTimer();  // Reinicia o timer
                    } else {
                        timer.cancel();
                        Client.this.shutdown();  // Encerra a conexão após 3 tentativas de inatividade
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
        client.run();  // Inicia o cliente
    }
}
