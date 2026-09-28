# VoiceCommunication

Sprechverbindung zwischen Fahrerkabine und Instruktorraum des Simulators über das LAN.
Übertragen wird unkomprimiert (48 kHz, 16 Bit, Mono) in 10-ms-Paketen per UDP.

## Aufbau

Instruktorraum: ein Rechner mit Mikrofon und Lautsprecher (`ip` = Simulator-Rechner 1).
Simulator: Rechner 1 nur Lautsprecher (Mikrofon „Aus“), Rechner 2 nur Mikrofon (Lautsprecher „Aus“,
`ip` = Instruktorrechner). Die Anwendung läuft im Simulator also zweimal.

Auf beiden Rechnern läuft dieselbe Software. Jeder Rechner nimmt sein Mikrofon auf und sendet es an
den anderen (`ip` = Adresse der Gegenseite) und spielt gleichzeitig ab, was von dort ankommt.
Der `port` ist auf beiden Seiten gleich.

Beide Rechner müssen dieselbe Version haben (seit 2.0 UDP statt TCP, nicht kompatibel zu 1.x).

## Einrichtung pro Rechner

1. `client.cfg.example` als `client.cfg` neben die JAR kopieren und `ip` auf die Gegenseite setzen.
2. Einmal mit Konsole starten und Lautsprecher/Mikrofon auswählen – die Auswahl wird gespeichert.
   Nach der Auswahl kommt ein Testton bzw. eine Pegelanzeige, damit man das richtige Gerät erwischt.
   `-2` (= `default`) nutzt einfach das Standardgerät des Systems.
3. Windows-Firewall: eingehend **UDP** auf dem eingestellten Port erlauben (Standard 5000).
4. Feste IP-Adressen vergeben (oder Hostnamen in `ip` eintragen – er wird alle 30 s neu aufgelöst).
5. Energieoptionen: „Energiesparmodus für USB-Anschlüsse“ deaktivieren und im Geräte-Manager bei
   USB-Headset/ESP „Computer kann das Gerät ausschalten, um Energie zu sparen“ abwählen.

## Verhalten im Betrieb

- Fehlt das konfigurierte Audiogerät (z. B. Headset an anderem USB-Port) oder ist es belegt, wird ohne
  Rückfrage das Standardgerät des Systems verwendet und eine Warnung ausgegeben. Die Konfiguration
  bleibt unverändert, beim nächsten Start wird das eigentliche Gerät wieder gesucht.
- Die Wiedergabe bleibt dauerhaft geöffnet, damit beim Drücken der Sprechtaste nichts abgeschnitten wird.
- Ein Neustart der Gegenseite wird automatisch erkannt, ein Neustart dieser Seite ist nicht nötig.
- Ohne Konsole (Autostart per `javaw`) läuft die Software ohne Konsolenbefehle weiter.

## Sprechtaste (ESP)

Der ESP hängt per USB am Rechner, meldet sich als serieller Port an und sendet `True` (gedrückt)
bzw. `False` (losgelassen). Die Firmware wiederholt den Zustand alle 100 ms.

- `comPort=auto`: Die Software prüft alle Ports mit passender USB-Hersteller-ID (`serialVendorId`) und
  übernimmt nur den, der tatsächlich `True`/`False` sendet. Andere USB-Seriell-Adapter am Rechner
  (z. B. Simulator-Hardware mit demselben Wandler-Chip) werden dadurch nicht verwechselt.
  Ein fester Port wie `COM3` funktioniert weiterhin.
- Hängen mehrere ESPs am selben Rechner, mit `serialNumber` genau einen festlegen. Die Seriennummer
  steht beim Verbinden im Log (CP210x haben eine eindeutige, CH340 meist keine).
- Wird der ESP abgezogen, wird das Mikrofon sofort stumm geschaltet. Nach dem Einstecken verbindet sich
  die Software selbst neu.
- `pttTimeoutMs=500`: Kommt 500 ms lang keine Meldung, wird das Mikrofon stumm geschaltet.
  Nur mit der Firmware aus `firmware/ptt` nutzen – eine alte Firmware, die nur bei Änderungen sendet,
  würde das Mikrofon nach 500 ms Sprechen abschalten und wird von `comPort=auto` auch nicht gefunden.
- Der ESP8266 startet beim Öffnen des Ports neu (Auto-Reset der NodeMCU). Das ist normal und dauert
  unter einer Sekunde.

### Firmware

PlatformIO-Projekt in `firmware/ptt`, Standard ist die NodeMCU 1.0 (ESP8266):

    pio run -e nodemcuv2 -t upload

Taster zwischen **D5 (GPIO 14)** und GND. Die blaue LED am ESP-Modul leuchtet, solange gesprochen wird.

Beim ESP32-S3 setzt die Software beim Verbinden DTR, weil der Arduino-Core über das native USB
erst sendet, wenn ein Host zuhört. Bei CP210x und CH340 bleibt DTR unangetastet, damit die
Auto-Reset-Schaltung der NodeMCU nicht ausgelöst wird.

