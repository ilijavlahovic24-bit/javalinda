# Uputstvo za pokretanje — KDP Linda sistem

Pretpostavka: kod je u `src/`, kompajliraš u `out/`, radiš u PowerShell-u na
Windows-u (isto kao u dosadašnjem radu). Komande za Linux/Mac su iste uz
`/` umesto `\` i `:` umesto `;` u classpath-u — napomenuću gde je bitno.

---

## Deo A — Test na 2 uređaja (računar + laptop, ista WiFi mreža)

### A1. Kompajliranje (na SVAKOM uređaju posebno, ili kopiraj `out` folder)

```powershell
cd D:\putanja\do\projekta
javac -d out (Get-ChildItem -Recurse -Path src -Filter *.java | ForEach-Object { $_.FullName })
```

Najjednostavnije: kompajliraj na jednom uređaju, pa **ceo `out` folder**
kopiraj na drugi (USB, deljeni folder, cloud) — izbegneš mogućnost da se
verzije koda razmimoiđu.

### A2. Napravi `linda-lib.jar`

Ovo je jar koji se prosleđuje **svakom poslu** (i svakom eval() pomoćnom
procesu) da bi mogao da pristupi `Linda` biblioteci. Trenutno pošto
`LindaSocketClient` zavisi i od jedne klase iz `server` paketa
(`TupleSpaceServer.JobSetHandshake`), najjednostavnije rešenje za sad je da
`linda-lib.jar` sadrži **ceo** kompajlirani projekat, ne samo `linda`
paket — funkcionalno je isto, samo malo veći jar:

```powershell
jar cf linda-lib.jar -C out .
```

*(Napomena za kasnije čišćenje: `JobSetHandshake` bi trebalo premestiti iz
`server.TupleSpaceServer` u `protocol` paket kao samostalnu klasu — tako bi
`linda-lib.jar` mogao da sadrži samo `linda`+`protocol`+`net` pakete, bez
celog `server`/`worker` koda. Nije hitno, ali je čistije.)*

Kopiraj `linda-lib.jar` na **oba** uređaja (mora postojati i tamo gde je
worker, ne samo gde se kompajlira).

### A3. Nađi lokalne IP adrese

Na **oba** uređaja:

```powershell
ipconfig
```

Traži `IPv4 Address` pod aktivnom mrežnom karticom (obično
`Wireless LAN adapter Wi-Fi`) — nešto tipa `192.168.1.50`.

U primerima ispod: desktop = `192.168.1.50` (tu ide server), laptop =
`192.168.1.60` (tu ide radna stanica). Zameni svojim stvarnim adresama.

### A4. Pokreni server (na desktopu)

```powershell
java -cp out rs.ac.bg.etf.kdp.server.ServerMain 6000 6001 3000 192.168.1.50 --gui
```

Argumenti redom: registracioni port (6000), port prostora torki (6001),
heartbeat interval u ms (3000), adresa na kojoj server sluša za
`TupleSpaceServer` (mora biti **stvarna** IP adresa desktopa, ne
`localhost`, jer se worker/eval procesi na to povezuju spolja), `--gui`
otvara `ServerGui`.

**Windows Firewall**: prvi put će verovatno iskočiti dijalog — dozvoli
pristup za **privatne mreže** (Private networks), ne treba javne.

### A5. Pokreni radnu stanicu (na laptopu)

```powershell
java -cp out rs.ac.bg.etf.kdp.worker.WorkerMain 192.168.1.50 6000 192.168.1.50 6001 7000 4 linda-lib.jar 192.168.1.60 --gui
```

Argumenti redom: `serverHost` (desktop), registracioni port, `tupleSpaceHost`
(desktop, isti kao gore), `tupleSpacePort`, `dispatchPort` (7000 — port na
kom OVA stanica sluša pozive od servera; možeš i `0` za nasumičan, ali za
više stanica je lakše pratiti loga ako su fiksni), kapacitet (4), putanja
do `linda-lib.jar`, **sopstvena adresa laptopa** (ključno — ovo je popravka
od malopre, MORA biti stvarna IP adresa laptopa), `--gui` otvara `WorkerGui`.

Dozvoli i ovde firewall pristup za privatne mreže kad iskoči dijalog.

U serverovom logu (ili `ServerGui` tabeli) trebalo bi odmah da vidiš da se
stanica prijavila.

### A6. Pošalji test posao (sa bilo kog od dva uređaja)

Preko komandne linije:

```powershell
java -cp out rs.ac.bg.etf.kdp.client.ClientMain submit 192.168.1.50 6000 test-job.jar test.TestJob output.txt
```

Ili preko konfiguracione datoteke (`primer-posao.txt`, videti format iz
ranijeg dela rada):

```powershell
java -cp out rs.ac.bg.etf.kdp.client.ClientMain submit-file 192.168.1.50 6000 primer-posao.txt
```

Ili preko GUI-ja:

```powershell
java -cp out rs.ac.bg.etf.kdp.gui.ClientGui
```

(u GUI formi unesi `192.168.1.50` kao host, `6000` kao port)

### A7. Test failover-a

Dok je `submit` konekcija otvorena i posao se izvršava, fizički isključi
laptop (ili samo ugasi `WorkerMain` proces sa Ctrl+C) — trebalo bi da se za
par sekundi na `ClientMain`/`ClientGui` pojavi pitanje o prosleđivanju
posla drugoj stanici. Za ovaj test ti treba **bar dve** radne stanice žive
istovremeno (da ima gde da se redispatch-uje) — pokreni drugi `WorkerMain`
na desktopu samom (drugi terminal, drugi `dispatchPort`, npr. 7001) da
imaš rezervu dok testiraš samo sa dva fizička uređaja.

---

## Deo B — Skaliranje na 16 računara (laboratorija)

### B1. Priprema pre dolaska u salu

- Kompajliraj **jednom**, spakuj `out/` folder i `linda-lib.jar` na USB ili
  deljeni mrežni resurs koji je dostupan svim računarima u laboratoriji.
- Sastavi unapred **spisak IP adresa** svih 16 računara (laborant će
  verovatno moći da ti kaže opseg/šemu — fakultetske laboratorije često
  imaju fiksne ili predvidljive IP adrese po računaru).
- Odluči unapred: **jedan** računar je server (recimo prvi u nizu), ostalih
  15 su radne stanice. Zapiši IP adresu servera pre nego što krenete.

### B2. Redosled pokretanja

Isto kao za dva uređaja, samo ponovljeno 15 puta za radne stanice:

```powershell
# na racunaru koji je SERVER (npr. IP 10.0.0.1):
java -cp out rs.ac.bg.etf.kdp.server.ServerMain 6000 6001 3000 10.0.0.1 --gui

