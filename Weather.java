// Weather.java
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Weather {
    @Parameter(names = "--city")
    private String city = "auto";
    @Parameter(names = "--hours")
    private int hours = 12;
    @Parameter(names = "--interval")
    private int interval = 0;
    @Parameter(names = "--format")
    private String format = "text";
    @Parameter(names = "--output")
    private String output;

    static class HourlyData {
        String time;
        double temp;
        double feelsLike;
        int humidity;
        double wind;
        double precip;
        int cloud;
    }

    public static void main(String[] args) throws Exception {
        Weather w = new Weather();
        JCommander.newBuilder().addObject(w).build().parse(args);
        w.run();
    }

    private String fetch() throws Exception {
        URL url = new URL("https://wttr.in/" + city + "?format=j1&lang=ru");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private List<HourlyData> parseHourly(String jsonStr) {
        JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
        JsonArray weatherArr = root.getAsJsonArray("weather");
        if (weatherArr.size() == 0) return Collections.emptyList();
        JsonObject weather = weatherArr.get(0).getAsJsonObject();
        JsonArray hourly = weather.getAsJsonArray("hourly");
        List<HourlyData> result = new ArrayList<>();
        int limit = Math.min(hours, hourly.size());
        for (int i = 0; i < limit; i++) {
            JsonObject h = hourly.get(i).getAsJsonObject();
            HourlyData d = new HourlyData();
            d.time = h.get("time").getAsString();
            d.time = d.time.substring(0, 2) + ":" + d.time.substring(2);
            d.temp = h.get("tempC").getAsDouble();
            d.feelsLike = h.get("FeelsLikeC").getAsDouble();
            d.humidity = h.get("humidity").getAsInt();
            d.wind = h.get("windspeedKmph").getAsDouble();
            d.precip = h.get("precipMM").getAsDouble();
            d.cloud = h.get("cloudcover").getAsInt();
            result.add(d);
        }
        return result;
    }

    private void displayText(List<HourlyData> forecast) {
        System.out.println("\u001B[36mПочасовой прогноз для " + city + " (на " + hours + " ч):\u001B[0m");
        System.out.println("Время | Темп. | Ощущ. | Влажн. | Ветер | Осадки | Облачн.");
        System.out.println("-".repeat(60));
        for (HourlyData f : forecast) {
            String color = "\u001B[34m";
            if (f.temp >= 0 && f.temp < 15) color = "\u001B[32m";
            else if (f.temp >= 15 && f.temp < 25) color = "\u001B[33m";
            else if (f.temp >= 25) color = "\u001B[31m";
            System.out.printf("%5s | %s%5.1f°C\u001B[0m | %5.1f°C | %6d%% | %5.1f км/ч | %6.1f мм | %6d%%%n",
                f.time, color, f.temp, f.feelsLike, f.humidity, f.wind, f.precip, f.cloud);
        }
    }

    private void exportJson(List<HourlyData> forecast, String filename) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.write(Paths.get(filename), gson.toJson(forecast).getBytes());
        System.out.println("\u001B[32mЭкспортировано в " + filename + " (JSON)\u001B[0m");
    }

    private void exportCsv(List<HourlyData> forecast, String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("time,temp,feels_like,humidity,wind,precip,cloud");
            for (HourlyData f : forecast) {
                pw.printf("%s,%.1f,%.1f,%d,%.1f,%.1f,%d%n",
                    f.time, f.temp, f.feelsLike, f.humidity, f.wind, f.precip, f.cloud);
            }
        }
        System.out.println("\u001B[32mЭкспортировано в " + filename + " (CSV)\u001B[0m");
    }

    public void run() throws Exception {
        while (true) {
            String jsonStr = fetch();
            List<HourlyData> forecast = parseHourly(jsonStr);
            if (forecast.isEmpty()) {
                System.err.println("\u001B[31mНет данных прогноза\u001B[0m");
                break;
            }
            if (format.equals("text")) {
                displayText(forecast);
            } else if (format.equals("json")) {
                if (output != null) {
                    exportJson(forecast, output);
                } else {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    System.out.println(gson.toJson(forecast));
                }
            } else if (format.equals("csv")) {
                if (output != null) {
                    exportCsv(forecast, output);
                } else {
                    System.out.println("\u001B[33mCSV вывод требует --output\u001B[0m");
                }
            }
            if (interval == 0) break;
            Thread.sleep(interval * 1000L);
        }
    }
}