Alternativ ESP32-S3-DevKitC-1 N16R8 (`-e esp32-s3-devkitc-1`): Taster an GPIO 4, Buchse „USB“ verwenden.

## Echo / Rückkopplung

Wenn beide Seiten Lautsprecher nutzen und ein Mikrofon dauerhaft offen ist, kommt der Ton als Echo zurück.
Abhilfe, in dieser Reihenfolge:

1. Push-to-Talk auf beiden Seiten.
2. `halfDuplex=true` am Instruktorrechner: Während man selbst spricht, wird die Gegenseite nicht abgespielt.
   Im Simulator wirkungslos, da Lautsprecher und Mikrofon dort in getrennten Instanzen laufen.
   Nur zusammen mit `pushToTalk=true` sinnvoll – sonst ist der Lautsprecher dauerhaft stumm.
3. Headset an Plätzen mit dauerhaft offenem Mikrofon.

## Konsolenbefehle

- Zahl 0–100: Lautstärke der Wiedergabe
- `test`: Testton auf dem eingestellten Lautsprecher
- `mic`: zeigt acht Sekunden lang den Aufnahmepegel des Mikrofons in Prozent
- `devices`: listet alles auf, was Java Sound sieht (auch per `java -jar ... devices` ohne Start)
- `stop`: beenden
- jede andere Eingabe: Mikrofon stumm/aktiv umschalten

## Linux

Die Software läuft unter Linux, produktiv eingesetzt wird sie unter Windows.

Java Sound zeigt unter Linux die rohen ALSA-Geräte an, die Namen sagen entsprechend wenig:

- `Name [default]` – das Standard-PCM der Karte. Läuft PipeWire oder PulseAudio, landet die Ausgabe
  dort und kann anschließend frei zugeordnet werden. Das ist in der Regel die richtige Wahl.
- `Name [plughw:1,3]` – eine feste Hardware-Schnittstelle der Karte (die vielen `HDMI`-Einträge sind
  die Ausgänge der Grafikkarte). Direkt darauf zuzugreifen scheitert meist, weil PipeWire die Karte
  bereits belegt.

Fehlt ein Gerät ganz in der Auswahl, hält es meist ein anderes Programm exklusiv geöffnet.
`aplay -l` zeigt das an: `Sub-Geräte: 0/1` bedeutet, dass kein Sub-Gerät mehr frei ist. Java kann die
Karte dann nicht abfragen und lässt sie weg. Solche Karten stehen jetzt mit Hinweis unter der Auswahl.
Betroffen sind vor allem Ausgänge, die Teil eines aktiven PipeWire-Geräts sind (z. B. eines
kombinierten Ausgangs) – auch hier ist `-2` die Lösung, weil PipeWire das Gerät dann verteilt.

Ob ALSA-Programme überhaupt über PipeWire laufen, zeigt `aplay -L | head`. Steht dort ein Eintrag
`pipewire`, ist die Brücke aktiv (Paket `pipewire-alsa`). Fehlt sie, greift Java direkt auf die
Hardware zu und kollidiert mit PipeWire.

Geräte, die kein Mono können (viele analoge Ein- und Ausgänge), stehen mit dem Hinweis `nur Stereo`
in der Liste. Die Software öffnet sie dann in Stereo und rechnet selbst um: bei der Wiedergabe kommt
das Signal auf beide Kanäle, bei der Aufnahme werden beide Kanäle gemischt. Übertragen wird weiterhin
Mono. Virtuelle Geräte aus PipeWire (z. B. ein kombinierter Ausgang) taucht Java gar nicht auf –
auch dafür ist `-2` der Weg, weil die Zuordnung dann im Lautstärkemixer passiert.

Deshalb bei der Auswahl `-2` (Systemstandard) nehmen. Die Anwendung erscheint dann im Lautstärkemixer
unter **Anwendungen** und kann dort auf das gewünschte Gerät gelegt werden – getrennt für Wiedergabe
und Aufnahme, und ohne die Konfiguration anzufassen.

Weitere Punkte:

- Paket `pipewire-alsa` (bzw. `pulseaudio-alsa`) muss installiert sein, sonst kommt Java nicht an die
  belegte Soundkarte und die Geräte lassen sich nicht öffnen.
- Die Lautstärke (`loudness`) rechnet die Software unter Linux selbst, da ALSA-Leitungen in Java keine
  Lautstärkeregelung anbieten. Systemseitig regelt man weiterhin im Lautstärkemixer.
- Firewall, falls aktiv: `sudo ufw allow 5000/udp`
- Für den ESP muss der Benutzer in der Gruppe `dialout` sein
  (`sudo usermod -aG dialout $USER`, danach neu anmelden). `comPort=auto` findet `/dev/ttyUSB0`
  genauso wie unter Windows `COM3`.

## Feinabstimmung

Knackt es, in `SpeakerClient` `TARGET_FRAMES` und `MAX_FRAMES` etwas erhöhen (je Frame 10 ms mehr Puffer).
