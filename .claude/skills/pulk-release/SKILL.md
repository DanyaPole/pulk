---
name: pulk-release
description: Как собрать, проверить и выложить новую версию Android-клавиатуры Пульк (C:\dev\pulk) на страницу danyapole.github.io/duga/pulk/. Использовать, когда нужно выпустить версию, прогнать тесты или посмотреть клавиатуру картинками.
---

# Выпуск Пулька

1. Тесты и картинки: `C:\dev\pulk\gw.ps1 testDebugUnitTest` (через PowerShell или `powershell -NoProfile -File gw.ps1 …` из bash в C:\dev\pulk). Сводка: число тестов по файлам. Упавшие: читать `app/build/test-results/testDebugUnitTest/*.xml` (вывод PowerShell в cp866 ломает русский). Картинки клавиатуры и экранов — `app/build/shots/*.png`, смотреть их глазами после правок вида.
2. Версия: в `app/build.gradle.kts` поднять `versionCode` и `versionName`.
3. Сборка: `gw.ps1 assembleRelease` → `app/build/outputs/apk/release/app-release.apk`, подписан ключом из `keys/` (не в git, автор хранит копию). Скопировать в `dist/pulk-<версия>.apk`, старый удалить. Проверить: `aapt2 dump badging` — версия и единственное разрешение VIBRATE.
4. Коммит в `C:\dev\pulk` от `DanyaPole <DanyaPole@users.noreply.github.com>` с подписью `Co-Authored-By`.
5. Страница: скопировать APK в `C:\dev\duga\pulk\pulk.apk`, в `pulk/index.html` поменять номер в кнопке «Скачать» и, если надо, карточки «Что внутри»; `node tools/build-pages.js`; коммит и `git push` в репозитории duga (Pages соберутся сами). Дождаться, пока `https://danyapole.github.io/duga/pulk/pulk.apk` отдаёт новый размер.
6. Отправить APK автору файлом и коротко описать, что нового и что не проверено на телефоне.

Подводные камни: в `local.properties` путь `C:/Android/sdk` (прямые слэши); большие правки Kotlin — через node-скрипт в scratchpad с проверкой якорей, а не sed с кириллицей и кавычками; таймеры и звук в Robolectric — `idleFor`.
