#!/usr/bin/env node
// weather.js
const { program } = require('commander');
const axios = require('axios');
const chalk = require('chalk');
const fs = require('fs');

class Weather {
    constructor(city, hours, interval, format, output) {
        this.city = city || 'auto';
        this.hours = hours || 12;
        this.interval = interval || 0;
        this.format = format || 'text';
        this.output = output;
    }

    async fetch() {
        const url = `https://wttr.in/${this.city}?format=j1&lang=ru`;
        try {
            const response = await axios.get(url, { timeout: 10000 });
            return response.data;
        } catch (err) {
            console.error(chalk.red(`Ошибка получения данных: ${err.message}`));
            return null;
        }
    }

    parseHourly(data) {
        if (!data || !data.weather || !data.weather[0].hourly) return [];
        const hours = data.weather[0].hourly;
        const result = [];
        for (let i = 0; i < Math.min(this.hours, hours.length); i++) {
            const h = hours[i];
            result.push({
                time: h.time.slice(0,2) + ':' + h.time.slice(2),
                temp: parseFloat(h.tempC),
                feels_like: parseFloat(h.FeelsLikeC),
                humidity: parseInt(h.humidity),
                wind: parseFloat(h.windspeedKmph),
                precip: parseFloat(h.precipMM),
                cloud: parseInt(h.cloudcover)
            });
        }
        return result;
    }

    displayText(forecast) {
        console.log(chalk.cyan(`Почасовой прогноз для ${this.city} (на ${this.hours} ч):`));
        console.log('Время | Темп. | Ощущ. | Влажн. | Ветер | Осадки | Облачн.');
        console.log('-'.repeat(60));
        for (const f of forecast) {
            let color = chalk.blue;
            if (f.temp >= 0 && f.temp < 15) color = chalk.green;
            else if (f.temp >= 15 && f.temp < 25) color = chalk.yellow;
            else if (f.temp >= 25) color = chalk.red;
            console.log(`${f.time.padEnd(5)} | ${color(f.temp.toFixed(1) + '°C')} | ${f.feels_like.toFixed(1)}°C | ${f.humidity}% | ${f.wind.toFixed(1)} км/ч | ${f.precip.toFixed(1)} мм | ${f.cloud}%`);
        }
    }

    exportJson(forecast, filename) {
        fs.writeFileSync(filename, JSON.stringify(forecast, null, 2));
        console.log(chalk.green(`Экспортировано в ${filename} (JSON)`));
    }

    exportCsv(forecast, filename) {
        const header = 'time,temp,feels_like,humidity,wind,precip,cloud\n';
        const rows = forecast.map(f => `${f.time},${f.temp},${f.feels_like},${f.humidity},${f.wind},${f.precip},${f.cloud}`).join('\n');
        fs.writeFileSync(filename, header + rows);
        console.log(chalk.green(`Экспортировано в ${filename} (CSV)`));
    }

    async run() {
        while (true) {
            const data = await this.fetch();
            if (!data) break;
            const forecast = this.parseHourly(data);
            if (!forecast.length) {
                console.log(chalk.red('Нет данных прогноза'));
                break;
            }
            if (this.format === 'text') {
                this.displayText(forecast);
            } else if (this.format === 'json') {
                if (this.output) {
                    this.exportJson(forecast, this.output);
                } else {
                    console.log(JSON.stringify(forecast, null, 2));
                }
            } else if (this.format === 'csv') {
                if (this.output) {
                    this.exportCsv(forecast, this.output);
                } else {
                    console.log(chalk.yellow('CSV вывод требует --output'));
                }
            }
            if (this.interval === 0) break;
            await new Promise(r => setTimeout(r, this.interval * 1000));
        }
    }
}

program
    .option('--city <city>', 'Город', 'auto')
    .option('--hours <number>', 'Количество часов', parseInt, 12)
    .option('--interval <seconds>', 'Интервал обновления', parseInt, 0)
    .option('--format <format>', 'Формат вывода', 'text')
    .option('--output <file>', 'Файл для сохранения')
    .parse(process.argv);

const opts = program.opts();
const weather = new Weather(opts.city, opts.hours, opts.interval, opts.format, opts.output);
weather.run().catch(console.error);
