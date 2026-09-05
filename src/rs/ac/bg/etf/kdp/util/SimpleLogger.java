package rs.ac.bg.etf.kdp.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class SimpleLogger {

    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS");

    public synchronized void log(String component, String message) {
        System.out.println("[" + format.format(new Date()) + "] [" + component
                + "] " + message);
    }
}