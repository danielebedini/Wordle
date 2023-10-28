import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class ClientMain {
  /**
   * Questa è la classe relativa al client. Prima di tutto si prova a connettere con il server instaurando una connessione TCP con esso.
   * Dopodiché può inviare i comandi per poter interagire con il server, quest'ultimo risponderà con dei mesaggi.
   * Inoltre, se riceve parole in formato "vvgg--" durante la fase di gioco, è in grado di interpretarle e stampare a video 
   * la parola con i relativi colori.
   */

  // Percorso del file di configurazione
  public static final String configFile = "../config/client.properties";

  // Stringhe per la comunicazione da e verso il server
  private static String fromServer="";
  private static String toServer="";
  
  // Variabili per la connessione TCP con il server
  private static String hostname;
  private static int port;
  private static Socket socket = null;
  private static InputStreamReader input = null;
  private static BufferedReader br = null;
  private static PrintWriter out = null;
  private static Scanner in = null;

  // Variabile per terminare la connessione con il server
  private static boolean end=false;

  // Variabili e struttre dati per il multicast
  private static int multicastPort;
  private static String multicastHost;
  private static MulticastSocket multicastSocket;
  private static List<String> notifications = new ArrayList<String>(); 
  private static boolean endMulticast=false;

  // Stringhe per i colori delle lettere (servono per la stampa dei colori delle lettere in fase di gioco)
  public static final String COLOR_YELLOW = "\u001B[33m";
  public static final String COLOR_GREEN = "\u001B[32m";
  public static final String COLOR_RESET = "\u001B[0m";

  public static void main(String[] args){
    
    try{
      // Leggo file di configurazione "client.properties"
      readConfig();

      // Strutture dati per comunicare col server
      input = new InputStreamReader(System.in); 
      br = new BufferedReader(input);
      socket = new Socket(hostname,port); 
      out = new PrintWriter(socket.getOutputStream(),true);
      in  = new Scanner(socket.getInputStream());
      multicastSocket = new MulticastSocket(multicastPort);
      
      // Thread che si inizializza non appena il client fa la join nel gruppo multicast
      Thread threadListenerUDPs = new Thread () {
        public void run () {
          System.out.println("CLIENT: Waiting shares...");
          while(!endMulticast){
            // Se ricevo condivisioni tramite UDP le stampo nel client che ha mandato show me sharing
            DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
            try {
              multicastSocket.receive(response);
            } catch (IOException e) {
              System.out.println("CLIENT: Client is not reciving messages.");
            }
            String responseUDPfromServer = new String(response.getData(), 0, response.getLength());
            notifications.add(responseUDPfromServer);
            // System.out.println("CLIENT: Results from server: " + responseUDPfromServer);
          }
        }
      };

      // Stampo il menu all'inizio dell'interazione con il server
      printMainMenu();

      // Ciclo di interazione con il server. 
      while(!end){ 

        /* 
         * Lista dei comandi disponibili:
         * 
         * register
         * login
         * logout
         * play / play wordle
         * logout
         * stats / send stats / send me statistics
         * share
         * show / show me sharing
         * menu
         * exit
        */

        // Scrivo il comando da mandare al server
        toServer = br.readLine(); 
        
        // Se il client desidera resettare il menu
        if(toServer.toLowerCase().equals("menu")){
          printMainMenu();
          continue;
        }
        
        // Se il client vuole uscire interrompo la comunicazione con il server e con il gruppo multicast
        if(toServer.toLowerCase().equals("exit")){
          System.out.println("CLIENT: Exiting...");
          end=true;
          endMulticast=true;
          out.println(toServer);
          continue;
        }else{
          out.println(toServer);
        }
        
        // Ricevo la risposta dal server e faccio i controlli su di essa successivamente.
        fromServer=in.nextLine(); 

        // Se il login è andato a buon fine allora rimuovo le vecchie notifiche dall'array delle notifiche e mi connetto al gruppo multicast.
        if(fromServer.equals("LOGIN: Login Successful.")){ 
          //System.out.println(fromServer);
          notifications.clear();
          endMulticast=false;

          InetAddress group = InetAddress.getByName(multicastHost);
          InetSocketAddress address = new InetSocketAddress(group, multicastPort);
          NetworkInterface networkInterface = NetworkInterface.getByInetAddress(group);

          multicastSocket.joinGroup(address, networkInterface);
          System.out.println("CLIENT: You've just joined this group: " + multicastHost);
          threadListenerUDPs.start();

          continue;
        }

        // Se il client vuole vedere le notifiche e il server acconsente il client le riceve e le stampa.
        if(fromServer.equals("SHOW ME SHARING: Request sent.")){
          System.out.println(fromServer);
          System.out.println("CLIENT: Notifications: "+ notifications.toString());
          continue;
        }

        // Se il client riesce a fare logout allora esce dal gruppo multicast
        if(fromServer.equals("LOGOUT: Success.")){
          System.out.println(fromServer);
          multicastSocket.close();
          endMulticast = true;
          System.out.println("CLIENT: You are no longer in the group.");
          continue;
        }

        // In fase di gioco: se ricevo una parola dal server nel formato "vvgg--" allora il client la elabora e la fa vedere a colori
        if(fromServer.length()==10){ // è una parola da colorare
          printColoredWord(toServer,fromServer);
          continue;
        }else{ // altrimenti è un altro messaggio
          System.out.println(fromServer);
          continue;
        }
        
      }
      
      // Quando la variabile end diventa true allora chiudo il socket del client perché la connessione tra client e server è terminata
      socket.close();
      System.out.println("CLIENT: Client socket closed.");

    }catch(Exception e){
      e.printStackTrace();
    } 
  }

/** 
 * Metodo printColoredWord(String guess, String s): prende la stringa guess e una stringa s, 
 * stampa la parola con le lettere colorate in base alle regole del gioco. 
 * 
 * VERDE "v" -> lettera corretta nella posizione corretta
 * GIALLO "g" -> lettera corretta nella posizione errata
 * LETTERA SBAGLIATA "-" -> lettera non presente nella parola da indovinare
 * 
 * @param guess parola inviata dal client
 * @param s parola codificata nel formato "vvgg--" ricevuta dal server
 */
  private static void printColoredWord(String guess, String s){
    for(int i=0; i<s.length();i++){
      if(s.charAt(i)=='-'){
        System.out.print(guess.charAt(i));
      }
      if(s.charAt(i)=='v'){
        System.out.print(COLOR_GREEN + guess.charAt(i) + COLOR_RESET);
        
      }
      if(s.charAt(i)=='g'){
        System.out.print(COLOR_YELLOW + guess.charAt(i) + COLOR_RESET);
      }
    }
    System.out.println("\n");
  }

  /** 
   * Metodo printMainMenu(): resetta la command line e stampa le varie opzioni del menu. 
   */
  public static void printMainMenu(){
    System.out.print("\033[H\033[2J");  
    System.out.flush();  
    System.out.println("----------------");
    System.out.println("Register");
    System.out.println("Login");
    System.out.println("Logout");
    System.out.println("Play Wordle");
    System.out.println("Player statistics");
    System.out.println("Share my stats");
    System.out.println("Show me sharing");
    System.out.println("Main menu");
    System.out.println("Exit");
    System.out.println("----------------");
  }

  /**
	* Metodo per leggere il file di configurazione del client "client.properties".
	* @throws FileNotFoundException se il file non esiste
	* @throws IOException se qualcosa va storto in fase di lettura
	*/
	public static void readConfig() throws FileNotFoundException, IOException {
		InputStream input = new FileInputStream(configFile);
    Properties prop = new Properties();
    prop.load(input);
    hostname = prop.getProperty("hostname");
    port = Integer.parseInt(prop.getProperty("port"));
    multicastPort = Integer.parseInt(prop.getProperty("multicastPort"));
    multicastHost = prop.getProperty("multicastHost"); 
    input.close();
	}
}
