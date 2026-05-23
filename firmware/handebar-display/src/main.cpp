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

bool deviceConnected = false;
String receivedData = "";

// Forward declarations
void updateStatus(String msg, uint16_t color);
void updateDisplay(String data);

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
      receivedData = value;
      updateDisplay(receivedData);
    }
  }
};

void updateStatus(String msg, uint16_t color) {
  tft.fillRect(0, 90, 128, 38, ST77XX_BLACK);
  tft.drawFastHLine(0, 90, 128, ST77XX_WHITE);
  tft.setTextColor(color);
  tft.setTextSize(1);
  tft.setCursor(0, 100);
  tft.println(msg);
}

void updateDisplay(String data) {
  tft.fillRect(0, 0, 128, 89, ST77XX_BLACK);
  tft.setTextColor(ST77XX_WHITE);
  tft.setTextSize(2);
  tft.setCursor(0, 0);
  tft.println(data);
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  tft.initR(INITR_144GREENTAB);
  tft.fillScreen(ST77XX_BLACK);
  
  tft.setTextColor(ST77XX_WHITE);
  tft.setTextSize(2);
  tft.setCursor(0, 0);
  tft.println("Moto HUD");
  
  tft.drawFastHLine(0, 90, 128, ST77XX_WHITE);
  
  tft.setTextColor(ST77XX_YELLOW);
  tft.setTextSize(1);
  tft.setCursor(0, 100);
  tft.println("BLE Starting...");

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

  tft.fillRect(0, 95, 128, 33, ST77XX_BLACK);
  tft.setTextColor(ST77XX_YELLOW);
  tft.setTextSize(1);
  tft.setCursor(0, 100);
  tft.println("BLE Advertising...");
  
  Serial.println("BLE Started, waiting for connection...");
}

void loop() {
  delay(10);
}