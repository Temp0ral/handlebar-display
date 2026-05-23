#include <Arduino.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7735.h>
#include <SPI.h>

#define TFT_CS  5
#define TFT_DC  15
#define TFT_RST 4

Adafruit_ST7735 tft = Adafruit_ST7735(TFT_CS, TFT_DC, TFT_RST);

void setup() {
  Serial.begin(115200);
  delay(1000);

  tft.initR(INITR_144GREENTAB);
  tft.fillScreen(ST77XX_BLACK);
  
  // Direction arrow area
  tft.setTextColor(ST77XX_WHITE);
  tft.setTextSize(1.75);
  tft.setCursor(0, 0);
  tft.println("< Turn LEFT");
  
  // Street name
  tft.setTextColor(ST77XX_YELLOW);
  tft.setTextSize(1);
  tft.setCursor(0, 25);
  tft.println("Main St");
  
  // Divider line
  tft.drawFastHLine(0, 40, 128, ST77XX_WHITE);
  
  // Distance
  tft.setTextColor(ST77XX_GREEN);
  tft.setTextSize(2);
  tft.setCursor(0, 50);
  tft.println("0.3 mi");
  
  // Divider line
  tft.drawFastHLine(0, 90, 128, ST77XX_WHITE);
  
  // Status bar
  tft.setTextColor(ST77XX_BLUE);
  tft.setTextSize(1);
  tft.setCursor(0, 100);
  tft.println("BLE Connecting...");

  Serial.println("Display initialized");
}

void loop() {}