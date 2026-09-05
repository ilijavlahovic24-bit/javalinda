package rs.ac.bg.etf.kdp.linda;


/** Baca se pri neocekivanim greskama unutar linda paketa (prekid cekanja, RMI kvar i sl.). */
public class LindaRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LindaRuntimeException(String message) {
        super(message);
    }

    public LindaRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}