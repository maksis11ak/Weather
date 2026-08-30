// Weather.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;

namespace Weather
{
    class Program
    {
        static async Task Main(string[] args)
        {
            var opts = ParseArgs(args);
            var weather = new Weather(opts);
            await weather.RunAsync();
        }

        static Options ParseArgs(string[] args)
        {
            var opts = new Options();
            for (int i = 0; i < args.Length; i++)
            {
                switch (args[i])
                {
                    case "--city": opts.City = args[++i]; break;
                    case "--hours": opts.Hours = int.Parse(args[++i]); break;
                    case "--interval": opts.Interval = int.Parse(args[++i]); break;
                    case "--format": opts.Format = args[++i]; break;
                    case "--output": opts.Output = args[++i]; break;
                }
            }
            return opts;
        }

        class Options
        {
            public string City { get; set; } = "auto";
            public int Hours { get; set; } = 12;
            public int Interval { get; set; } = 0;
            public string Format { get; set; } = "text";
            public string Output { get; set; }
        }

        class HourlyData
        {
            public string Time { get; set; }
            public double Temp { get; set; }
            public double FeelsLike { get; set; }
            public int Humidity { get; set; }
            public double Wind { get; set; }
            public double Precip { get; set; }
            public int Cloud { get; set; }
        }

        class Weather
        {
            private Options opts;
            private HttpClient client = new HttpClient();

            public Weather(Options opts)
            {
                this.opts = opts;
            }

            private async Task<string> FetchAsync()
            {
                string url = $"https://wttr.in/{opts.City}?format=j1&lang=ru";
                var response = await client.GetAsync(url);
                response.EnsureSuccessStatusCode();
                return await response.Content.ReadAsStringAsync();
            }

            private List<HourlyData> ParseHourly(string json)
            {
                using var doc = JsonDocument.Parse(json);
                var root = doc.RootElement;
                if (!root.TryGetProperty("weather", out var weatherArr) || weatherArr.GetArrayLength() == 0)
                    return new List<HourlyData>();
                var weather = weatherArr[0];
                if (!weather.TryGetProperty("hourly", out var hourlyArr))
                    return new List<HourlyData>();
                var result = new List<HourlyData>();
                int limit = Math.Min(opts.Hours, hourlyArr.GetArrayLength());
                for (int i = 0; i < limit; i++)
                {
                    var h = hourlyArr[i];
                    var time = h.GetProperty("time").GetString();
                    result.Add(new HourlyData
                    {
                        Time = time.Substring(0, 2) + ":" + time.Substring(2),
                        Temp = h.GetProperty("tempC").GetDouble(),
                        FeelsLike = h.GetProperty("FeelsLikeC").GetDouble(),
                        Humidity = h.GetProperty("humidity").GetInt32(),
                        Wind = h.GetProperty("windspeedKmph").GetDouble(),
                        Precip = h.GetProperty("precipMM").GetDouble(),
                        Cloud = h.GetProperty("cloudcover").GetInt32()
                    });
                }
                return result;
            }

            private void DisplayText(List<HourlyData> forecast)
            {
                Console.ForegroundColor = ConsoleColor.Cyan;
                Console.WriteLine($"Почасовой прогноз для {opts.City} (на {opts.Hours} ч):");
                Console.ResetColor();
                Console.WriteLine("Время | Темп. | Ощущ. | Влажн. | Ветер | Осадки | Облачн.");
                Console.WriteLine(new string('-', 60));
                foreach (var f in forecast)
                {
                    ConsoleColor color = ConsoleColor.Blue;
                    if (f.Temp >= 0 && f.Temp < 15) color = ConsoleColor.Green;
                    else if (f.Temp >= 15 && f.Temp < 25) color = ConsoleColor.Yellow;
                    else if (f.Temp >= 25) color = ConsoleColor.Red;
                    Console.ForegroundColor = color;
                    Console.Write($"{f.Time,5} | ");
                    Console.Write($"{f.Temp,5:F1}°C");
                    Console.ResetColor();
                    Console.Write($" | {f.FeelsLike,5:F1}°C | {f.Humidity,6}% | {f.Wind,5:F1} км/ч | {f.Precip,6:F1} мм | {f.Cloud,6}%");
                    Console.WriteLine();
                }
            }

            private void ExportJson(List<HourlyData> forecast, string filename)
            {
                string json = JsonSerializer.Serialize(forecast, new JsonSerializerOptions { WriteIndented = true });
                File.WriteAllText(filename, json);
                Console.ForegroundColor = ConsoleColor.Green;
                Console.WriteLine($"Экспортировано в {filename} (JSON)");
                Console.ResetColor();
            }

            private void ExportCsv(List<HourlyData> forecast, string filename)
            {
                using var sw = new StreamWriter(filename);
                sw.WriteLine("time,temp,feels_like,humidity,wind,precip,cloud");
                foreach (var f in forecast)
                {
                    sw.WriteLine($"{f.Time},{f.Temp:F1},{f.FeelsLike:F1},{f.Humidity},{f.Wind:F1},{f.Precip:F1},{f.Cloud}");
                }
                Console.ForegroundColor = ConsoleColor.Green;
                Console.WriteLine($"Экспортировано в {filename} (CSV)");
                Console.ResetColor();
            }

            public async Task RunAsync()
            {
                while (true)
                {
                    try
                    {
                        string json = await FetchAsync();
                        var forecast = ParseHourly(json);
                        if (forecast.Count == 0)
                        {
                            Console.ForegroundColor = ConsoleColor.Red;
                            Console.WriteLine("Нет данных прогноза");
                            Console.ResetColor();
                            break;
                        }
                        if (opts.Format == "text")
                        {
                            DisplayText(forecast);
                        }
                        else if (opts.Format == "json")
                        {
                            if (!string.IsNullOrEmpty(opts.Output))
                                ExportJson(forecast, opts.Output);
                            else
                                Console.WriteLine(JsonSerializer.Serialize(forecast, new JsonSerializerOptions { WriteIndented = true }));
                        }
                        else if (opts.Format == "csv")
                        {
                            if (!string.IsNullOrEmpty(opts.Output))
                                ExportCsv(forecast, opts.Output);
                            else
                                Console.WriteLine("CSV вывод требует --output");
                        }
                        if (opts.Interval == 0) break;
                        await Task.Delay(opts.Interval * 1000);
                    }
                    catch (Exception ex)
                    {
                        Console.ForegroundColor = ConsoleColor.Red;
                        Console.WriteLine($"Ошибка: {ex.Message}");
                        Console.ResetColor();
                        break;
                    }
                }
            }
        }
    }
}
