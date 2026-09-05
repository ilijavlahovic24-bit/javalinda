# Komande — pokretanje sistema i pravljenje test-poslova

## 1) Kompajliranje projekta (posle svake izmene koda)

```powershell
cd D:\Skola\"6. semestar"\"Konkurentno I Distribuirano Programiranje"\Projekat\LindaRMI\LindaRMI
javac -d out (Get-ChildItem -Recurse -Path src -Filter *.java | ForEach-Object { $_.FullName })
```

## 2) Pravljenje `linda-lib.jar` (posle svake izmene koda)

```powershell
jar cf linda-lib.jar -C out .
```

---

## 3) Pokretanje na JEDNOM računaru (loopback)

```powershell
# terminal 1 - server
java -cp out rs.ac.bg.etf.kdp.server.ServerMain 6000 6001 3000 localhost --gui

# terminal 2 - radna stanica
java -cp out rs.ac.bg.etf.kdp.worker.WorkerMain localhost 6000 localhost 6001 7000 4 linda-lib.jar localhost --gui

# terminal 3 - druga radna stanica (za test failover-a, drugi dispatchPort)
java -cp out rs.ac.bg.etf.kdp.worker.WorkerMain localhost 6000 localhost 6001 7001 4 linda-lib.jar localhost --gui

# terminal 4 - klijent (komandna linija)
java -cp out rs.ac.bg.etf.kdp.client.ClientMain submit localhost 6000 test-job-v2.jar test.TestJobV2 output.txt

# ili klijent preko konfiguracione datoteke
java -cp out rs.ac.bg.etf.kdp.client.ClientMain submit-file localhost 6000 primer-posao.txt

# ili klijent preko GUI-ja
java -cp out rs.ac.bg.etf.kdp.gui.ClientGui
```

---

## 4) Pokretanje na DVA uređaja (računar + laptop, ista WiFi mreža)

Prvo na oba: `ipconfig` → nađi `IPv4 Address`. Primer: desktop `192.168.1.50`,
laptop `192.168.1.60` — zameni svojim.

```powershell
# NA DESKTOPU - server
java -cp out rs.ac.bg.etf.kdp.server.ServerMain 6000 6001 3000 192.168.1.50 --gui

# NA LAPTOPU - radna stanica
java -cp out rs.ac.bg.etf.kdp.worker.WorkerMain 192.168.1.50 6000 192.168.1.50 6001 7000 4 linda-lib.jar 192.168.1.60 --gui

# NA DESKTOPU - druga radna stanica (rezerva za failover test)
java -cp out rs.ac.bg.etf.kdp.worker.WorkerMain 192.168.1.50 6000 192.168.1.50 6001 7001 4 linda-lib.jar 192.168.1.50 --gui

# SA BILO KOG uredjaja - klijent
java -cp out rs.ac.bg.etf.kdp.client.ClientMain submit 192.168.1.50 6000 test-job-v2.jar test.TestJobV2 output.txt
```

---

## 5) Pokretanje na 16 računara (laboratorija)

Server = `10.0.0.1` (primer, zameni pravom adresom), radne stanice
`10.0.0.2` do `10.0.0.16`.

```powershell
# RACUNAR #1 (server)
java -cp out rs.ac.bg.etf.kdp.server.ServerMain 6000 6001 3000 10.0.0.1 --gui

# RACUNAR #2 do #16 (radne stanice) - menjaj samo poslednji argument pre --gui
java -cp out rs.ac.bg.etf.kdp.worker.WorkerMain 10.0.0.1 6000 10.0.0.1 6001 7000 4 linda-lib.jar 10.0.0.2 --gui
java -cp out rs.ac.bg.etf.kdp.worker.WorkerMain 10.0.0.1 6000 10.0.0.1 6001 7000 4 linda-lib.jar 10.0.0.3 --gui
# ... itd do 10.0.0.16

# klijent (bilo koji racunar na mrezi)
java -cp out rs.ac.bg.etf.kdp.client.ClientMain submit 10.0.0.1 6000 test-job-v2.jar test.TestJobV2 output.txt
java -cp out rs.ac.bg.etf.kdp.client.ClientMain submit localhost 6000 test-job-long.jar test.TestJobLong output.txt
```

