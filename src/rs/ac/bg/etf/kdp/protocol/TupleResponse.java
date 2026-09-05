package rs.ac.bg.etf.kdp.protocol;

import java.io.Serializable;

/**
 * Odgovor na TupleMessage. tuple nosi popunjen sablon (za in/rd/inp/rdp -
 * isti niz koji je klijent poslao, sa null poljima zamenjenim
 * pronadjenim vrednostima). found je relevantno samo za INP/RDP (da li je
 * uopste nadjena torka) - za OUT/IN/RD se ignorise (uvek true posle
 * uspesnog poziva, jer su blokirajuce ili nemaju povratnu vrednost).
 */
public class TupleResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean found;
    private final String[] tuple;

    public TupleResponse(boolean found, String[] tuple) {
        this.found = found;
        this.tuple = tuple;
    }

    public boolean isFound() {
        return found;
    }

    public String[] getTuple() {
        return tuple;
    }
}