package rs.ac.bg.etf.kdp.linda;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Serijalizacija/deserijalizacija Runnable-a za eval(). Runnable MORA implementirati Serializable -
 * ako referencira Linda pristup, to ne moze
 * biti LindaSocketClient direktno (nije Serializable, umesto toga Runnable implementacija treba da
 * primi host/port/jobSetId kao svoja (Serializable) polja i sama napravi
 * NOVU LindaSocketClient konekciju u run() metodi, na ciljnom procesu.
 *
 */
public final class EvalTask {

    private EvalTask() {
    }

    public static byte[] serialize(Runnable runnable) {
        if (!(runnable instanceof Serializable)) {
            throw new IllegalArgumentException(
                    "Runnable prosledjen eval()-u mora implementirati Serializable");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
                out.writeObject(runnable);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new LindaRuntimeException("Serijalizacija eval() Runnable-a nije uspela", e);
        }
    }

    /**
     * loader mora biti ClassLoader koji vidi klase iz korisnickog jar-a
     * (isti jar iz kog se izvrsava trenutni proces) - koristi se na strani
     * pomocnog eval() procesa, ne na serveru/radnoj stanici koji samo
     * prosledjuju bajtove.
     */
    public static Runnable deserialize(byte[] bytes, ClassLoader loader) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
            @Override
            protected Class<?> resolveClass(java.io.ObjectStreamClass desc)
                    throws IOException, ClassNotFoundException {
                return Class.forName(desc.getName(), false, loader);
            }
        }) {
            return (Runnable) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new LindaRuntimeException("Deserijalizacija eval() Runnable-a nije uspela", e);
        }
    }
}