`start-worker.bat` za lakše ponavljanje po računaru:

```batch
@echo off
REM Upotreba: start-worker.bat <sopstvenaIP>
java -cp out rs.ac.bg.etf.kdp.worker.WorkerMain 10.0.0.1 6000 10.0.0.1 6001 7000 4 linda-lib.jar %1 --gui
```

```powershell
.\start-worker.bat 10.0.0.5
```

---

## 6) Dijagnostika kad posao završi sa FAILED

```powershell
# nadji poslednji privremeni folder posla
Get-ChildItem $env:TEMP\kdp-job-* | Sort-Object LastWriteTime -Descending | Select-Object -First 1

# pogledaj sta je proces ispisao (stack trace, gresku, itd.)
Get-Content "$env:TEMP\kdp-job-<ime_foldera>\stdout.log"
```

Provera zauzetog porta ako server/worker odbija da se pokrene:

```powershell
netstat -ano | findstr :6000
taskkill /PID <broj_procesa> /F
```

---

## 7) Pravljenje NOVOG test-posla (jar-a)

Svaki test-posao je obična Java klasa sa `main(String[] args)` metodom,
smeštena u koreni `test\` folder projekta (NE unutar `src\`, i NE unutar
`testjob-build\`).

### Korak po korak

1. Napravi `.java` fajl direktno u `test\` folderu (npr. `test\MojPosao.java`),
   sa `package test;` na vrhu (ili drugi paket po želji — samo budi
   dosledan sa onim što stavljaš u `-cp` i `jar cf`).

2. Ako posao treba `Linda` pristup (torke/`eval()`), koristi
   `LindaSocketClient.fromSystemProperties()` da dobiješ radnu konekciju —
   worker automatski postavlja potrebne sistemske propertije pri
   pokretanju procesa, ne treba ništa ručno prosleđivati:

   ```java
   try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
       linda.out(new String[] { "kljuc", "vrednost" });
       // ...
   }
   ```

3. Ako posao treba da pozove `eval()`, `Runnable` MORA implementirati
   `Serializable`, i mora sam da otvori sopstvenu `LindaSocketClient`
   konekciju unutar `run()` (ne nasleđuje konekciju od glavnog procesa —
   izvršava se u posebnom procesu):

   ```java
   private static class MojEval implements Runnable, Serializable {
       private static final long serialVersionUID = 1L;
       public void run() {
           try (LindaSocketClient linda = LindaSocketClient.fromSystemProperties()) {
               // ...
           } catch (Exception e) {
               e.printStackTrace();
           }
       }
   }
   ```

4. Ako posao treba da vrati izlazni fajl, piši ga u **trenutni radni
   direktorijum** (worker ga postavlja automatski na privremeni folder
   posla) — ne apsolutnu putanju:

   ```java
   Files.write(Paths.get("izlaz.txt"), sadrzaj.getBytes());
   ```

5. Kompajliraj (iz korena projekta, sa `out` na classpath-u zbog
   `Linda`/`LindaSocketClient` klasa):

   ```powershell
   javac -cp out -d testjob-build .\test\MojPosao.java
   ```

6. Spakuj u jar (ime jar-a proizvoljno, samo zapamti ga za `submit`):

   ```powershell
   jar cf moj-posao.jar -C testjob-build test
   ```

7. Proveri sadržaj jar-a (bitno ako imaš unutrašnje/anonimne klase, npr.
   `Runnable` za `eval()` — moraju biti unutra):

   ```powershell
   jar tf moj-posao.jar
   ```

8. Pošalji ga:

   ```powershell
   java -cp out rs.ac.bg.etf.kdp.client.ClientMain submit localhost 6000 moj-posao.jar test.MojPosao izlaz.txt
   ```

### Primer konfiguracione datoteke za `submit-file`

`primer-posao.txt`:

```
jar=moj-posao.jar
mainClass=test.MojPosao
args=arg1 arg2
in.podaci.txt=lokalna/putanja/do/podaci.txt
out=izlaz.txt
```

(najviše 6 `in.` linija, najviše 6 imena u `out=`, odvojenih zarezom)
