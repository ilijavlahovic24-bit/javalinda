package test;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

public class TestJobUnisexBathroom {

    public static void main(String[] args) throws Exception {
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.out(new String[] { "occupants", "0" });
            linda.out(new String[] { "gender", "none" });

            linda.eval("man-0", new PersonRunnable(0, "M", false));
            linda.eval("woman-0", new PersonRunnable(1, "W", false));
            linda.eval("child-0", new PersonRunnable(2, "M", true));
            linda.eval("man-1", new PersonRunnable(3, "M", false));

            for (int i = 0; i < 4; i++) {
                linda.in(new String[] { "done" });
            }
            Files.write(Paths.get("output.txt"), ("svi (4) prosli kroz kupatilo").getBytes());
        }
        System.out.println("[UnisexBathroom] pokrenuto i zavrseno");
    }

    private static class PersonRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;
        private final String gender;
        private final boolean isChild;

        PersonRunnable(int id, String gender, boolean isChild) {
            this.id = id;
            this.gender = gender;
            this.isChild = isChild;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                while (true) {
                    String[] genderT = new String[] { "gender", null };
                    linda.in(genderT);
                    String current = genderT[1];

                    boolean canEnter = current.equals("none")
                            || (!isChild && current.equals(gender))
                            || (isChild && !current.equals("none"));

                    if (!canEnter) {
                        linda.out(genderT);
                        Thread.sleep(200);
                        continue;
                    }

                    String[] occT = new String[] { "occupants", null };
                    linda.in(occT);
                    int occ = Integer.parseInt(occT[1]) + 1;
                    linda.out(new String[] { "occupants", String.valueOf(occ) });
                    linda.out(new String[] { "gender", isChild ? current : gender });

                    System.out.println((isChild ? "[Dete " : "[Odrasla osoba ") + id
                            + "] usao/la, trenutno " + occ + " u kupatilu");

                    Thread.sleep(300);

                    String[] occT2 = new String[] { "occupants", null };
                    linda.in(occT2);
                    int occ2 = Integer.parseInt(occT2[1]) - 1;
                    linda.out(new String[] { "occupants", String.valueOf(occ2) });

                    String[] genderT2 = new String[] { "gender", null };
                    linda.in(genderT2);
                    linda.out(new String[] { "gender", occ2 == 0 ? "none" : genderT2[1] });

                    System.out.println((isChild ? "[Dete " : "[Odrasla osoba ") + id + "] izasao/la");

                    linda.out(new String[] { "done" }); // DODATO
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}