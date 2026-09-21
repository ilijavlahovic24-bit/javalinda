package test;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

public class TestJobProducerConsumer {
    private static final int NUM_PRODUCERS = 2;
    private static final int NUM_CONSUMERS = 3;
    private static final int BUFFER_SIZE = 3;
    // NOVO: ukupan broj proizvedenih MORA da se poklopi sa ukupnim brojem
    // potrosenih, inace ili proizvodjac ceka "space" koji nikad ne stize,
    // ili potrosac ceka "buffer" stavku koja nikad ne stize.
    private static final int PRODUCER_ROUNDS = 6; // 2 proizvodjaca x 6 = 12
    private static final int CONSUMER_ROUNDS = 4; // 3 potrosaca x 4 = 12

    public static void main(String[] args) throws Exception {
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            for (int i = 0; i < BUFFER_SIZE; i++) linda.out(new String[] { "space" });
            linda.out(new String[] { "head", "0" });
            linda.out(new String[] { "tail", "0" });

            for (int i = 0; i < NUM_PRODUCERS; i++) linda.eval("producer-" + i, new ProducerRunnable());
            for (int i = 0; i < NUM_CONSUMERS; i++) linda.eval("consumer-" + i, new ConsumerRunnable());

            int total = NUM_PRODUCERS + NUM_CONSUMERS;
            for (int i = 0; i < total; i++) {
                linda.in(new String[] { "done" });
            }
            Files.write(Paths.get("output.txt"),
                    ((NUM_PRODUCERS * PRODUCER_ROUNDS) + " stavki proizvedeno i potroseno").getBytes());
        }
        System.out.println("[ProducerConsumer] pokrenuto i zavrseno");
    }

    private static class ProducerRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int round = 0; round < PRODUCER_ROUNDS; round++) {
                    int data = round;
                    linda.in(new String[] { "space" });
                    String[] headT = new String[] { "head", null };
                    linda.in(headT);
                    int head = Integer.parseInt(headT[1]);
                    linda.out(new String[] { "head", String.valueOf((head + 1) % BUFFER_SIZE) });
                    linda.out(new String[] { "buffer", String.valueOf(head), String.valueOf(data) });
                    System.out.println("[Producer] proizveo " + data + " na poziciju " + head);
                }
                linda.out(new String[] { "done" });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class ConsumerRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int round = 0; round < CONSUMER_ROUNDS; round++) {
                    String[] tailT = new String[] { "tail", null };
                    linda.in(tailT);
                    int tail = Integer.parseInt(tailT[1]);
                    linda.out(new String[] { "tail", String.valueOf((tail + 1) % BUFFER_SIZE) });

                    String[] item = new String[] { "buffer", String.valueOf(tail), null };
                    linda.in(item);
                    linda.out(new String[] { "space" });
                    System.out.println("[Consumer] potrosio " + item[2] + " sa pozicije " + tail);
                }
                linda.out(new String[] { "done" });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}