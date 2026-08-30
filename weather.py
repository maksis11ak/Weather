
#!/usr/bin/env python3
# weather.py
import argparse
import json
import csv
import sys
import time
import requests
from datetime import datetime
from colorama import init, Fore, Style

init(autoreset=True)

class Weather:
    def __init__(self, city, hours=12, interval=0, output_format="text", output_file=None):
        self.city = city
        self.hours = hours
        self.interval = interval
        self.format = output_format
        self.output_file = output_file
        self.data = None

    def fetch(self):
        url = f"https://wttr.in/{self.city}?format=j1&lang=ru"
        try:
            resp = requests.get(url, timeout=10)
            resp.raise_for_status()
            return resp.json()
        except Exception as e:
            print(Fore.RED + f"Ошибка получения данных: {e}")
            return None

    def parse_hourly(self, data):
        if not data or "weather" not in data:
            return []
        hours = data["weather"][0]["hourly"]
        result = []
        for i, h in enumerate(hours[:self.hours]):
            time_str = h["time"][:2] + ":" + h["time"][2:]
            temp = float(h["tempC"])
            feels_like = float(h["FeelsLikeC"])
            humidity = int(h["humidity"])
            wind = float(h["windspeedKmph"])
            precip = float(h["precipMM"])
            cloud = int(h["cloudcover"])
            result.append({
                "time": time_str,
                "temp": temp,
                "feels_like": feels_like,
                "humidity": humidity,
                "wind": wind,
                "precip": precip,
                "cloud": cloud
            })
        return result

    def display_text(self, forecast):
        print(Fore.CYAN + f"Почасовой прогноз для {self.city} (на {self.hours} ч):")
        print("Время | Темп. | Ощущ. | Влажн. | Ветер | Осадки | Облачн.")
        print("-" * 60)
        for f in forecast:
            temp = f["temp"]
            color = Fore.BLUE if temp < 0 else Fore.GREEN if temp < 15 else Fore.YELLOW if temp < 25 else Fore.RED
            print(f"{f['time']:5} | {color}{temp:5.1f}°C{Style.RESET_ALL} | {f['feels_like']:5.1f}°C | {f['humidity']:6}% | {f['wind']:5.1f} км/ч | {f['precip']:6.1f} мм | {f['cloud']:6}%")

    def export_json(self, forecast, filename):
        with open(filename, 'w') as f:
            json.dump(forecast, f, indent=2)
        print(Fore.GREEN + f"Экспортировано в {filename} (JSON)")

    def export_csv(self, forecast, filename):
        with open(filename, 'w', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=["time", "temp", "feels_like", "humidity", "wind", "precip", "cloud"])
            writer.writeheader()
            writer.writerows(forecast)
        print(Fore.GREEN + f"Экспортировано в {filename} (CSV)")

    def run(self):
        while True:
            data = self.fetch()
            if not data:
                break
            forecast = self.parse_hourly(data)
            if not forecast:
                print(Fore.RED + "Нет данных прогноза")
                break
            if self.format == "text":
                self.display_text(forecast)
            elif self.format == "json":
                if self.output_file:
                    self.export_json(forecast, self.output_file)
                else:
                    print(json.dumps(forecast, indent=2))
            elif self.format == "csv":
                if self.output_file:
                    self.export_csv(forecast, self.output_file)
                else:
                    print("CSV вывод требует --output")
            if self.interval == 0:
                break
            time.sleep(self.interval)

def main():
    parser = argparse.ArgumentParser(description="Почасовой прогноз погоды")
    parser.add_argument("--city", default="auto", help="Город (по умолчанию auto)")
    parser.add_argument("--hours", type=int, default=12, help="Количество часов")
    parser.add_argument("--interval", type=int, default=0, help="Интервал обновления (сек)")
    parser.add_argument("--format", choices=["text", "json", "csv"], default="text")
    parser.add_argument("--output", help="Файл для сохранения")
    args = parser.parse_args()

    weather = Weather(args.city, args.hours, args.interval, args.format, args.output)
    try:
        weather.run()
    except KeyboardInterrupt:
        print(Fore.YELLOW + "\nПрервано пользователем")

if __name__ == "__main__":
    main()
