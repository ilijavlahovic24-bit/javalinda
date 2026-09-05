package rs.ac.bg.etf.kdp.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsira tekstualnu datoteku sa parametrima posla - alternativa GUI-ju
 * (spec: "Ovi parametri mogu da se zadaju ili putem korisnickog interfejsa
 * ili putem tekstualne datoteke"). Format, jedan kljuc=vrednost po liniji:
 *
 *   jar=putanja/do/posao.jar
 *   mainClass=paket.GlavnaKlasa
 *   args=arg1 arg2 arg3
 *   in.imeNaStanici=lokalnaPutanja       (do 6 ovakvih linija)
 *   out=izlaz1.txt,izlaz2.txt            (do 6 imena, zarezom odvojena)
 * Prazne linije i linije koje pocinju sa # se ignorisu.
 */
public class JobConfigFile {

    private String jarPath;
    private String mainClassName;
    private String[] programArgs = new String[0];
    private final Map<String, String> inputFiles = new LinkedHashMap<>(); // ime na stanici -> lokalna putanja
    private String[] outputFileNames = new String[0];

    public static JobConfigFile parse(Path path) throws IOException {
        JobConfigFile config = new JobConfigFile();
        List<String> lines = Files.readAllLines(path);
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IOException("Neispravna linija (ocekuje se kljuc=vrednost): " + rawLine);
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();

            if ("jar".equals(key)) {
                config.jarPath = value;
            } else if ("mainClass".equals(key)) {
                config.mainClassName = value;
            } else if ("args".equals(key)) {
                config.programArgs = value.isEmpty() ? new String[0] : value.split("\\s+");
            } else if ("out".equals(key)) {
                config.outputFileNames = value.split(",");
            } else if (key.startsWith("in.")) {
                config.inputFiles.put(key.substring("in.".length()), value);
            } else {
                throw new IOException("Nepoznat kljuc u konfiguracionoj datoteci: " + key);
            }
        }

        validate(config, path);
        return config;
    }

    private static void validate(JobConfigFile config, Path path) throws IOException {
        List<String> problems = new ArrayList<>();
        if (config.jarPath == null) problems.add("nedostaje 'jar='");
        if (config.mainClassName == null) problems.add("nedostaje 'mainClass='");
        if (config.outputFileNames.length == 0) problems.add("nedostaje 'out='");
        if (config.outputFileNames.length > 6) problems.add("najvise 6 izlaznih fajlova je dozvoljeno");
        if (config.inputFiles.size() > 6) problems.add("najvise 6 ulaznih fajlova je dozvoljeno");
        if (!problems.isEmpty()) {
            throw new IOException("Neispravna konfiguraciona datoteka " + path + ": "
                    + String.join("; ", problems));
        }
    }

    public String getJarPath() {
        return jarPath;
    }

    public String getMainClassName() {
        return mainClassName;
    }

    public String[] getProgramArgs() {
        return programArgs;
    }

    public Map<String, String> getInputFiles() {
        return inputFiles;
    }

    public String[] getOutputFileNames() {
        return outputFileNames;
    }
}