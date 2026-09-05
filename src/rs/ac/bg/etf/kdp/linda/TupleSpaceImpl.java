package rs.ac.bg.etf.kdp.linda;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Cista lokalna, thread-safe implementacija prostora torki. Ne zna nista
 * o mrezi
 * Blokirajuce operacije (in/rd) koriste monitor ovog objekta:
 * out() radi notifyAll() posle dodavanja, in()/rd() rade wait() u petlji
 * dok se ne pojavi poklapajuca torka (klasican producer/consumer obrazac,
 * petlja je neophodna zbog "spurious wakeup" i zbog konkurentskih in()
 * poziva koji mogu odneti torku pre nas).
 */
public class TupleSpaceImpl implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<String[]> tuples = new ArrayList<>();

    public synchronized void out(String[] tuple) {
        TupleMatcher.requireNoNulls(tuple);
        tuples.add(tuple.clone());
        notifyAll();
    }

    public synchronized void in(String[] template) {
        while (true) {
            int idx = findMatch(template);
            if (idx >= 0) {
                String[] found = tuples.remove(idx);
                TupleMatcher.fillWildcards(found, template);
                return;
            }
            awaitChange();
        }
    }

    public synchronized boolean inp(String[] template) {
        int idx = findMatch(template);
        if (idx < 0) {
            return false;
        }
        String[] found = tuples.remove(idx);
        TupleMatcher.fillWildcards(found, template);
        return true;
    }

    public synchronized void rd(String[] template) {
        while (true) {
            int idx = findMatch(template);
            if (idx >= 0) {
                TupleMatcher.fillWildcards(tuples.get(idx), template);
                return;
            }
            awaitChange();
        }
    }

    public synchronized boolean rdp(String[] template) {
        int idx = findMatch(template);
        if (idx < 0) {
            return false;
        }
        TupleMatcher.fillWildcards(tuples.get(idx), template);
        return true;
    }

    /** Trenutni broj torki - koristno za dijagnostiku/GUI, ne deo Linda API-ja. */
    public synchronized int size() {
        return tuples.size();
    }

    private int findMatch(String[] template) {
        for (int i = 0; i < tuples.size(); i++) {
            if (TupleMatcher.matches(tuples.get(i), template)) {
                return i;
            }
        }
        return -1;
    }

    private void awaitChange() {
        try {
            wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LindaRuntimeException(
                    "Cekanje na torku je prekinuto (interrupt)", e);
        }
    }
}