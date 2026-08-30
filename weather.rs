// weather.rs
use clap::{App, Arg};
use reqwest;
use serde::Deserialize;
use serde_json;
use colored::*;
use std::fs;
use std::thread;
use std::time::Duration;

#[derive(Debug, Deserialize)]
struct WttrHourly {
    time: String,
    tempC: String,
    FeelsLikeC: String,
    humidity: String,
    windspeedKmph: String,
    precipMM: String,
    cloudcover: String,
}

#[derive(Debug, Deserialize)]
struct WttrWeather {
    hourly: Vec<WttrHourly>,
}

#[derive(Debug, Deserialize)]
struct WttrResponse {
    weather: Vec<WttrWeather>,
}

struct HourlyData {
    time: String,
    temp: f64,
    feels_like: f64,
    humidity: u32,
    wind: f64,
    precip: f64,
    cloud: u32,
}

struct Weather {
    city: String,
    hours: usize,
    interval: u64,
    format: String,
    output: Option<String>,
}

impl Weather {
    fn new(city: &str, hours: usize, interval: u64, format: &str, output: Option<&str>) -> Self {
        Weather {
            city: city.to_string(),
            hours,
            interval,
            format: format.to_string(),
            output: output.map(String::from),
        }
    }

    async fn fetch(&self) -> Result<WttrResponse, Box<dyn std::error::Error>> {
        let url = format!("https://wttr.in/{}?format=j1&lang=ru", self.city);
        let resp = reqwest::get(&url).await?;
        let data: WttrResponse = resp.json().await?;
        Ok(data)
    }

    fn parse_hourly(&self, data: &WttrResponse) -> Vec<HourlyData> {
        if data.weather.is_empty() || data.weather[0].hourly.is_empty() {
            return Vec::new();
        }
        let hours = &data.weather[0].hourly;
        let mut result = Vec::new();
        for i in 0..self.hours.min(hours.len()) {
            let h = &hours[i];
            let temp: f64 = h.tempC.parse().unwrap_or(0.0);
            let feels: f64 = h.FeelsLikeC.parse().unwrap_or(0.0);
            let hum: u32 = h.humidity.parse().unwrap_or(0);
            let wind: f64 = h.windspeedKmph.parse().unwrap_or(0.0);
            let precip: f64 = h.precipMM.parse().unwrap_or(0.0);
            let cloud: u32 = h.cloudcover.parse().unwrap_or(0);
            result.push(HourlyData {
                time: h.time[..2].to_string() + ":" + &h.time[2..],
                temp,
                feels_like: feels,
                humidity: hum,
                wind,
                precip,
                cloud,
            });
        }
        result
    }

    fn display_text(&self, forecast: &[HourlyData]) {
        println!("{}", format!("Почасовой прогноз для {} (на {} ч):", self.city, self.hours).cyan());
        println!("Время | Темп. | Ощущ. | Влажн. | Ветер | Осадки | Облачн.");
        println!("{}", "-".repeat(60));
        for f in forecast {
            let color = if f.temp < 0.0 { "blue" } else if f.temp < 15.0 { "green" } else if f.temp < 25.0 { "yellow" } else { "red" };
            let temp_str = format!("{:5.1}°C", f.temp).color(color);
            println!("{:5} | {} | {:5.1}°C | {:6}% | {:5.1} км/ч | {:6.1} мм | {:6}%",
                f.time, temp_str, f.feels_like, f.humidity, f.wind, f.precip, f.cloud);
        }
    }

    fn export_json(&self, forecast: &[HourlyData], filename: &str) -> Result<(), Box<dyn std::error::Error>> {
        let json = serde_json::to_string_pretty(forecast)?;
        fs::write(filename, json)?;
        println!("{}", format!("Экспортировано в {} (JSON)", filename).green());
        Ok(())
    }

    fn export_csv(&self, forecast: &[HourlyData], filename: &str) -> Result<(), Box<dyn std::error::Error>> {
        let mut wtr = csv::Writer::from_path(filename)?;
        wtr.write_record(&["time", "temp", "feels_like", "humidity", "wind", "precip", "cloud"])?;
        for f in forecast {
            wtr.write_record(&[
                &f.time,
                &f.temp.to_string(),
                &f.feels_like.to_string(),
                &f.humidity.to_string(),
                &f.wind.to_string(),
                &f.precip.to_string(),
                &f.cloud.to_string(),
            ])?;
        }
        wtr.flush()?;
        println!("{}", format!("Экспортировано в {} (CSV)", filename).green());
        Ok(())
    }

    async fn run(&self) -> Result<(), Box<dyn std::error::Error>> {
        loop {
            let data = self.fetch().await?;
            let forecast = self.parse_hourly(&data);
            if forecast.is_empty() {
                eprintln!("{}", "Нет данных прогноза".red());
                break;
            }
            match self.format.as_str() {
                "text" => self.display_text(&forecast),
                "json" => {
                    if let Some(ref out) = self.output {
                        self.export_json(&forecast, out)?;
                    } else {
                        println!("{}", serde_json::to_string_pretty(&forecast)?);
                    }
                }
                "csv" => {
                    if let Some(ref out) = self.output {
                        self.export_csv(&forecast, out)?;
                    } else {
                        println!("{}", "CSV вывод требует --output".yellow());
                    }
                }
                _ => {}
            }
            if self.interval == 0 {
                break;
            }
            thread::sleep(Duration::from_secs(self.interval));
        }
        Ok(())
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let matches = App::new("Погода")
        .arg(Arg::with_name("city").long("city").takes_value(true).default_value("auto"))
        .arg(Arg::with_name("hours").long("hours").takes_value(true).default_value("12"))
        .arg(Arg::with_name("interval").long("interval").takes_value(true).default_value("0"))
        .arg(Arg::with_name("format").long("format").takes_value(true).default_value("text"))
        .arg(Arg::with_name("output").long("output").takes_value(true))
        .get_matches();

    let city = matches.value_of("city").unwrap();
    let hours: usize = matches.value_of("hours").unwrap().parse()?;
    let interval: u64 = matches.value_of("interval").unwrap().parse()?;
    let format = matches.value_of("format").unwrap();
    let output = matches.value_of("output");

    let weather = Weather::new(city, hours, interval, format, output);
    weather.run().await?;
    Ok(())
}
