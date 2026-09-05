package rs.ac.bg.etf.kdp.linda;

/**
 * Poredi torku sa sablonom po semantici iz Linda interfejsa: sablon i
 * torka moraju imati isti broj polja; svako polje sablona koje je null
 * je wildcard (poklapa se sa bilo cim); ostala polja se porede tacnom
 * jednakoscu stringova.
 *
 * Napomena: prava torka (ono sto se ubacuje sa out()) po specifikaciji
 * ne sme imati null polja - null je dozvoljen samo u sablonima (in/rd).
 */
public final class TupleMatcher {

    private TupleMatcher() {
    }

    public static boolean matches(String[] tuple, String[] template) {
        if (tuple == null || template == null) {
            return false;
        }
        if (tuple.length != template.length) {
            return false;
        }
        for (int i = 0; i < tuple.length; i++) {
            String templateField = template[i];
            if (templateField == null) {
                continue; // wildcard
            }
            if (!templateField.equals(tuple[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Kopira vrednosti iz pronadjene torke u null pozicije sablona -
     * "puni" sablon rezultatom pretrage, kao sto trazi specifikacija
     * za in()/rd().
     */
    public static void fillWildcards(String[] tuple, String[] template) {
        for (int i = 0; i < template.length; i++) {
            if (template[i] == null) {
                template[i] = tuple[i];
            }
        }
    }

    /** Validacija za out(): nijedno polje ne sme biti null. */
    public static void requireNoNulls(String[] tuple) {
        if (tuple == null) {
            throw new IllegalArgumentException("Torka ne sme biti null");
        }
        for (String field : tuple) {
            if (field == null) {
                throw new IllegalArgumentException(
                        "Nije dozvoljeno slati null u okviru torke (out)");
            }
        }
    }
}