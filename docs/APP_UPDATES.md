# Обновления MOROK на телефоне

В MOROK сохранено `BuildVars.CHECK_UPDATES = false`: клиент не принимает и не устанавливает APK из upstream Telegram. Собственный экран находится в MOROK Settings → «Обновления Морока» и ничего не проверяет в фоне. Проверка manifest, скачивание APK и открытие системного установщика — три отдельных явных действия пользователя.

## Доверие и manifest

Доверие задаётся только включённым в APK файлом `assets/morok-update-trust.properties`: до четырёх RSA public keys и основной/резервный HTTPS endpoint. Пример находится в `infra/morok-update-trust.properties.example`. Пустой пример не включён как рабочая конфигурация; текущая development-сборка честно показывает, что источник обновлений не настроен.

Endpoint возвращает ASCII envelope `MOROK-SIGNED-UPDATE-1` размером не более 48 КиБ. RSA-2048+ подпись SHA-256 покрывает точные UTF-8 байты payload. Payload содержит монотонный `version_code`, version name, точные package/ABI, срок действия до 31 дня, размер/SHA-256/certificate SHA-256 APK, HTTPS URL, Telegram base, MOROK commit и bounded changelog. Клиент отклоняет неизвестный ключ, слабый RSA, лишние/неполные поля, неверную кодировку, повтор номера версии с другим payload, rollback, будущую дату, истечение, HTTP/credentials/fragment URL и несовпадающий target.

Последний успешно проверенный envelope сохраняется локально вместе с version/digest. Он используется при недоступных зеркалах только пока подпись и срок действия остаются валидными. Redirects выключены. Manifest не содержит исполняемого кода и не может добавить новый ключ или endpoint.

## APK и установка

Загрузка начинается только отдельной кнопкой и использует ровно HTTPS URL из выбранного подписанного manifest. Файл пишется в private cache `morok-updates/update.tmp` с верхней границей 512 МиБ и ожидаемым точным размером, одновременно считается SHA-256, затем выполняется `fsync`. До атомарного перехода в готовое состояние Android `PackageManager` повторно проверяет package, versionCode и единственный signer. SHA-256 signer certificate должен одновременно совпасть с manifest, установленным MOROK и скачанным APK. Ротация Android signing key этой версией updater не поддерживается.

Только прошедший все проверки файл выдаётся через закрытый `FileProvider` с временным read grant. На Android 8+ при необходимости сначала открывается системное разрешение установки из этого источника. Затем запускается обычный Android package installer: пользователь всегда видит и подтверждает установку, silent install отсутствует. Android отдельно не позволит downgrade либо APK с несовместимой подписью.

## Подготовка публикации

1. Собрать release APK с постоянным владельческим keystore и монотонно увеличить versionCode.
2. Получить lowercase SHA-256 DER-сертификата signer через `apksigner verify --print-certs`.
3. Заполнить закрытую копию `infra/update.json.example` реальными timestamps, HTTPS APK URL, package, version, certificate, Telegram base, commit и changelog.
4. Подписать: `python3 infra/sign-update.py --input /private/update.json --apk /release/MOROK.apk --private-key /secure/update-key.pem --key-id owner-2026 --output /private/update.txt`.
5. До release встроить соответствующий public DER key и два owner-controlled endpoint в `TMessagesProj/src/main/assets/morok-update-trust.properties`, затем собрать APK заново.
6. Опубликовать один и тот же signed envelope на primary/backup, а APK — по точному URL из payload. Проверить upgrade поверх предыдущей подписанной версии с сохранением settings/Memory и отдельно отказ tampered/wrong-cert APK.

Приватный RSA key, release keystore и пароли не входят в Git, APK и manifest. Настоящие endpoints, ключ и двухверсийная device-проверка пока отсутствуют, поэтому текущий код является готовым безопасным клиентским путём, а не работающим публичным каналом обновлений.