# na SVAKOM od 15 racunara koji su radne stanice (menjaj IP na kraju za svaki):
java -cp out rs.ac.bg.etf.kdp.worker.WorkerMain 10.0.0.1 6000 10.0.0.1 6001 7000 4 linda-lib.jar 10.0.0.2 --gui
java -cp out rs.ac.bg.etf.kdp.worker.WorkerMain 10.0.0.1 6000 10.0.0.1 6001 7000 4 linda-lib.jar 10.0.0.3 --gui
# ... i tako dalje, poslednji argument pre --gui je UVEK sopstvena IP adresa tog racunara
```

`dispatchPort` (7000) može ostati isti broj na svakoj stanici jer je to
port **na toj stanici**, ne globalno jedinstven broj — svaki računar sluša
na sopstvenom 7000. Ako te nervira da otkucavaš 16 komandi, napravi malu
`.bat` skriptu po računaru unapred (videti B4).

### B3. Test scenario koji je opisan (profesor gasi struju jedne po jedan)

- Pošalji dovoljno dugačak test posao (nešto što traje bar 20-30 sekundi,
  da profesor stigne da isključi struju pre nego što se posao sam završi —
  ako trenutni `TestJob` završava trenutno, dodaj `Thread.sleep(...)` u
  test-jar samo za ovu demonstraciju, ili koristi posao koji stvarno nešto
  računa).
- Klijent drži `submit` konekciju otvorenu (obavezno — GUI ili terminal,
  ne zatvaraj ga).
- Kad profesor ugasi struju jednoj stanici: heartbeat prestaje → server
  detektuje za ~6-9 sekundi → `WorkerFailureNotice` stiže na klijenta →
  odgovoriš "prosledi drugoj stanici" → posao nastavlja na jednoj od
  preostalih 14 stanica.
- Da ovo radi pouzdano više puta zaredom (profesor gasi "jedan po jedan"),
  **mora uvek postojati bar jedna slobodna stanica** — sa 16 stanica i
  profesorom koji gasi jednu po jednu, imaš dosta rezerve dok ne dođe do
  poslednjih par.

### B4. Automatizacija (preporučeno, da ne kucaš ručno po 16 puta)

Napravi jednu `start-worker.bat` (Windows) na deljenom resursu koji svaki
računar pokreće, sa **sopstvenom** IP adresom kao jedini parametar koji se
menja:

```batch
@echo off
REM Upotreba: start-worker.bat <sopstvenaIP>
java -cp out rs.ac.bg.etf.kdp.worker.WorkerMain 10.0.0.1 6000 10.0.0.1 6001 7000 4 linda-lib.jar %1 --gui
```

Na svakom računaru samo:

```powershell
.\start-worker.bat 10.0.0.5
```

(zameni brojem koji odgovara tom računaru)

### B5. Provere pre same odbrane

- Proveri da Windows Firewall na **svih 16** računara dozvoljava Java na
  privatnoj mreži — ako laboratorija ima grupnu politiku (Group Policy) koja
  blokira nepoznate aplikacije, ovo vredi proveriti sa dežurnim laborantom
  unapred (spec eksplicitno pominje dogovor sa laborantom bar 3 dana pre
  odbrane — iskoristi taj termin i za ovo).
- Proveri da svi računari imaju **istu verziju** `out`/`linda-lib.jar` —
  ako neko ima stariju verziju koda, dobićeš teško objašnjive greške.
- Proveri da `heartbeatIntervalMs` (3000ms, 2 propuštena intervala = ~6-9s
  do detekcije) daje razuman balans za demonstraciju — ni prebrzo (lažne
  detekcije zbog trenutnog zastoja mreže) ni presporo (profesor čeka
  predugo). Ako želiš brži odziv za demonstraciju, smanji na npr. 1500ms.

---

## Brz podsetnik — redosled komandi za jednu sesiju (2 uređaja)

```powershell
# 1) kompajliranje (jednom)
javac -d out (Get-ChildItem -Recurse -Path src -Filter *.java | ForEach-Object { $_.FullName })
jar cf linda-lib.jar -C out .

# 2) server (desktop, IP 192.168.1.50)
java -cp out rs.ac.bg.etf.kdp.server.ServerMain 6000 6001 3000 192.168.1.50 --gui

# 3) radna stanica (laptop, IP 192.168.1.60)
java -cp out rs.ac.bg.etf.kdp.worker.WorkerMain 192.168.1.50 6000 192.168.1.50 6001 7000 4 linda-lib.jar 192.168.1.60 --gui

# 4) klijent (bilo koji uredjaj)
java -cp out rs.ac.bg.etf.kdp.client.ClientMain submit 192.168.1.50 6000 test-job.jar test.TestJob output.txt
```
