import java.util.ArrayList;

public class User {

  // Attributi per la registrazione e il login
  private String username;
  private String password;

  // Attributi (variabili e strutture dati) per il calcolo delle statistiche e booleani per ulteriori controlli (settate a false di default).
  private int gamesPlayed;
  private int streak;
  private int totalWins;
  private int triesInGame;
  private int triesLastGame;
  private int lastws;
  private int maxws;
  private double winRate;
  private double guessDistribution;
  private boolean isLogged = false;
  private boolean inStreak = false;
  private boolean canPlay = false;
  private boolean isPlaying = false;
  private boolean hasGuessed = false;
  private ArrayList<Integer> triesList = null;


  // Costrutture di un nuovo utente, le variabili intere iniziali sono settate a 0 e l'array dei tentativi vuoto.
  public User (String username, String password) {
    this.username=username;
    this.password=password;
    this.gamesPlayed=0;
    this.streak=0;
    this.winRate=0;
    this.totalWins=0;
    this.triesInGame=0;
    this.lastws=0;
    this.maxws=0;
    this.winRate=0;
    this.guessDistribution=0;
    this.triesList= new ArrayList<Integer>();
  }

  // Getters e setters dei campi privati 
  public String getUsername(){
    return this.username;   
  }
  
  public String getPassword(){
    return this.password;   
  } 

  public void setGamesPlayed(int gamesPlayed){
    this.gamesPlayed=gamesPlayed;
  }

  public int getGamesPlayed(){
    return gamesPlayed;
  } 

  public void setStreak(int streak){
    this.streak=streak;
  }

  public int getTries(){
    return triesInGame;
  } 

  public void setTries(int tries){
    this.triesInGame=tries;
  } 

  public int getLastTries(){
    return triesLastGame;
  } 

  public void setLastTries(int tries){
    this.triesLastGame=tries;
  }
 
  public int getStreak(){
    return this.streak;
  }

  public void setWinRate(double winRate){
    this.winRate=winRate;
  }

  public double getWinRate(){
    return this.winRate;
  }

  public void setTotalWins(int totalWins){
    this.totalWins=totalWins;
  }

  public int getTotalWins(){
    return this.totalWins;
  }

  public void setInStreak(boolean inStreak){
    this.inStreak=inStreak;
  }

  public boolean getInStreak(){
    return this.inStreak;
  }

  public boolean getIsPlaying(){
    return this.isPlaying;
  }

  public void setIsPlaying(boolean isPlaying){
    this.isPlaying=isPlaying;
  }

  public boolean getIsLogged(){
    return this.isLogged;
  }

  public void setIsLogged(boolean isLogged){
    this.isLogged=isLogged;
  }

  public boolean getCanPlay(){
    return this.canPlay;
  }

  public void setCanPlay(boolean canPlay){
    this.canPlay=canPlay;
  }

  public void addTriesToList(int tries){
    this.triesList.add(tries);
  }

  public ArrayList<Integer> getTriesList(){
    return triesList;
  }

  public void setGuessDistribution(double gd){
    this.guessDistribution=gd;
  }

  public double getGuessDistribution(){
    return this.guessDistribution;
  }

  public void setLastWS(int ws){
    this.lastws=this.streak;
  }

  public int getLastWS(){
    return this.lastws;
  }

  public void setMaxWS(int ws){
    this.maxws=ws;
  }

  public int getMaxWS(){
    return this.maxws;
  }

  public void setHasGuessed(boolean result){
    this.hasGuessed=result;
  }

  public boolean getHasGuessed(){
    return this.hasGuessed;
  }

  // Calcoli per le statistiche dell'utente.

  public void increaseGamesPlayed(){
    this.gamesPlayed+=1;
  }

  public void increaseTotalWins(){
    this.totalWins+=1;
    if(!inStreak){
      inStreak=true;
    }
  }

  public void increaseStreak(){
    if(this.inStreak){
      if(this.streak==this.maxws){
        this.maxws+=1;
      }
      this.streak+=1;
    }else{
      this.streak=1;
      inStreak=true;
    }
  }

  public void calculateWinRate(){
    double wr = (double)totalWins/(double)gamesPlayed * 100;
    this.setWinRate(wr);
  }

  public double calculateGuessDistribution(){
    double sum=0;
    for(int n : triesList){
      sum+=n;
    }
    this.setGuessDistribution(sum/(double)triesList.size()); 
    return this.guessDistribution;
  }

  public void calculateWS(){
    this.lastws = this.streak;
    if(this.getMaxWS()<=this.streak){
      this.setMaxWS(streak);
    }
  }

  // Metodo equals per il confronto tra utenti.
  @Override
  public boolean equals(Object obj) {
    if(!(obj instanceof User)) {
      return false;
    }
    User u = (User)obj;
    return this.username.equals(u.getUsername()) && this.password.equals(u.getPassword());
  }

}