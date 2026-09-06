# Сборка MOROK

Основной модуль APK — `TMessagesProj_App`. Внутренние Java/JNI namespace Telegram сохранены. Постоянный applicationId: `io.github.haewho.morok`; разработческий APK: `io.github.haewho.morok.beta`. Разные типы подписи устанавливаются раздельно. APK содержит arm64-v8a.

## Закреплённая среда

| Инструмент | Версия из upstream |
|---|---|
| JDK | 17 в upstream Dockerfile; локально JBR 21.0.9 |
| Gradle wrapper | 8.11.1 |
| Android Gradle Plugin | 8.10.1 |
| Kotlin plugin | 2.1.0 |
| Android SDK / build tools | 36 / 36.0.0 |
| NDK | 27.2.12479018 |
| CMake | 3.22.1 |
| minSdk / targetSdk | 21 / 36; Память требует API23 Keystore |

`git submodule update --init --recursive --depth=1 --jobs=3` получает закреплённые SHA. `python3 scripts/check_upstream.py` проверяет их. Android SDK packages: `platforms;android-36`, `build-tools;36.0.0`, `ndk;27.2.12479018`, `cmake;3.22.1`. Установите их штатным SDK Manager с принятием лицензий SDK. Не подменяйте NDK текущей версией Android Studio.

На macOS скрипт находит JBR в Android Studio и SDK в `~/Library/Android/sdk`; в другой среде задайте `JAVA_HOME` и `ANDROID_HOME`. Экономный профиль: heap3GB, metaspace768MB, 2 workers, без параллельных Gradle-проектов. Не запускайте одновременно исходную и модифицированную сборки. Полный arm64 native-код upstream компилируется, функции камеры/звонков/медиа не вырезаются.

## Разработка

Выполните `./scripts/build.sh debug`. Без собственных API-данных получается только сборка для разработки интерфейса: экран входа объясняет отсутствие конфигурации, авторизация не заявляется. Debug подписывается локальным стандартным Android debug keystore; если его ещё нет на машине или CI runner, скрипт создаёт новый host-local ключ. Этот ключ не используется для release и не заимствован у Telegram. Проверенный APK копируется в `artifacts/` вместе с SHA256 и сведениями о подписи. AGP при injected ABI оставляет исходный APK в `build/intermediates/apk/afat/debug/`, что учитывает скрипт отчёта.

Для входа скопируйте `morok.local.properties.example` в `morok.local.properties` и заполните собственные `MOROK_API_ID` и `MOROK_API_HASH`, полученные через [официальную форму](https://core.telegram.org/api/obtaining_api_id). Не присылайте коды входа/2FA в чат. API hash извлекается из APK и не является серверным секретом.

Для FCM поместите собственный `google-services.json` в `TMessagesProj_App/`, зарегистрировав нужный applicationId, включая `.beta` для debug. При отсутствии файла Google Services plugin и получение FCM токена отключены. Это ограничивает фоновую доставку; обычное поддержание Telegram-соединения не эквивалентно проверенному FCM. Maps/OAuth/SMS hash задаются отдельно в локальной конфигурации при необходимости. Passkeys upstream доступны только официальным app IDs и отключены; пароль/код и 2FA остаются штатными сценариями после настройки API.

## Распространяемая сборка

Заполните все `MOROK_KEYSTORE`, `MOROK_STORE_PASSWORD`, `MOROK_KEY_ALIAS`, `MOROK_KEY_PASSWORD` и выполните `./scripts/build.sh release`. Используйте постоянный приватный ключ и храните резервную копию вне git. Потеря ключа лишает возможности обычного обновления поверх установленного APK. Публичный keystore upstream удалён из рабочей версии; исходная история лицензированного проекта сохранена.

Для каждого передаваемого APK запишите SHA256, applicationId, versionName/versionCode и `apksigner verify --print-certs`. Debug APK не является повседневным релизом. Текущие реальные результаты сборки — в `PROGRESS.md`.

## Linux x86_64 / Docker

`Dockerfile` сохраняет upstream Gradle/JDK17 и Android SDK/NDK/CMake pins. Из корня: `docker build --platform linux/amd64 -t morok-builder .`, затем `mkdir -p artifacts` и `docker run --rm --platform linux/amd64 -v "$PWD:/source:ro" -v "$PWD/artifacts:/artifacts" morok-builder`. Исходники копируются без локального build/cache, сборка выполняется в контейнере, APK/метаданные возвращаются в artifacts. Локальная конфигурация при наличии копируется только внутрь этого локального build-контейнера; она не попадает в Docker image. Данный Linux/container путь подготовлен, но в этой сессии выполнена macOS-сборка, не Docker.
