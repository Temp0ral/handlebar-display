#include <Arduino.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7735.h>
#include <SPI.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "arrows.h"

#define TFT_CS  5
#define TFT_DC  15
#define TFT_RST 4

Adafruit_ST7735 tft = Adafruit_ST7735(TFT_CS, TFT_DC, TFT_RST);

#define SERVICE_UUID        "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"

#define W 128
#define H 128

bool deviceConnected = false;

void updateStatus(bool connected);
void drawHUD(String maneuver, String street, String distance);

void updateStatus(bool connected) {
  uint16_t color = connected ? ST77XX_GREEN : ST77XX_YELLOW;
  tft.fillCircle(8, 8, 5, color);
}

void drawArrow(String direction) {
  int arrowX = (W - ARROW_SIZE) / 2;
  int arrowY = -2;
  tft.fillRect(arrowX, arrowY, ARROW_SIZE, ARROW_SIZE, ST77XX_BLACK);
  if (direction == "LEFT")
    tft.drawBitmap(arrowX, arrowY, ARROW_LEFT, ARROW_SIZE, ARROW_SIZE, ST77XX_WHITE);
  else if (direction == "RIGHT")
    tft.drawBitmap(arrowX, arrowY, ARROW_RIGHT, ARROW_SIZE, ARROW_SIZE, ST77XX_WHITE);
  else if (direction == "UTURN")
    tft.drawBitmap(arrowX, arrowY, ARROW_UTURN, ARROW_SIZE, ARROW_SIZE, ST77XX_WHITE);
  else if (direction == "ARRIVE")
    tft.drawBitmap(arrowX, arrowY, ARROW_ARRIVE, ARROW_SIZE, ARROW_SIZE, ST77XX_GREEN);
  else
    tft.drawBitmap(arrowX, arrowY, ARROW_STRAIGHT, ARROW_SIZE, ARROW_SIZE, ST77XX_WHITE);
}

void drawHUD(String maneuver, String street, String distance) {
  tft.fillScreen(ST77XX_BLACK);

  // Arrow — top 70%
  drawArrow(maneuver);

  // Street name — centered below arrow
  tft.setTextColor(ST77XX_YELLOW, ST77XX_BLACK);
  tft.setTextSize(1);
  int streetW = street.length() * 6;
  if (streetW > W) streetW = W;
  tft.setCursor((W - streetW) / 2, 96);
  if (street.length() > 26) street = street.substring(0, 26);
  tft.print(street);

  // Distance — centered below street
  tft.setTextColor(ST77XX_GREEN, ST77XX_BLACK);
  tft.setTextSize(1);
  int distW = distance.length() * 6;
  tft.setCursor((W - distW) / 2, 110);
  tft.print(distance);

  // BLE dot
  updateStatus(deviceConnected);
}

void drawInitialScreen() {
  tft.fillScreen(ST77XX_BLACK);
  tft.setTextColor(ST77XX_WHITE, ST77XX_BLACK);
  tft.setTextSize(1);
  tft.setCursor((W - 72) / 2, 56);
  tft.print("Moto HUD v1.0");
  tft.setTextColor(0x4208, ST77XX_BLACK);
  tft.setCursor((W - 108) / 2, 70);
  tft.print("Waiting for nav...");
  updateStatus(false);
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    deviceConnected = true;
    updateStatus(true);
  }
  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
    updateStatus(false);
    pServer->startAdvertising();
  }
};

class CharacteristicCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pCharacteristic) {
    String value = pCharacteristic->getValue().c_str();
    if (value.length() > 0) {
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
        else if (raw.indexOf("UTURN") >= 0) direction = "UTURN";
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
    CHARACTERISTIC_UUID, BLECharacteristic::PROPERTY_WRITE);
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