# Прогресс реализации

Состояние на 2026-09-06: создана и проверена первая исполняемая основа MOROK на базе официального Telegram Android 12.10.1 (7038), commit `62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c`. Полное ТЗ ещё не выполнено; фактическое покрытие требований отражено в `FEATURE_PARITY.md`.

## Готово в текущей ветке

- собственные package ID, название, launcher aliases, ресурсы бренда и отдельная debug-установка;
- воспроизводимая привязка к upstream и десяти submodule commit, preflight-проверки и CI;
- локальные MOROK Settings: управление доступным blur, уменьшение эффектов, ссылки на штатные темы и энергосбережение;
- MOROK Memory: ручные карточки, заметки, теги, поиск, версии и напоминания; выключенный по умолчанию account-scoped автоархив новых разрешённых сообщений в выбранных чатах; явный импорт до 100 уже локальных сообщений выбранного чата без сети; Android Keystore/Tink, account isolation, очистка при logout и bounded encrypted WAL/replay для standard new/edit/delete/history updates;
- MOROK connection: direct/manual/auto, импорт, проверки, ротация, подписанный пул с anti-rollback и явное отсутствие скрытого direct fallback;
- экспериментальная privacy-основа: account-local пресет «Призрак», фактическое подавление обычных typing/recording/upload/sticker/emoji activity actions, foreground online-status, обычного server read cursor с сохранением локального read-state, content-read входящих voice/round video и story-view; явный mark-read и opt-in read-on-reply синхронизируют cursor; отдельный opt-in ставит поддержанные Ghost-отправки в видимое серверное расписание на минуту без silent fallback; chat exceptions возвращают штатное поведение в выбранном чате и управляются общим списком;
- экспериментальное улучшение кружочков, выключенное по умолчанию: Auto / Economy / High, проверка Camera2 source и AVC surface encoder, поддержанные AF/EIS/FPS requests, корректные enhanced metadata и откат к штатному профилю до начала записи; физическое качество и доставка получателю ещё не подтверждены;
- шаблоны собственной прокси-инфраструктуры и документация сборки, архитектуры, обновления и ограничений.

## Проверено

- `./scripts/check.sh`: upstream lock и 10 submodules, 28 Memory domain checks, 6 proxy transaction cases, 34 proxy core checks, settings migration/isolation/archive-policy checks, совместимость Python/OpenSSL signer с Java verifier;
- `:TMessagesProj_App:assembleAfatDebug`: успешная arm64-v8a debug-сборка;
- `:TMessagesProj_App:connectedAfatDebugAndroidTest`: 1/1 Android instrumentation test на Android 16 API 36, включая отказ при tampering и cross-account replay;
- APK проверен `aapt2` и `apksigner`: `io.github.haewho.morok.beta`, version 12.10.1/70389, только `arm64-v8a`, debug certificate;
- на эмуляторе Android 16 APK установлен и холодно запущен; вручную открыты intro, MOROK connection и MOROK Settings, проверена блокировка Memory до входа; падений MOROK в logcat нет.

Локальный проверенный APK и машинный отчёт находятся в `artifacts/` и намеренно исключены из Git. Это debug/test-only сборка, не релиз для распространения.

## Внешние зависимости

Для проверки входа и реальной эксплуатации нужны собственные Telegram `api_id`/`api_hash`. Для push-уведомлений нужен собственный Firebase project. Для release APK нужен постоянный release keystore. Автоматическому режиму соединения нужны минимум два независимо размещённых прокси, HTTPS primary/backup endpoints и ключ подписи владельца.

Проверки на физическом устройстве, в российских сетях, с реальным аккаунтом, звонками, FCM, двумя аккаунтами, Doze и обновлением между двумя подписанными версиями ещё не выполнялись. Для кружочков отдельно не выполнена матрица OnePlus Android 16 / Samsung Android 10 / официальный клиент получателя из `ROUND_VIDEO_TESTS.md`.

## Следующий этап

Следующий P0-этап — предоставить собственные API/Firebase/release/proxy параметры, провести вход и сетевую матрицу на физическом устройстве, затем проверить автоархив/WAL/cache-only импорт/server effects вторым аккаунтом и расширить покрытие difference/channel-difference.
