package test;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

public class TestJobChildCare {
    private static final int KIDS_PER_TEACHER = 3;

    public static void main(String[] args) throws Exception {
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.out(new String[] { "state", "0", "0", "0" });

            linda.eval("teacher-arrive-0", new TeacherComesRunnable(0));
            linda.eval("teacher-arrive-1", new TeacherComesRunnable(1));
            linda.eval("bring-0", new BringChildrenRunnable(0, 4));
            linda.eval("bring-1", new BringChildrenRunnable(1, 10));
            linda.eval("teacher-leave-0", new TeacherGoesHomeRunnable(0));
            linda.eval("teacher-arrive-2", new TeacherComesRunnable(2));
            linda.eval("take-0", new TakeChildrenHomeRunnable(0, 2));

            for (int i = 0; i < 7; i++) {
                linda.in(new String[] { "done" });
            }
            Files.write(Paths.get("output.txt"), ("svih 7 akcija u vrticu zavrseno").getBytes());
        }
        System.out.println("[ChildCare] pokrenuto i zavrseno");
    }

    private static String[] readState(LindaSocketClient linda) throws Exception {
        String[] t = new String[] { "state", null, null, null };
        linda.in(t);
        return t;
    }

    private static class BringChildrenRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;
        private final int num;

        BringChildrenRunnable(int id, int num) {
            this.id = id;
            this.num = num;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                String[] s = readState(linda);
                int children = Integer.parseInt(s[1]);
                int teachers = Integer.parseInt(s[2]);
                int waiting = Integer.parseInt(s[3]);

                boolean ok = children + num <= teachers * KIDS_PER_TEACHER;
                if (ok) children += num;
                linda.out(new String[] { "state", String.valueOf(children),
                        String.valueOf(teachers), String.valueOf(waiting) });

                System.out.println("[Dovodjenje " + id + "] " + num + " dece - "
                        + (ok ? "primljeno" : "odbijeno, nema mesta"));
                linda.out(new String[] { "done" }); // DODATO
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class TakeChildrenHomeRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;
        private final int num;

        TakeChildrenHomeRunnable(int id, int num) {
            this.id = id;
            this.num = num;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                String[] s = readState(linda);
                int children = Integer.parseInt(s[1]);
                int teachers = Integer.parseInt(s[2]);
                int waiting = Integer.parseInt(s[3]);

                if (num <= children) children -= num;

                while (waiting > 0 && children <= (teachers - 1) * KIDS_PER_TEACHER) {
                    waiting--;
                    teachers--;
                    linda.out(new String[] { "teacherLeave" });
                }
                linda.out(new String[] { "state", String.valueOf(children),
                        String.valueOf(teachers), String.valueOf(waiting) });

                System.out.println("[Odvodjenje " + id + "] " + num + " dece odvedeno kuci");
                linda.out(new String[] { "done" }); // DODATO
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class TeacherComesRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        TeacherComesRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                String[] s = readState(linda);
                int children = Integer.parseInt(s[1]);
                int teachers = Integer.parseInt(s[2]);
                int waiting = Integer.parseInt(s[3]);

                if (waiting > 0) {
                    waiting--;
                    linda.out(new String[] { "teacherLeave" });
                } else {
                    teachers++;
                }
                linda.out(new String[] { "state", String.valueOf(children),
                        String.valueOf(teachers), String.valueOf(waiting) });

                System.out.println("[Vaspitacica " + id + "] dosla na posao");
                linda.out(new String[] { "done" }); // DODATO
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class TeacherGoesHomeRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        TeacherGoesHomeRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                String[] s = readState(linda);
                int children = Integer.parseInt(s[1]);
                int teachers = Integer.parseInt(s[2]);
                int waiting = Integer.parseInt(s[3]);

                if (children <= (teachers - 1) * KIDS_PER_TEACHER) {
                    teachers--;
                    linda.out(new String[] { "state", String.valueOf(children),
                            String.valueOf(teachers), String.valueOf(waiting) });
                    System.out.println("[Vaspitacica " + id + "] otisla kuci odmah");
                } else {
                    waiting++;
                    linda.out(new String[] { "state", String.valueOf(children),
                            String.valueOf(teachers), String.valueOf(waiting) });
                    System.out.println("[Vaspitacica " + id + "] ceka da moze da ide kuci");
                    linda.in(new String[] { "teacherLeave" });
                    System.out.println("[Vaspitacica " + id + "] sada moze da ide kuci");
                }
                linda.out(new String[] { "done" }); // DODATO
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}