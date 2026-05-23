#include <Arduino.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7735.h>
#include <SPI.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define TFT_CS  5
#define TFT_DC  15
#define TFT_RST 4

Adafruit_ST7735 tft = Adafruit_ST7735(TFT_CS, TFT_DC, TFT_RST);

#define SERVICE_UUID        "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"

#define W 160
#define H 128

// Zone Y positions
#define ZONE_NAV_Y    0
#define ZONE_NAV_H    80
#define ZONE_DIV1_Y   80
#define ZONE_DIST_Y   83
#define ZONE_DIST_H   28
#define ZONE_DIV2_Y   111
#define ZONE_STATUS_Y 114

bool deviceConnected = false;

void drawDivider(int y) {
  tft.drawFastHLine(0, y, W, 0x4208); // dark gray
}

void drawArrow(String direction) {
  tft.setTextSize(5);
  tft.setTextColor(ST77XX_WHITE, ST77XX_BLACK);
  tft.setCursor(4, 4);
  if (direction == "LEFT") tft.print("<");
  else if (direction == "RIGHT") tft.print(">");
  else if (direction == "STRAIGHT") tft.print("^");
  else if (direction == "ARRIVE") tft.setTextColor(ST77XX_GREEN, ST77XX_BLACK), tft.print("*");
  else tft.print("-");
}

void drawHUD(String maneuver, String street, String distance) {
  tft.fillRect(0, ZONE_NAV_Y, W, ZONE_NAV_H, ST77XX_BLACK);

  // Big arrow
  drawArrow(maneuver);

  // Direction text below arrow
  tft.setTextColor(ST77XX_WHITE, ST77XX_BLACK);
  tft.setTextSize(1);
  tft.setCursor(4, 46);
  String maneuverText = "Turn " + maneuver;
  if (maneuver == "STRAIGHT") maneuverText = "Continue";
  if (maneuver == "ARRIVE") maneuverText = "Arriving";
  tft.print(maneuverText);

  // Street name below direction
  tft.setTextColor(ST77XX_YELLOW, ST77XX_BLACK);
  tft.setTextSize(1);
  tft.setCursor(4, 58);
  if (street.length() > 22) {
    tft.print(street.substring(0, 22));
    tft.setCursor(4, 68);
    tft.print(street.substring(22));
  } else {
    tft.print(street);
  }

  // Dividers
  drawDivider(ZONE_DIV1_Y);
  drawDivider(ZONE_DIV2_Y);

  // Distance centered
  tft.fillRect(0, ZONE_DIST_Y, W, ZONE_DIST_H, ST77XX_BLACK);
  tft.setTextColor(ST77XX_GREEN, ST77XX_BLACK);
  tft.setTextSize(2);
  int distWidth = distance.length() * 12;
  int distX = (W - distWidth) / 2;
  tft.setCursor(distX, ZONE_DIST_Y + 6);
  tft.print(distance);
}

void updateStatus(String msg, uint16_t color) {
  tft.fillRect(0, ZONE_STATUS_Y, W, H - ZONE_STATUS_Y, ST77XX_BLACK);
  tft.setTextColor(color, ST77XX_BLACK);
  tft.setTextSize(1);
  tft.setCursor(4, ZONE_STATUS_Y);
  tft.print(msg);
}

void drawInitialScreen() {
  tft.fillScreen(ST77XX_BLACK);
  drawDivider(ZONE_DIV1_Y);
  drawDivider(ZONE_DIV2_Y);

  tft.setTextColor(ST77XX_WHITE, ST77XX_BLACK);
  tft.setTextSize(1);
  tft.setCursor(4, 30);
  tft.print("Moto HUD v1.0");

  tft.setTextColor(0x4208, ST77XX_BLACK);
  tft.setCursor(4, 50);
  tft.print("Waiting for nav...");

  updateStatus("BLE Advertising...", ST77XX_YELLOW);
}

// Forward declarations
void updateStatus(String msg, uint16_t color);
void drawHUD(String maneuver, String street, String distance);

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    deviceConnected = true;
    updateStatus("BLE Connected", ST77XX_GREEN);
  }
  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
    updateStatus("BLE Disconnected", ST77XX_RED);
    pServer->startAdvertising();
  }
};

class CharacteristicCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pCharacteristic) {
    String value = pCharacteristic->getValue().c_str();
    if (value.length() > 0) {
      // Parse pipe-delimited string: "DIRECTION|Street Name|Distance"
      int pipe1 = value.indexOf('|');
      int pipe2 = value.indexOf('|', pipe1 + 1);

      String direction = "STRAIGHT";
      String street = "";
      String distance = "";

      if (pipe1 > 0) {
        String raw = value.substring(0, pipe1);
        raw.toUpperCase();
        if (raw.indexOf("LEFT") >= 0) direction = "LEFT";
        else if (raw.indexOf("RIGHT") >= 0) direction = "RIGHT";
        else if (raw.indexOf("ARRIVE") >= 0) direction = "ARRIVE";
        else direction = "STRAIGHT";

        street = value.substring(pipe1 + 1, pipe2);
        distance = value.substring(pipe2 + 1);
      }

      drawHUD(direction, street, distance);
    }
  }
};

void setup() {
  Serial.begin(115200);
  delay(1000);

  tft.initR(INITR_144GREENTAB);
  tft.setRotation(2);
  drawInitialScreen();

  BLEDevice::init("MotoHUD");
  BLEServer* pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);
  BLECharacteristic* pCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_WRITE
  );
  pCharacteristic->setCallbacks(new CharacteristicCallbacks());
  pCharacteristic->addDescriptor(new BLE2902());

  pService->start();
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->start();

  Serial.println("MotoHUD ready");
}

void loop() {
  delay(10);
}