// Weather.kt
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

class Weather {
    @Parameter(names = ["--city"])
    private var city: String = "auto"

    @Parameter(names = ["--hours"])
    private var hours: Int = 12

    @Parameter(names = ["--interval"])
    private var interval: Int = 0

    @Parameter(names = ["--format"])
    private var format: String = "text"

    @Parameter(names = ["--output"])
    private var output: String? = null

    data class HourlyData(val time: String, val temp: Double, val feelsLike: Double, val humidity: Int, val wind: Double, val precip: Double, val cloud: Int)

    private fun fetch(): String? {
        val url = URL("https://wttr.in/$city?format=j1&lang=ru")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        return try {
            BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                reader.readText()
            }
        } catch (e: Exception) {
            System.err.println("Ошибка: ${e.message}")
            null
        }
    }

    private fun parseHourly(json: String): List<HourlyData> {
        val root = JsonParser.parseString(json).asJsonObject
        val weatherArr = root.getAsJsonArray("weather")
        if (weatherArr.size() == 0) return emptyList()
        val weather = weatherArr[0].asJsonObject
        val hourlyArr = weather.getAsJsonArray("hourly")
        val result = mutableListOf<HourlyData>()
        val limit = minOf(hours, hourlyArr.size())
        for (i in 0 until limit) {
            val h = hourlyArr[i].asJsonObject
            val time = h["time"].asString
            result.add(HourlyData(
                time = time.substring(0, 2) + ":" + time.substring(2),
                temp = h["tempC"].asDouble,
                feelsLike = h["FeelsLikeC"].asDouble,
                humidity = h["humidity"].asInt,
                wind = h["windspeedKmph"].asDouble,
                precip = h["precipMM"].asDouble,
                cloud = h["cloudcover"].asInt
            ))
        }
        return result
    }

    private fun displayText(forecast: List<HourlyData>) {
        println("\u001B[36mПочасовой прогноз для $city (на $hours ч):\u001B[0m")
        println("Время | Темп. | Ощущ. | Влажн. | Ветер | Осадки | Облачн.")
        println("-".repeat(60))
        for (f in forecast) {
            val color = when {
                f.temp < 0 -> "\u001B[34m"
                f.temp < 15 -> "\u001B[32m"
                f.temp < 25 -> "\u001B[33m"
                else -> "\u001B[31m"
            }
            println("%5s | %s%5.1f°C\u001B[0m | %5.1f°C | %6d%% | %5.1f км/ч | %6.1f мм | %6d%%".format(
                f.time, color, f.temp, f.feelsLike, f.humidity, f.wind, f.precip, f.cloud
            ))
        }
    }

    private fun exportJson(forecast: List<HourlyData>, filename: String) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        Files.write(Paths.get(filename), gson.toJson(forecast).toByteArray())
        println("\u001B[32mЭкспортировано в $filename (JSON)\u001B[0m")
    }

    private fun exportCsv(forecast: List<HourlyData>, filename: String) {
        PrintWriter(filename).use { pw ->
            pw.println("time,temp,feels_like,humidity,wind,precip,cloud")
            for (f in forecast) {
                pw.println("${f.time},${f.temp},${f.feelsLike},${f.humidity},${f.wind},${f.precip},${f.cloud}")
            }
        }
        println("\u001B[32mЭкспортировано в $filename (CSV)\u001B[0m")
    }

    fun run() {
        while (true) {
            val json = fetch() ?: break
            val forecast = parseHourly(json)
            if (forecast.isEmpty()) {
                System.err.println("\u001B[31mНет данных прогноза\u001B[0m")
                break
            }
            when (format) {
                "text" -> displayText(forecast)
                "json" -> {
                    if (output != null) {
                        exportJson(forecast, output!!)
                    } else {
                        val gson = GsonBuilder().setPrettyPrinting().create()
                        println(gson.toJson(forecast))
                    }
                }
                "csv" -> {
                    if (output != null) {
                        exportCsv(forecast, output!!)
                    } else {
                        println("\u001B[33mCSV вывод требует --output\u001B[0m")
                    }
                }
            }
            if (interval == 0) break
            Thread.sleep(interval * 1000L)
        }
    }
}

fun main(args: Array<String>) {
    val w = Weather()
    JCommander.newBuilder().addObject(w).build().parse(*args)
    w.run()
}
