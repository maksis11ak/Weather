// weather.go
package main

import (
	"encoding/csv"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

type HourlyData struct {
	Time       string  `json:"time"`
	Temp       float64 `json:"temp"`
	FeelsLike  float64 `json:"feels_like"`
	Humidity   int     `json:"humidity"`
	Wind       float64 `json:"wind"`
	Precip     float64 `json:"precip"`
	Cloud      int     `json:"cloud"`
}

type WttrResponse struct {
	Weather []struct {
		Hourly []struct {
			Time         string `json:"time"`
			TempC        string `json:"tempC"`
			FeelsLikeC   string `json:"FeelsLikeC"`
			Humidity     string `json:"humidity"`
			WindspeedKmph string `json:"windspeedKmph"`
			PrecipMM     string `json:"precipMM"`
			Cloudcover   string `json:"cloudcover"`
		} `json:"hourly"`
	} `json:"weather"`
}

type Weather struct {
	city   string
	hours  int
	interval int
	format string
	output string
}

func NewWeather(city string, hours, interval int, format, output string) *Weather {
	return &Weather{city: city, hours: hours, interval: interval, format: format, output: output}
}

func (w *Weather) fetch() (*WttrResponse, error) {
	url := fmt.Sprintf("https://wttr.in/%s?format=j1&lang=ru", w.city)
	resp, err := http.Get(url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	var data WttrResponse
	if err := json.Unmarshal(body, &data); err != nil {
		return nil, err
	}
	return &data, nil
}

func (w *Weather) parseHourly(data *WttrResponse) []HourlyData {
	if data == nil || len(data.Weather) == 0 || len(data.Weather[0].Hourly) == 0 {
		return nil
	}
	hours := data.Weather[0].Hourly
	result := make([]HourlyData, 0, w.hours)
	for i, h := range hours {
		if i >= w.hours {
			break
		}
		temp, _ := strconv.ParseFloat(h.TempC, 64)
		feels, _ := strconv.ParseFloat(h.FeelsLikeC, 64)
		hum, _ := strconv.Atoi(h.Humidity)
		wind, _ := strconv.ParseFloat(h.WindspeedKmph, 64)
		precip, _ := strconv.ParseFloat(h.PrecipMM, 64)
		cloud, _ := strconv.Atoi(h.Cloudcover)
		result = append(result, HourlyData{
			Time:       h.Time[:2] + ":" + h.Time[2:],
			Temp:       temp,
			FeelsLike:  feels,
			Humidity:   hum,
			Wind:       wind,
			Precip:     precip,
			Cloud:      cloud,
		})
	}
	return result
}

func (w *Weather) displayText(forecast []HourlyData) {
	fmt.Printf("\033[36mПочасовой прогноз для %s (на %d ч):\033[0m\n", w.city, w.hours)
	fmt.Println("Время | Темп. | Ощущ. | Влажн. | Ветер | Осадки | Облачн.")
	fmt.Println(strings.Repeat("-", 60))
	for _, f := range forecast {
		var color string
		if f.Temp < 0 {
			color = "\033[34m"
		} else if f.Temp < 15 {
			color = "\033[32m"
		} else if f.Temp < 25 {
			color = "\033[33m"
		} else {
			color = "\033[31m"
		}
		fmt.Printf("%5s | %s%5.1f°C\033[0m | %5.1f°C | %6d%% | %5.1f км/ч | %6.1f мм | %6d%%\n",
			f.Time, color, f.Temp, f.FeelsLike, f.Humidity, f.Wind, f.Precip, f.Cloud)
	}
}

func (w *Weather) exportJSON(forecast []HourlyData, filename string) error {
	data, err := json.MarshalIndent(forecast, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(filename, data, 0644)
}

func (w *Weather) exportCSV(forecast []HourlyData, filename string) error {
	file, err := os.Create(filename)
	if err != nil {
		return err
	}
	defer file.Close()
	writer := csv.NewWriter(file)
	defer writer.Flush()
	writer.Write([]string{"time", "temp", "feels_like", "humidity", "wind", "precip", "cloud"})
	for _, f := range forecast {
		record := []string{
			f.Time,
			strconv.FormatFloat(f.Temp, 'f', 1, 64),
			strconv.FormatFloat(f.FeelsLike, 'f', 1, 64),
			strconv.Itoa(f.Humidity),
			strconv.FormatFloat(f.Wind, 'f', 1, 64),
			strconv.FormatFloat(f.Precip, 'f', 1, 64),
			strconv.Itoa(f.Cloud),
		}
		writer.Write(record)
	}
	return nil
}

func (w *Weather) run() error {
	for {
		data, err := w.fetch()
		if err != nil {
			return fmt.Errorf("ошибка получения данных: %v", err)
		}
		forecast := w.parseHourly(data)
		if len(forecast) == 0 {
			return fmt.Errorf("нет данных прогноза")
		}
		switch w.format {
		case "text":
			w.displayText(forecast)
		case "json":
			if w.output != "" {
				if err := w.exportJSON(forecast, w.output); err != nil {
					return err
				}
				fmt.Printf("\033[32mЭкспортировано в %s (JSON)\033[0m\n", w.output)
			} else {
				jsonData, _ := json.MarshalIndent(forecast, "", "  ")
				fmt.Println(string(jsonData))
			}
		case "csv":
			if w.output != "" {
				if err := w.exportCSV(forecast, w.output); err != nil {
					return err
				}
				fmt.Printf("\033[32mЭкспортировано в %s (CSV)\033[0m\n", w.output)
			} else {
				fmt.Println("CSV вывод требует --output")
			}
		}
		if w.interval == 0 {
			break
		}
		time.Sleep(time.Duration(w.interval) * time.Second)
	}
	return nil
}

func main() {
	var city, format, output string
	var hours, interval int
	flag.StringVar(&city, "city", "auto", "Город")
	flag.IntVar(&hours, "hours", 12, "Количество часов")
	flag.IntVar(&interval, "interval", 0, "Интервал обновления (сек)")
	flag.StringVar(&format, "format", "text", "Формат вывода (text, json, csv)")
	flag.StringVar(&output, "output", "", "Файл для сохранения")
	flag.Parse()

	weather := NewWeather(city, hours, interval, format, output)
	if err := weather.run(); err != nil {
		fmt.Fprintf(os.Stderr, "\033[31mОшибка: %v\033[0m\n", err)
		os.Exit(1)
	}
}
