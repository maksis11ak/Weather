// weather.cpp
#include <iostream>
#include <string>
#include <vector>
#include <sstream>
#include <iomanip>
#include <curl/curl.h>
#include <json/json.h>
#include <unistd.h>

using namespace std;

size_t WriteCallback(void* contents, size_t size, size_t nmemb, void* userp) {
    ((string*)userp)->append((char*)contents, size * nmemb);
    return size * nmemb;
}

struct HourlyData {
    string time;
    double temp;
    double feelsLike;
    int humidity;
    double wind;
    double precip;
    int cloud;
};

class Weather {
public:
    string city;
    int hours;
    int interval;
    string format;
    string output;

    Weather(string c, int h, int i, string f, string o) : city(c), hours(h), interval(i), format(f), output(o) {}

    string fetch() {
        CURL* curl = curl_easy_init();
        if (!curl) return "";
        string url = "https://wttr.in/" + city + "?format=j1&lang=ru";
        string response;
        curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteCallback);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 10L);
        CURLcode res = curl_easy_perform(curl);
        curl_easy_cleanup(curl);
        if (res != CURLE_OK) {
            cerr << "Ошибка запроса" << endl;
            return "";
        }
        return response;
    }

    vector<HourlyData> parseHourly(const string& jsonStr) {
        vector<HourlyData> result;
        Json::Value root;
        Json::Reader reader;
        if (!reader.parse(jsonStr, root)) return result;
        if (!root.isMember("weather") || root["weather"].size() == 0) return result;
        const Json::Value& weather = root["weather"][0];
        if (!weather.isMember("hourly")) return result;
        const Json::Value& hourly = weather["hourly"];
        int limit = min(hours, (int)hourly.size());
        for (int i = 0; i < limit; i++) {
            const Json::Value& h = hourly[i];
            HourlyData d;
            string time = h["time"].asString();
            d.time = time.substr(0,2) + ":" + time.substr(2);
            d.temp = h["tempC"].asDouble();
            d.feelsLike = h["FeelsLikeC"].asDouble();
            d.humidity = h["humidity"].asInt();
            d.wind = h["windspeedKmph"].asDouble();
            d.precip = h["precipMM"].asDouble();
            d.cloud = h["cloudcover"].asInt();
            result.push_back(d);
        }
        return result;
    }

    void displayText(const vector<HourlyData>& forecast) {
        cout << "\033[36mПочасовой прогноз для " << city << " (на " << hours << " ч):\033[0m" << endl;
        cout << "Время | Темп. | Ощущ. | Влажн. | Ветер | Осадки | Облачн." << endl;
        cout << string(60, '-') << endl;
        for (const auto& f : forecast) {
            string color = "\033[34m";
            if (f.temp >= 0 && f.temp < 15) color = "\033[32m";
            else if (f.temp >= 15 && f.temp < 25) color = "\033[33m";
            else if (f.temp >= 25) color = "\033[31m";
            cout << setw(5) << f.time << " | " << color << setw(5) << fixed << setprecision(1) << f.temp << "°C\033[0m"
                 << " | " << setw(5) << setprecision(1) << f.feelsLike << "°C"
                 << " | " << setw(6) << f.humidity << "%"
                 << " | " << setw(5) << setprecision(1) << f.wind << " км/ч"
                 << " | " << setw(6) << setprecision(1) << f.precip << " мм"
                 << " | " << setw(6) << f.cloud << "%" << endl;
        }
    }

    void exportJSON(const vector<HourlyData>& forecast, const string& filename) {
        Json::Value root(Json::arrayValue);
        for (const auto& f : forecast) {
            Json::Value item;
            item["time"] = f.time;
            item["temp"] = f.temp;
            item["feels_like"] = f.feelsLike;
            item["humidity"] = f.humidity;
            item["wind"] = f.wind;
            item["precip"] = f.precip;
            item["cloud"] = f.cloud;
            root.append(item);
        }
        ofstream ofs(filename);
        ofs << root.toStyledString();
        cout << "\033[32mЭкспортировано в " << filename << " (JSON)\033[0m" << endl;
    }

    void exportCSV(const vector<HourlyData>& forecast, const string& filename) {
        ofstream ofs(filename);
        ofs << "time,temp,feels_like,humidity,wind,precip,cloud\n";
        for (const auto& f : forecast) {
            ofs << f.time << "," << f.temp << "," << f.feelsLike << ","
                << f.humidity << "," << f.wind << "," << f.precip << "," << f.cloud << "\n";
        }
        cout << "\033[32mЭкспортировано в " << filename << " (CSV)\033[0m" << endl;
    }

    void run() {
        curl_global_init(CURL_GLOBAL_DEFAULT);
        while (true) {
            string jsonStr = fetch();
            if (jsonStr.empty()) break;
            auto forecast = parseHourly(jsonStr);
            if (forecast.empty()) {
                cerr << "\033[31mНет данных прогноза\033[0m" << endl;
                break;
            }
            if (format == "text") {
                displayText(forecast);
            } else if (format == "json") {
                if (!output.empty()) {
                    exportJSON(forecast, output);
                } else {
                    Json::Value root(Json::arrayValue);
                    for (const auto& f : forecast) {
                        Json::Value item;
                        item["time"] = f.time;
                        item["temp"] = f.temp;
                        item["feels_like"] = f.feelsLike;
                        item["humidity"] = f.humidity;
                        item["wind"] = f.wind;
                        item["precip"] = f.precip;
                        item["cloud"] = f.cloud;
                        root.append(item);
                    }
                    cout << root.toStyledString();
                }
            } else if (format == "csv") {
                if (!output.empty()) {
                    exportCSV(forecast, output);
                } else {
                    cout << "\033[33mCSV вывод требует --output\033[0m" << endl;
                }
            }
            if (interval == 0) break;
            sleep(interval);
        }
        curl_global_cleanup();
    }
};

int main(int argc, char* argv[]) {
    string city = "auto", format = "text", output;
    int hours = 12, interval = 0;

    for (int i = 1; i < argc; ++i) {
        string arg = argv[i];
        if (arg == "--city" && i+1 < argc) city = argv[++i];
        else if (arg == "--hours" && i+1 < argc) hours = stoi(argv[++i]);
        else if (arg == "--interval" && i+1 < argc) interval = stoi(argv[++i]);
        else if (arg == "--format" && i+1 < argc) format = argv[++i];
        else if (arg == "--output" && i+1 < argc) output = argv[++i];
    }

    Weather w(city, hours, interval, format, output);
    w.run();
    return 0;
}
