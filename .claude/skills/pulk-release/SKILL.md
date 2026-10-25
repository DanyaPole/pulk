---
name: pulk-release
description: Как проверить и выпустить Пульк в обеих версиях — Android-клавиатура (C:\dev\projects\pulk) и веб-версия (C:\dev\projects\duga\pulk\web) — на danyapole.github.io/duga/pulk/. Использовать, когда нужно прогнать тесты, проверить экраны, выпустить версию или обновить страницу скачивания и инструкции.
---

# Проверка и выпуск Пулька

## Проверка после каждого шага
1. Android: `powershell -NoProfile -File gw.ps1 testDebugUnitTest` в `C:\dev\projects\pulk`, в фоне; пока идёт — файлы не править (иначе компилирует полуготовое). Сводка: `grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml` и сложить; упавшие — читать эти xml (вывод PowerShell в cp866). Картинки — `app/build/shots/*.png`, смотреть глазами после правок вида.
2. Общие данные изменились в Kotlin (буквы, фразы, тренировки, пруд) → `node tools/build-pulk-web.js` в `C:\dev\projects\duga`.
3. Веб: `node tools/build-pages.js` в `C:\dev\projects\duga` — сначала идут 12 тестов ядра (`tools/test-pulk-web.js`), упал тест — сборка останавливается. Затем превью `duga-site` (порт 5174, `/pulk/web/index.html`), прогнать экраны через JS: окно превью часто скрыто — скриншоты обрезаются, rAF не идёт. Консоль без ошибок.
4. Честно сказать автору, что проверено (тесты, браузер на компьютере), а что нет (живой телефон).

## Выпуск Android
1. Версия: `versionCode`/`versionName` в `app/build.gradle.kts`.
2. `gw.ps1 assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (подпись из `keys/`, не в git). Скопировать в `dist/pulk-<версия>.apk`. По желанию `aapt2 dump badging`: версия и единственное разрешение VIBRATE.
3. Коммит в `C:\dev\projects\pulk`: `git -c user.name=DanyaPole -c user.email=DanyaPole@users.noreply.github.com commit …` с `Co-Authored-By`.

## Сайт (обе версии)
1. APK → `C:\dev\projects\duga\pulk\pulk.apk`; номер в кнопке «Скачать» на `pulk/index.html`.
2. Изменились возможности — обновить таблицу «Android или браузер» (`pulk/index.html`, id="diff") и инструкции: тексты в `tools/make-guides.js`, потом `node tools/make-guides.js`.
3. `node tools/build-pages.js`; коммит и `git push` в duga; дождаться, что страница отдаёт новое (`curl` по адресу, размер APK).
4. Коротко автору: что нового, что не проверено на телефоне.

Подводные камни: `local.properties` — `C:/Android/sdk`; большие правки — node-скрипт в scratchpad с проверкой якорей (в JS-шаблонах экранировать `${…}`, после вставки проверить `\n` в Kotlin-строках); таймеры в Robolectric — `shadowOf(Looper.getMainLooper()).idleFor(Duration)`; локальный сервер не отдаёт папку без `index.html` в адресе.
