# Сторонние данные

- `app/src/main/assets/dict_ru.txt`, `dict_en.txt` — первые 25 000 (RU) и 20 000 (EN) слов из частотных списков
  [FrequencyWords](https://github.com/hermitdave/FrequencyWords) (Hermit Dave, 2018, по субтитрам OpenSubtitles).
  Код репозитория — MIT, списки слов — CC BY-SA 4.0. Оставлены только слова из букв, от двух букв.
- `app/src/main/assets/next_ru.txt`, `next_en.txt` — какие слова обычно идут следом, посчитано по предложениям
  [Tatoeba](https://tatoeba.org) (CC BY 2.0 FR) скриптом `tools/make-next.js`; примерные имена корпуса (Том, Мэри…) убраны.
- Фильтр дрожи — алгоритм 1€ filter: G. Casiez, N. Roussel, D. Vogel. «1€ Filter: A Simple Speed-based Low-pass Filter for Noisy Input in Interactive Systems», CHI 2012.
