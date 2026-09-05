package rs.ac.bg.etf.kdp.net;

/**
 * Funkcionalni interfejs za obradu jedne konekcije - implementacija radi
 * petlju receive()/send() dok konekcija ne pukne ili logika ne odluci da
 * je zavrsena. MessageServer poziva ovo u sopstvenoj niti po konekciji.
 */
@FunctionalInterface
public interface ConnectionHandler {
    void handle(MessageConnection connection) throws Exception;
}