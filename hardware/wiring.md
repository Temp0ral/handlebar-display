# Hardware Wiring Reference

## Components

- ESP32 DevKit (ESP-WROOM-32)
- 1.44" ST7735 TFT LCD Display (128x128)

---

## TFT Display → ESP32 Pinout

| TFT Pin | ESP32 GPIO | Notes                          |
|---------|------------|--------------------------------|
| VCC     | 3.3V       | Do NOT connect to 5V           |
| GND     | GND        |                                |
| CS      | GPIO 5     | Chip select                    |
| RESET   | GPIO 4     | Hardware reset                 |
| DC/RS   | GPIO 2     | Data/command select            |
| SDA     | GPIO 23    | MOSI — Hardware SPI            |
| SCL     | GPIO 18    | SCLK — Hardware SPI            |
| BLK/LED | 3.3V       | Backlight on (PWM via GPIO later) |

---

## Display Specs

- Driver IC: ST7735
- Resolution: 128 x 128 px
- Interface: SPI
- Logic voltage: 3.3V
- Init flag: `ST7735_GREENTAB128`

---

## PlatformIO Build Flags (platformio.ini)

```ini
build_flags =
    -D ST7735_DRIVER
    -D TFT_WIDTH=128
    -D TFT_HEIGHT=128
    -D ST7735_GREENTAB128
    -D TFT_CS=5
    -D TFT_DC=2
    -D TFT_RST=4
    -D SPI_FREQUENCY=27000000
    -D USER_SETUP_LOADED
```

---

## Notes

- ADC2 pins are unusable when WiFi/BLE is active — use ADC1 pins (GPIO 32, 33, 34, 35, 36, 39) for any analog reads
- GPIO 34, 35, 36, 39 are input only — no internal pull-ups
- GPIO 6–11 are tied to internal flash — do not use
- Power the ESP32 via VIN (5V) from the buck converter, not the 3.3V pin directly
