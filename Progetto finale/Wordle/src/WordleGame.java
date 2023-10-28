import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class WordleGame implements Runnable{

    // Percorso del file di configurazione del server
    public static final String configFile = "../config/wordlegame.properties";

    // Variabili che servono per implementare il multicast. Range di indirizzi: [224.0.0.0-239.255.255.255] 
    InetAddress group;
    private static int multicastPort;
    private static String multicastHost;
    private MulticastSocket multicastSocket; 

    // Socket per la comunicazione TCP con il client
    private Socket clientSocket;
    
    // Utente corrente che sta giocando o semplicemente comunicando con il server
    private User currentUser;
    
    // Mappa hash per tenere traccia degli utenti registrati
    private ConcurrentHashMap<String, User> map;

    // Parola segreta da indovinare
    private static AtomicReference<String> secretWord;

    /**
     * Costruttre della classe WordleGame: istanzia la classe con i relativi parametri.
     * 
     * @param clientSocket Socket relativo al client
     * @param multicastSocket Socket relativo al gruppo multicast
     * @param map Mappa hash che tiene traccia degli utenti registrati
     * @param secretWord Parola segreta da indovinare
     */
    public WordleGame(Socket clientSocket, MulticastSocket multicastSocket, ConcurrentHashMap<String, User> map, AtomicReference<String> secretWord) {
        this.clientSocket = clientSocket;
        this.multicastSocket = multicastSocket;
        this.map = map; 
        WordleGame.secretWord = secretWord;
    }

    /**
     * Essendo Runnable la classe deve implementare il metodo run() sottostante.
     * Questo metodo gestisce tutta la comunicazione con il client.
     * La parte principale della comunicazione è uno switch che in base alle scelte del client esegue i comandi desiderati.
     * Inoltre si occupa anche di far giocare i client a Wordle se inseriscono il comando "play".
     */
    public void run(){
        /**
         * Stampe varie per visualizzare cosa fa il server.
         * Togliere o mettere il commento per vedere a video le operazioni.
         */
        System.out.println("SERVER WORDLE: Client "+ clientSocket.getInetAddress() +" just connected!");
        
    
        try{
            // Leggo il file di configurazione "wordlegame.properties" per inizializzare le variabili per la comunicazione
            readConfig();
            
            // Il server inizializza il gruppo multicast
            group = InetAddress.getByName(multicastHost);

            // Stream per la comunicazione con il client che si è connesso
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            Scanner in = new Scanner(clientSocket.getInputStream());
            String toClient="";
            String fromClient="";

            
            while(in.hasNextLine()){
                /**
                 * Questo switch legge i comandi che sono inviati dal client e in base a quello esegue le operazioni desiderate.
                 * La label "start" serve per far ripartire lo switch durante una break.
                 * La comunicazione con il client avviene finché nella stream di ricezione dal client "in" ci sono elementi da leggere.
                 */
                
                fromClient = in.nextLine();

                start: switch(fromClient.toLowerCase()){
                    
                    case "register": // L'utente desidera registrarsi
                    
                        boolean userExistsRegister = false; 
                        out.println("Register: Insert username:");
                        // Chiede all'utente con quale username vorrebbe registrarsi
                        String reg_username = in.nextLine();

                        // Non si può registrare un utente senza nome
                        if(reg_username.equals("")){
                            out.println("REGISTER: Please enter a valid userneame.");
                            break start;
                        }

                        // Se viene inserito un username valido ontrollo in tutta la map che l'utente inserito non sia già registrato
                        // Se l'utente è già registrato allora si torna all'inizio dello switch, comunicando di fare login
                        for(Map.Entry<String,User> entry : map.entrySet()) {
                            User user = entry.getValue(); 
                            if(user.getUsername().equalsIgnoreCase(reg_username)){
                                out.println("REGISTER: Username is already registered. Try again.");
                                userExistsRegister = true;
                                break start;
                            }
                        }

                        // Se l'utente è nuovo allora posso registrarlo, si richiede una password da associare all'username 
                        // poi si aggiunge il nuovo utente alla mappa
                        if(!userExistsRegister){
                            out.println("REGISTER: Insert password: ");
                            String reg_password = in.nextLine();
                            User user = new User(reg_username,reg_password);
                            map.put(reg_username, user);
                        }

                        // Togliere il commento al comando sottostante per stampare la mappa aggiornata
                        // System.out.println("SERVER: Updated map -> " + map);
                        out.println("SERVER: Enter new command.");

                    break start;

                    case "login": // L'utente desidera fare il login

                        /* Se l'utente ancora non è stato definito oppure non è loggato allora si può procedere con il login.
                        * Controllo che nella mappa esista un utente registrato con quell'username,
                        * se è presente, il server chiede di inserire la password (deve combaciare ovviamente con quella relativa a quell'username).
                        * Se non è presente si chiede al client di registrare l'account prima di poter effettuare il login.
                        * Se il login ha successo allora inizializzo il currentUser all'utente che si è appena loggato.
                        * I procedimenti di controllo nella map sono pressoché identici a quelli effettuati durante la registrazione.
                        */
                        if(currentUser==null || (currentUser!=null && !currentUser.getIsLogged())){
                            out.println("LOGIN: Username: ");
                            String log_username = in.nextLine();
                            boolean userExistsLogin = false;
                            
                            for(Map.Entry<String,User> entry : map.entrySet()) {
                                User user = entry.getValue();
                                if(user.getUsername().equalsIgnoreCase(log_username) && !userExistsLogin){ 
                                    userExistsLogin = true;
                                    out.println("LOGIN: Password:");
                                    String log_password = in.nextLine();
                                    
                                    if(user.getPassword().equalsIgnoreCase(log_password)){
                                        out.println("LOGIN: Login Successful.");
                                        currentUser = user;
                                        currentUser.setIsLogged(true);
                                        currentUser.setCanPlay(true);
                                        
                                        break start;
                                    }else{
                                        out.println("LOGIN: Invalid password, login again.");
                                        break start;
                                    }
                                }
                            }
                            
                            if(!userExistsLogin){
                                out.println("LOGIN: User not existing, register first.");
                                break start;
                            }
                            // out.println("SERVER: Enter new command.");
                            break start;

                        } else { // Se dopo un login il client volesse loggarsi nuovamente
                            out.println("LOGIN: Already logged.");
                            break start;
                        }
                    
                    case "play", "play wordle": // L'utente desidera giocare a wordle.
                        /*
                         * Prima di poter giocare un utente deve aver effettuato il login, 
                         * così da far diventare true tutte le variabili nell'if sottostante.
                         * Inoltre, per giocare non deve già aver giocato con la solita parola.
                         * 
                         * Una volta che un client sta giocando si prende la parola del client e si controlla.
                         * Ad ogni parola sbagliata (ma che esiste nel file) si chiama il metodo buildResult
                         * che costruisce la stringa da dare in risposta al client nel formato "vvgg--" e si incrementa il numero di tentativi.
                         * 
                         * Se il numero di tentativi massimi viene raggiunto allora il client ha perso.
                         * In caso contrario, se la parola è indovinata il client vince.
                         * Al termine della partita vengono elaborate le statistiche.
                         * 
                         */
                        
                        if(currentUser!=null && currentUser.getIsLogged() && currentUser.getCanPlay()){ 
                            // System.out.println(clientSocket.getInetAddress()+ " is in game.");
                            currentUser.setIsPlaying(true);
                            boolean wordGuessed=false;
                            String guess = "";
                            String result = "";
    
                            toClient = "Try to guess...";
                            out.println(toClient); 
                            
                            // Fase effettiva di gioco: i tentativi massimi sono 12.
                            while(!wordGuessed && currentUser.getTries()<12){
                                // Leggo che parola ha inviato e controllo
                                guess = in.nextLine();
                                
                                if(isValid(guess)){ // La parola inserita è valida (presente nel file)
                                    if (guess.toLowerCase().equals(secretWord.toString())){ // Ha indovinato
                                        currentUser.setTries(currentUser.getTries()+1);
                                        toClient = "vvvvvvvvvv"; // Parola tutta verde
                                        out.println(toClient);
                                        wordGuessed=true;
                                        continue;
                                    }else{ // parola valida (presente nel file) ma scorretta
                                        result=buildResult(guess); // result è la stringa nel formato "vvgg--"
                                        //System.out.println(result);
                                        toClient=result;
                                        out.println(toClient);
                                        currentUser.setTries(currentUser.getTries()+1);
                                        continue;
                                    }
                                }else{ // Parola inserita non valida (non presente nel file), non conta come tentativo
                                    toClient="Invalid word, retry";
                                    out.println(toClient);
                                }
                            }
                            /*
                            * Assegnamento dei punteggi post partita: a prescindere si incrementa il numero di partite giocate.
                            * Sia che l'utente vinca o perda si vanno a ricalcolare le varie statistiche 
                            */ 
                            currentUser.increaseGamesPlayed();
                            if(wordGuessed){
                                currentUser.increaseStreak();
                                currentUser.calculateWS();
                                currentUser.increaseTotalWins();
                                currentUser.setHasGuessed(true);
                            }else{
                                // ha perso quindi resetto la streak
                                currentUser.setHasGuessed(false);
                                currentUser.calculateWS();
                                currentUser.setInStreak(false);
                                currentUser.setStreak(0);
                            }
                            currentUser.addTriesToList(currentUser.getTries());
                            currentUser.setLastTries(currentUser.getTries());
                            currentUser.setTries(0);
                            currentUser.calculateGuessDistribution();
                            currentUser.calculateWinRate();
                            currentUser.setIsPlaying(false);
                            currentUser.setCanPlay(false);

                        }else if(currentUser.getIsLogged() && !currentUser.getCanPlay()){ // Un utente prova a giocare di nuovo con la stessa parola
                            toClient="SERVER: You have already played with this word.";
                            out.println(toClient);

                        }else{ // Un utente prova a giocare senza avere fatto prima il login 
                            toClient="SERVER: You have to login first in order to play.";
                            out.println(toClient);
                        }
                        
                    break start;

                    case "logout": // L'utente desidera fare logout
                    /**
                     * Per poter fare logout ovviamente un utente deve essere loggato e currentUser quindi diverso da null.
                     * Si settano i campi isLogged e canPlay a false dell'utente.
                     */
                        if(currentUser!=null && currentUser.getIsLogged()){

                            currentUser.setIsLogged(false);
                            currentUser.setCanPlay(false);

                            out.println("LOGOUT: Success.");

                            break start;
                        }else{
                            out.println("LOGOUT: You have to login first");
                        }
                    break start;

                    case "stats", "send stats", "send me statistics", "send me stats": // Un utente desidera verificare le sue statistiche
                        /**
                         * Un utente per poter vedere le sue statistiche deve ssere loggato ed aver almeno fatto una partita da quando si è registrato.
                         * Il messaggio del server contiene tutte le statistiche relative a quell'utente.
                         */
                        if(currentUser != null && currentUser.getIsLogged() && currentUser.getGamesPlayed()!=0){
                            out.println("STATISTICS: "+currentUser.getUsername()
                            + ", Win Rate: "+ String.format("%.2f",currentUser.getWinRate())
                            + ", Last winstreak: " + currentUser.getLastWS() 
                            + ", Max winstreak: " + currentUser.getMaxWS() 
                            + ", Guess distribution: " 
                            + String.format("%.2f",currentUser.getGuessDistribution())+".");
                        }else{
                            out.println("ERROR: Cannot send statistics, you have to play one game first.");
                        }
                    break;
                    
                    case "share": // L'utente desidera condividere le proprie statistiche (dell'ultima partita fatta) nel gruppo multicast
                        /**
                         * Come per il comando precedente, un utente può condividere le proprie statistiche solo se è loggato e 
                         * ha giocato almeno una partita da quando si è registrato.
                         * Questa procedura una volta che costruisce il mesasggio in base all'esito dell'ultima partita, lo invia nel gruppo.
                         */
                        String result ="";
                        if(currentUser != null && currentUser.getGamesPlayed()!=0 && currentUser.getIsLogged()){
                            System.out.println("WORDLE: Sharing "+ currentUser.getUsername() +" last game stats...");
                            if(currentUser.getHasGuessed()){
                                result="won";
                            }else{
                                result="lost";
                            }
                            String messageUDP = "User: "+ currentUser.getUsername() 
                                                + " has " + result 
                                                + " with "+ (currentUser.getLastTries())
                                                + " tries.";
                            byte[] buf = messageUDP.getBytes();
                            DatagramPacket shareResults= new DatagramPacket(buf, buf.length, group, multicastPort);
                            multicastSocket.send(shareResults);
                            out.println("SHARE: Result sent succesfully.");
                            // System.out.println("SERVER: Result sent.");
                            break start;
                        }else{
                            out.println("SHARE ERROR: Can't send statistics. You are not logged in or you have never played before.");
                            break start;
                        }

                    case "show", "show me sharing": // L'utente desidera vedere le statistiche condivise dagli altri utenti nel gruppo
                        /**
                         * Come per i comandi precedenti, l'utente deve essere loggato per poter vedere le statistiche degli altri.
                         * In caso contrario invia un messaggio di errore.
                         */
                        if(currentUser != null && currentUser.getIsLogged()){
                            out.println("SHOW ME SHARING: Request sent.");
                            break start;
                        }else{
                            out.println("SHOW ME SHARING ERROR: Request error, you have to login first.");
                            break start;
                        }
                        
                    case "exit": // L'utente vuole disconnetersi dal server ed uscire dal gioco.
                        /**
                         * Questa procedura setta i campi dell'utente di login e gioco a false.
                         * Ciò serve nel caso in cui l'utente esce senza fare logout.
                         * In seguito si chiude la socket di comunicazione con il client che vuole disconnettersi.
                         */
                        System.out.println("SERVER WORDLE: Client "
                        + clientSocket.getInetAddress()+" just disconnected");
                        currentUser.setCanPlay(false);
                        currentUser.setIsLogged(false);
                        clientSocket.close(); 
                        
                    break start;
                    default: // L'utente ha inserito un comando non valido.
                        out.println("ERROR: Unknow command. Insert a valid command");
                    break start;
                    
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Metodo buildResult(String guess): utilizzando uno Stringbuilder costruisce la stringa codificata nel formato "vvgg--" da inviare in risposta
     * al client.
     * Carattere per carattere confronta la parola che l'utente ha inserito con la parola segreta.
     * 
     * v -> verde, lettera giusta nella posizione giusta
     * g -> giallo, lettera giusta nella posizione sbagliata
     * - -> bianco, lettera non presente nella parola segreta
     * 
     * La parola codificata in questo modo serve al client che poi andrà a colorarla per l'output grafico.
     * 
     * @param guess parola inserita dall'utente
     * @return parola codificata nel formato "vvgg--"
     */
    private static String buildResult(String guess){
        StringBuilder result = new StringBuilder(guess);
        String word = secretWord.toString();
        //System.out.println(result);
        String cguess = null;
        String cword = null;
        int i=0;
        int j=0;
    
        while(i<guess.length()){    
            cguess = guess.substring(i, i+1);
            cword = word.substring(j, j+1);
            if(word.contains(cguess)){
                result.setCharAt(i, 'g');
            }
            if(cguess.equals(cword)){
                result.setCharAt(i, 'v');
            }
            if(!cguess.equals(cword) && !word.contains(cguess)){
                result.setCharAt(i, '-');
            }
            i++;
            j++;
        }
        return result.toString();
    }

    /**
     * Metodo isValid(String guess): metodo che controlla che la parola inserita in fase di gioco dall'utente sia valida.
     * Una parola per essere valida deve essere presente nel file "words.txt".
     * 
     * @param guess parola inserita dall'utente
     * @return true se la parola è presente nel file, false altrienti
     */
    private static boolean isValid(String guess){
        try (RandomAccessFile raf = new RandomAccessFile("../../Words/words.txt", "r")) {
        // Search key
            int pos = binarySearch(raf, guess);
        
            // Return true or false
            if (pos != -1) return true;
            else return false;
        
            }catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        return false;
    }

    /**
     * Metodo binarySearch(RandomAccessFile raf, String key): metodo che tramite la ricerca binaria cerca la parola nel file delle parole.
     * Se la trova restituisce la sua posizione, questo metodo è utilizzato da isValid per controllare che la parola effettivamente esiste nel file.
     * Questa ricerca è possibile grazie al fatto che è il file ordinato in modo alfabetico ed è la ricerca più efficiente.
     * 
     * @param raf per poter accedere al file delle parole
     * @param key parola da cercare
     * @return la posizione della parola se è presente, -1 altrimenti.
     * @throws Exception
     */
    private static int binarySearch(RandomAccessFile raf, String key) throws Exception{

        int numElements = (((int) raf.length())-30824)/10; // number of elements
        int lower = 0, upper = numElements, mid=0; // indexes for search
        int index;
        String w="";

        while (lower <= upper) {
            mid = (lower + upper) / 2;
            index= mid*11-11;
            if(index>0)
                raf.seek(index);
            else return -1;
                w = raf.readLine();
            int comparison = w.compareTo(key);

            if (comparison == 0){
                // found it
                return (index);
            }
            else if (comparison < 0){
                // w comes after key
                lower = mid + 1;
            }
            else {
                // w comes before key
                upper = mid - 1;
            }
        }
        return -1;
    }

    /**
     * Metodo readConfig(): metodo per leggere il file di configurazione del server di gioco.
     * 
     * @throws FileNotFoundException se il file non esiste.
     * @throws IOException se ci sono probleminella lettura del file.
     */
    public static void readConfig() throws FileNotFoundException, IOException {
        InputStream input = new FileInputStream(configFile);
        Properties prop = new Properties();
        prop.load(input);
        multicastPort = Integer.parseInt(prop.getProperty("multicastPort"));
        multicastHost = prop.getProperty("multicastHost");
        input.close();
    }
    
    /**
     * Metodo readFileAsString(String file): metodo per la lettura di un file di properties come stringa.
     * 
     * @param file percorso del file di properties
     * @return file letto come stringa
     * @throws Exception se ci sono problemi durante la sua lettura
     */
    public static String readFileAsString(String file)throws Exception{
        return new String(Files.readAllBytes(Paths.get(file)));
    }
}