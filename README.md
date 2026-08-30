# Погода (почасовой прогноз)

Многоязычная утилита для получения почасового прогноза погоды с использованием открытого API wttr.in.  
Позволяет просматривать прогноз на указанное количество часов, отслеживать изменения в реальном времени с автоматическим обновлением, экспортировать данные в JSON и CSV.

## Особенности
- Получение почасового прогноза для любого города (по умолчанию — текущее местоположение).
- Отображение температуры, ощущаемой температуры, осадков, влажности, скорости ветра и облачности.
- Цветная индикация температуры (синий — холодно, красный — жарко).
- Автоматическое обновление с заданным интервалом (режим мониторинга).
- Экспорт прогноза в JSON и CSV.
- Поддержка нескольких городов (разделитель запятая).
- Настраиваемое количество часов прогноза (по умолчанию 12).
- Кроссплатформенность (Windows, Linux, macOS).
- Полностью бесплатно, без необходимости регистрации и API-ключей.

## Установка и запуск
Для каждого языка требуются соответствующие инструменты и зависимости (указаны ниже).

### Запуск на разных языках

1. **Python**  
   Установка: `pip install requests colorama`  
   Запуск: `python weather.py --city London --hours 24 --interval 60`

2. **JavaScript (Node.js)**  
   Установка: `npm install axios commander chalk`  
   Запуск: `node weather.js --city London --hours 24 --interval 60`

3. **Go**  
   Установка: модулей не требуется (стандартная библиотека).  
   Запуск: `go run weather.go --city London --hours 24 --interval 60`

4. **Rust**  
   Добавьте `reqwest`, `serde`, `serde_json`, `clap`, `colored`, `chrono` в `Cargo.toml`.  
   Запуск: `cargo run -- --city London --hours 24 --interval 60`

5. **Java**  
   Сборка: `javac -cp gson.jar Weather.java` (требуется Gson).  
   Запуск: `java -cp .;gson.jar Weather --city London --hours 24`

6. **C# (.NET Core)**  
   Установка: `dotnet add package Newtonsoft.Json`  
   Запуск: `dotnet run -- --city London --hours 24`

7. **C++ (Linux)**  
   Требуется libcurl, nlohmann/json.  
   Сборка: `g++ -std=c++11 -o weather weather.cpp -lcurl -ljsoncpp`  
   Запуск: `./weather --city London --hours 24`

8. **Kotlin (JVM)**  
   Сборка: `kotlinc -cp gson.jar Weather.kt`  
   Запуск: `kotlin -cp .;gson.jar WeatherKt --city London --hours 24`

## Использование

Общие аргументы командной строки (везде, где поддерживается):

- `--city <название>` – город для прогноза (по умолчанию определяется по IP).
- `--hours <число>` – количество часов прогноза (по умолчанию 12).
- `--interval <сек>` – интервал обновления в секундах (0 = однократный вывод).
- `--format <json|csv|text>` – формат вывода (по умолчанию `text`).
- `--output <файл>` – сохранить результат в файл.
- `--help` – справка.

Пример (Python):
```bash
python weather.py --city Moscow --hours 24 --interval 60 --format json --output forecast.json
Структура репозитория
text
/
├── README.md
├── weather.py
├── weather.js
├── weather.go
├── weather.rs
├── Weather.java
├── Weather.cs
├── weather.cpp
└── Weather.kt
Лицензия
MIT

text
