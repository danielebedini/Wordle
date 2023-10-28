// Libreria java
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
// Libreria gson
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TerminationHandler extends Thread {
    /*
     * Questa classe si occupa della terminazioen del server quando viene chiuso (ad esempio con un ^C).
     * La terminazione prende i seguenti parametri e gestisce la chiusura della pool e del socket.
     * Alla terminazione resetta anche i campi degli utenti (isLogged e canPlay).
     * Prima di terminare aggiorna la mappa trasformandola in un json usando GSON nel file "registeredUsers.json".
     * 
     */
    private int maxDelay; 
    private ExecutorService pool;
    private ServerSocket serverSocket;
    public ConcurrentHashMap<String, User> map;
    
    /**
     * Costruttore della classe che permette di ricevere i parametri sottostanti dal server.
     * 
     * @param maxDelay tempo massimo in cui la pool può stare aperta in attesa che tutti i threat escano da essa.
     * @param pool threadpool contenente i thread del server
     * @param serverSocket socket del server
     * @param map hash map contenente gli utenti 
     */
    public TerminationHandler(int maxDelay, ExecutorService pool, ServerSocket serverSocket, ConcurrentHashMap<String, User> map) {
        this.maxDelay = maxDelay;
        this.pool = pool;
        this.serverSocket = serverSocket;
        this.map = map;
    }
    
    /**
     * Metodo run(): metodo che quando il server termina chiude la pool, resetta a false i campi canPlay e isLogged di tutti gli utenti nella map.
     * Infine aggiorna la mappa con GSON, sovrascrivendola nel file "registeredUsers.json".
     */
    public void run() {
        
        System.out.println("SERVER TERMINATION: Termination...");

        try {
            // Prima di tutto chiude la socket del server almeno non accetta più nuove richieste
            serverSocket.close();
            // Spegne la pool se è vuota oppure aspetta che si svuota fino ad un massimo delay, dopodiché la spegne.
            pool.shutdown();
            if(!pool.awaitTermination(maxDelay, TimeUnit.MILLISECONDS)){
                pool.shutdownNow();
            }
            System.out.println("SERVER TERMINATION: Server shut down.");

            // Resetto i campi degli user che sono usciti senza aver fatto logout o exit
            for(Map.Entry<String,User> entry : map.entrySet()){
                User user = entry.getValue();
                user.setCanPlay(false);
                user.setIsLogged(false);
            }

            // Sovrascrittura della mappa nel file "registeredUsers.json"
            FileWriter fileWriter = new FileWriter("registeredUsers.json");
            // System.out.println("SERVER TERMINATION: map after termination: " + map);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(map, fileWriter);
            fileWriter.close();
        }catch (Exception e) {
            e.printStackTrace();
        }  		
    }
}

