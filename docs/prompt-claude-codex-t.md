Проверь незакоммиченный diff в G:\DEV\projects\pulk, ветка feat/handsdown: T3 (добровольные локальные траектории только в учебных полях, JSONL, replay, выдача через закрытый TraceProvider) и T6 (ScreenAccessibilityTest, фокус калибровки). Задача: G:\DEV\drive-sync\tasks\2026-10-26-pulk-handsdown; результат Codex автоматически сохраняется в result-codex-t.md там же.

Особенно проверь приватность детей, исключение паролей/чужих приложений, временное чтение только выбранной копии, отзыв старых адресов и стирание. Новых библиотек и разрешений нет. Веб-запись не делалась (см. docs/gesture-traces.md). Проверки T6 не проверяют нарисованные буквы/зоны жестов и не заменяют TalkBack на устройстве.

Перезапусти Android и веб-тесты; шум normal ≥ 0.978, sloppy ≥ 0.856. Для этой песочницы использовались только служебные переменные процесса:
```powershell
$env:GRADLE_USER_HOME = 'C:\Temp\pulk-codex-gradle'
$env:ANDROID_USER_HOME = 'C:\Temp\pulk-codex-home\.android'
$env:JAVA_TOOL_OPTIONS = '-Duser.home=C:\Temp\pulk-codex-home -Drobolectric.offline=true -Drobolectric.dependency.dir=C:\Temp\pulk-codex-home\.m2\repository\org\robolectric\android-all-instrumented\14-robolectric-10818077-i6'
powershell -NoProfile -File gw.ps1 testDebugUnitTest --offline
node tools/test-web.js
```
Коммит не сделан: .git/index.lock — Permission denied. После проверки закоммить. Таблицу Android/веб на сайте Дуги здесь не меняли: она вне разрешённой папки. docs/VISION.md не менять; без main, push, сети и новых разрешений. Проверку сдай review-claude.md в папку задачи, с доказательствами и блоком «Дальше».
