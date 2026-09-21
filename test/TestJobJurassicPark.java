package test;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;

import rs.ac.bg.etf.kdp.linda.LindaSocketClient;

public class TestJobJurassicPark {
    private static final int NUM_CARS = 2;
    private static final int NUM_VISITORS = 4;

    public static void main(String[] args) throws Exception {
        try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
            for (int i = 0; i < NUM_CARS; i++) linda.eval("car-" + i, new CarRunnable(i));
            for (int i = 0; i < NUM_VISITORS; i++) linda.eval("visitor-" + i, new VisitorRunnable(i));

            int total = NUM_CARS + NUM_VISITORS;
            for (int i = 0; i < total; i++) {
                linda.in(new String[] { "done" });
            }
            Files.write(Paths.get("output.txt"),
                    (NUM_VISITORS + " posetilaca provozano sa " + NUM_CARS + " automobila").getBytes());
        }
        System.out.println("[JurassicPark] pokrenuto i zavrseno");
    }

    private static class CarRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int carId;

        CarRunnable(int carId) {
            this.carId = carId;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                for (int trip = 0; trip < 2; trip++) {
                    linda.out(new String[] { "free_car", String.valueOf(carId) });
                    linda.in(new String[] { "board", String.valueOf(carId) });
                    System.out.println("[Auto " + carId + "] vozi posetioca po parku");
                    Thread.sleep(200);
                    linda.out(new String[] { "ride_done", String.valueOf(carId) });
                }
                linda.out(new String[] { "done" }); // DODATO
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class VisitorRunnable implements Runnable, Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        VisitorRunnable(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
                System.out.println("[Posetilac " + id + "] obilazi muzej, ceka slobodan auto");

                String[] carT = new String[] { "free_car", null };
                linda.in(carT);
                int carId = Integer.parseInt(carT[1]);

                linda.out(new String[] { "board", String.valueOf(carId) });
                linda.in(new String[] { "ride_done", String.valueOf(carId) });

                System.out.println("[Posetilac " + id + "] zavrsio voznju u autu " + carId);
                linda.out(new String[] { "done" }); // DODATO
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}