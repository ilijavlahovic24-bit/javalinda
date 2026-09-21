package test;

import java.io.Serializable;
import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

/** Vise niti istovremeno uplacuje/isplacuje sa istog racuna - medjusobno iskljucivanje uz proveru sredstava. */
public class TestJobSavingsAccount {

    public static void main(String[] args) throws Exception {
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            linda.out(new String[] { "balance", "1000" });

            linda.eval("deposit-0", new TransactionRunnable(0, true, 200));
            linda.eval("withdraw-0", new TransactionRunnable(1, false, 300));
            linda.eval("deposit-1", new TransactionRunnable(2, true, 50));
            linda.eval("withdraw-1", new TransactionRunnable(3, false, 5000));

            for (int i = 0; i < 4; i++) {
                linda.in(new String[] { "done" });
            }

            String[] finalBalance = new String[] { "balance", null };
            linda.rd(finalBalance);
            java.nio.file.Files.write(java.nio.file.Paths.get("output.txt"),
                    ("konacno stanje racuna: " + finalBalance[1]).getBytes());
        }
        System.out.println("[SavingsAccount] pokrenuto i zavrseno");
    }

    private static class TransactionRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;
        private final boolean deposit;
        private final int amount;

        TransactionRunnable(int id, boolean deposit, int amount) {
            this.id = id;
            this.deposit = deposit;
            this.amount = amount;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int attempt = 0; attempt < 10; attempt++) {
                    String[] balT = new String[] { "balance", null };
                    linda.in(balT);
                    int balance = Integer.parseInt(balT[1]);

                    if (deposit) {
                        linda.out(new String[] { "balance", String.valueOf(balance + amount) });
                        System.out.println("[Transakcija " + id + "] uplata " + amount + ", novo stanje " + (balance + amount));
                        linda.out(new String[] { "done" }); // DODATO
                        return;
                    }
                    if (balance >= amount) {
                        linda.out(new String[] { "balance", String.valueOf(balance - amount) });
                        System.out.println("[Transakcija " + id + "] isplata " + amount + ", novo stanje " + (balance - amount));
                        linda.out(new String[] { "done" }); // DODATO
                        return;
                    }

                    // nedovoljno sredstava - vrati stanje kakvo je bilo i pokusaj ponovo
                    linda.out(new String[] { "balance", String.valueOf(balance) });
                    System.out.println("[Transakcija " + id + "] nedovoljno sredstava, pokusaj " + attempt);
                    Thread.sleep(300);
                }
                System.out.println("[Transakcija " + id + "] odustao posle 10 pokusaja");
                linda.out(new String[] { "done" }); // DODATO - i "odustao" se racuna kao zavrseno
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}