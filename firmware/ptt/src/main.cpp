#include <Arduino.h>

#ifndef BUTTON_PIN
#error "BUTTON_PIN muss in platformio.ini gesetzt sein"
#endif

// Entprellzeit
static const uint32_t DEBOUNCE_MS = 20;

// Zustand zyklisch wiederholen, damit die PC-Software einen Ausfall erkennt (pttTimeoutMs = 500)
static const uint32_t REPEAT_MS = 100;

static bool stablePressed = false;
static bool lastRawPressed = false;
static uint32_t lastRawChangeAt = 0;
static uint32_t lastSentAt = 0;

static void sendState(bool pressed) {
    // "True" = gedrückt, "False" = losgelassen
    Serial.println(pressed ? "True" : "False");
    lastSentAt = millis();
}

static void showState(bool pressed) {
#if defined(RGB_BUILTIN)
    // ESP32-S3: Onboard-RGB-LED rot, solange gesprochen wird
    neopixelWrite(RGB_BUILTIN, pressed ? 40 : 0, 0, 0);
#elif defined(ESP8266)
    // NodeMCU: blaue LED am ESP-Modul (GPIO 2) ist low-aktiv
    digitalWrite(LED_BUILTIN, pressed ? LOW : HIGH);
#endif
}

void setup() {
    pinMode(BUTTON_PIN, INPUT_PULLUP);
#if defined(ESP8266)
    pinMode(LED_BUILTIN, OUTPUT);
#endif
    Serial.begin(115200);
    // Leerzeile trennt die Meldungen vom Boot-Text des ESP8266
    Serial.println();

    stablePressed = digitalRead(BUTTON_PIN) == LOW;
    lastRawPressed = stablePressed;
    showState(stablePressed);
    sendState(stablePressed);
}

void loop() {
    uint32_t now = millis();
    bool rawPressed = digitalRead(BUTTON_PIN) == LOW;

    if (rawPressed != lastRawPressed) {
        lastRawPressed = rawPressed;
        lastRawChangeAt = now;
    }

    // Änderung erst übernehmen, wenn das Signal DEBOUNCE_MS lang stabil ist – dann sofort senden
    if (rawPressed != stablePressed && now - lastRawChangeAt >= DEBOUNCE_MS) {
        stablePressed = rawPressed;
        showState(stablePressed);
        sendState(stablePressed);
    }

    // Lebenszeichen mit aktuellem Zustand
    if (now - lastSentAt >= REPEAT_MS) {
        sendState(stablePressed);
    }

    // Gibt dem ESP8266 Zeit für Hintergrundaufgaben (Watchdog)
    delay(1);
}
