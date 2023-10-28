import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

// Libreria gson
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerMain {

  // HashMap utenti e parola da indovinare
  private static ConcurrentHashMap<String, User> map;
  private static AtomicReference<String> secretWord = new AtomicReference<String>("initialKeyWord");

  // File di configurazione
  private static final String configFile = "../config/server.properties";

  // File con gli utenti
  private static String path = "../bin/registeredUsers.json";

  // Variabili da leggere dal file di configurazione
  private static int port;
  private static int nThread;
  private static int maxDelay; 
  private static int terminationDelay;
  private static int timeBetweenWords;

  public static void main(String[] args){
    
    try{
      // Per prmia cosa leggo il file di confgurazione
      readConfig();

      System.out.println("SERVER: Server running...");

      // Inizializzazione dei socket del server
      ServerSocket serverSocket = new ServerSocket(port);
      MulticastSocket multicastSocket = new MulticastSocket();

      // Creazione threadpool con la coda dei thread
      BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
      ExecutorService pool = new ThreadPoolExecutor( 
      nThread, 
      nThread, 
      terminationDelay, 
      TimeUnit.MILLISECONDS,
      queue, 
      new ThreadPoolExecutor.AbortPolicy() 
      );

      // Costruisco la map degli utenti leggendo dal file JSON
      buildMap(path);

      // Utilizzo handler di terminazione, ad esempio se terminassi il server con CTRL+C mi salva una mappa
      // con tutti gli utenti e le caratteristiche nel file json "registeredUsers.json"
      Runtime.getRuntime().addShutdownHook(new TerminationHandler(maxDelay, pool, serverSocket, map));

      // Scelgo una nuova parola dal file delle parole
      chooseNewWord();
      // System.out.println("SERVER: Secret word -> "+ secretWord);

      // Ciclo di ascolto dei client, gestito con una threadpool
      while (true) {
        Socket clientSocket = null;
        
        try {
          // Accetto le richieste dei client connessi
          clientSocket = serverSocket.accept();
        } catch (SocketException e) {
          // Quando il TerminationHandler chiude la serverSocket, si solleva una SocketException ed esco dal ciclo
          break;
        }
        // Avvio un thread dalla pool per avviare il gioco per il client
        pool.execute(new WordleGame(clientSocket, multicastSocket, map, secretWord));  
      } 
      serverSocket.close();

    }catch (Exception e){
      e.printStackTrace();
    }

  }

  
  /**
   * Metodo newWord(): prende una parola a random nel file e la restituisce.
   * @return la parola segreta
   * @throws IOException se qualcosa va storto nella lettura del file
   */
  private static String newWord() throws IOException{ 
    String newWord;
    String path = "../../Words/words.txt";
    RandomAccessFile raf = new RandomAccessFile(path,"r");
    Random random = new Random();
    int n=1;
    int numElements = (((int) raf.length())-30824)/10; // Calcolo del numero degli elementi nel file
    n = random.nextInt(numElements);
    raf.seek(n*11-11); // Vado ad una linea a caso nel file
    newWord = raf.readLine(); // Prendo la parola a quella linea
    System.out.println("SERVER: Secret word -> "+ newWord);
    raf.close();
    return newWord;
  }
  

  /**
   * Metodo che legge il file di configurazione del server.
   * @throws FileNotFoundException se il file non esiste
   * @throws IOException se si verifica un errore durante la lettura
  */
	public static void readConfig() throws FileNotFoundException, IOException {
		InputStream input = new FileInputStream(configFile);
		Properties prop = new Properties();

		prop.load(input);

		port = Integer.parseInt(prop.getProperty("port"));
    nThread = Integer.parseInt(prop.getProperty("nThread"));
		maxDelay = Integer.parseInt(prop.getProperty("maxDelay"));
    terminationDelay = Integer.parseInt(prop.getProperty("terminationDelay"));
    timeBetweenWords = Integer.parseInt(prop.getProperty("timeBetweenWords"));

		input.close();
	}

  /**
   * Metodo chooseNewWord(): chiama il metodo newWord() allo scadere del timer per scegliere una nuova parola
   */
  private static void chooseNewWord(){
    // Scelta della nuova parola da trovare, il timer regola ogni quanto cambia
    Timer timer = new Timer();
    timer.scheduleAtFixedRate(new TimerTask(){
      public void run() {
        try{
          String word = newWord();
          secretWord.set(word);
          resetPlayers(); // Al cambio della parola è possibile rigiocare per gli utenti loggati che hanno già giocato con la parola precedente

        }catch(Exception e){
          e.printStackTrace();
        }
      }
    } , 0, timeBetweenWords);
  }

  /**
   * Metodo buildMap(String path): prende un file json e costruisce una concurrentHashMap 
   * ossia la mappa per tenere traccia degli utenti registrati.
   * Prima controlla se il file è vuoto, in quel caso inizializza una mappa vuota.
   * Se esiste già una mappa ben formata nel file json la trasforma in una concurrentHashMap.
   * 
   * @param path percorso del file
   * @throws Exception se qualcosa va storto
   */
  private static void buildMap(String path) throws Exception{
    String jsonString = new String(Files.readAllBytes(Paths.get(path)));
    if(jsonString.equals("") || jsonString.equals("null")){ 
        map = new ConcurrentHashMap<String, User>();
    }else{ 
        map = new Gson().fromJson(jsonString, new TypeToken<ConcurrentHashMap<String, User>>() {}.getType());
        // System.out.println("SERVER: Current map -> " + map);
    }
  }

  /**
   * Metodo resetPlayers(): resetta il campo canPlay degli user. Usata quando la parola cambia, cosicché i giocatori
   * che hanno già giocato non possono rigiocare con la stessa parola.
   * E dopo che cambia possono rigiocare niuovamente.
   */
  private static void resetPlayers(){
    for(Map.Entry<String,User> entry : map.entrySet()) {
      User user = entry.getValue(); 
      user.setCanPlay(true);
    }
  } 

}