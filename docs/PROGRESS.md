# Прогресс реализации

Состояние на 2026-09-09: создана и последовательно расширяется исполняемая основа MOROK на базе официального Telegram Android 12.10.1 (7038). Полное ТЗ ещё не выполнено; фактическое покрытие требований отражено в `FEATURE_PARITY.md`.

## Готово в текущей ветке

- собственные package ID, название, launcher aliases, ресурсы бренда и отдельная debug-установка;
- воспроизводимая привязка к upstream и десяти submodule commit, preflight-проверки и CI;
- локальные MOROK Settings: режимы «Как в Telegram / Без прозрачности / Минимум эффектов» с точным preview перед применением, независимое управление доступным blur и уменьшением эффектов, ссылки на штатные темы и энергосбережение; профили «Обычный / Скрытный / Рабочий / Экономный», сохранённый «Свой» и возврат предыдущего состояния управляют оформлением, LiteMode autoplay, глобальной privacy-политикой и текстом уведомлений, сохраняя маршрут, качество кружочков и chat exceptions; явный SAF export/import переносимых настроек с версией формата, строгой проверкой и preview diff без auth/account/chat/proxy/archive/Memory данных;
- MOROK Memory: ручные карточки, заметки, теги, поиск, версии и напоминания; выключенный по умолчанию account-scoped автоархив разрешённых live/difference/channel-difference message payload в выбранных чатах; pending bridge для fast new→edit/delete; явный импорт до 100 уже локальных сообщений выбранного чата без сети; cache-only повтор оригинала, deduplicated blob references и диагностика хранилища; Android Keystore/Tink, account isolation, очистка при logout и bounded encrypted WAL/replay;
- MOROK connection: direct/manual/auto, импорт, проверки, ротация, подписанный пул с anti-rollback и явное отсутствие скрытого direct fallback;
- экспериментальная privacy-основа: account-local пресет «Призрак», фактическое подавление обычных typing/recording/upload/sticker/emoji activity actions, foreground online-status, обычного server read cursor с сохранением локального read-state, content-read входящих voice/round video и story-view; явный mark-read и opt-in read-on-reply синхронизируют cursor; отдельный opt-in ставит поддержанные Ghost-отправки в видимое серверное расписание на минуту без silent fallback; chat exceptions возвращают штатное поведение в выбранном чате и управляются общим списком;
- экспериментальное улучшение кружочков, выключенное по умолчанию: Auto / Economy / High, проверка Camera2 source и AVC surface encoder, поддержанные AF/EIS/FPS requests, корректные enhanced metadata и откат к штатному профилю до начала записи; отдельная opt-in диагностика хранит до 40 metadata-only событий и позволяет явно скопировать/очистить отчёт; физическое качество и доставка получателю ещё не подтверждены;
- локальная безопасность, выключенная по умолчанию: режим защищённого Android-окна для main/bubble/external и известных media/payment/translate путей, ссылка на штатный код-пароль Telegram, подтверждение исходящих личных audio/video calls после permissions и до initiateCall, preview-подтверждение кружочков перед отправкой, прямой account-local переключатель текста уведомлений;
- жесты: account-local выключатель chat-message реакции по двойному нажатию, маршруты в штатные Telegram reaction picker и выбор действия свайпа списка чатов, независимость от профилей приложения;
- единая read-only диагностика сборки/устройства, прокси, автоархива/Памяти и кружочков с переходами в существующие экраны и явным копированием обезличенного отчёта;
- локальные псевдонимы и личные заметки обычных чатов: нативный редактор из меню чата, общий searchable список и clear-all в настройках, локальная подмена только текста заголовка, account-bound Android Keystore/Tink storage, строгие лимиты и revocation/очистка при logout; серверный профиль, secret/Saved chats и экспорт настроек не затрагиваются;
- шаблоны собственной прокси-инфраструктуры и документация сборки, архитектуры, обновления и ограничений.

## Проверено

- `./scripts/check.sh`: upstream lock и 10 submodules, secret-free diagnostic report/source checks, 12 round-video diagnostic domain checks + 7 integration-инвариантов, 35 Memory domain checks, 9 Memory hook/cache-only checks, 6 proxy transaction cases, 34 proxy core checks, appearance mode mapping, settings migration/isolation/archive/safety/interaction policy, strict secret-free transfer codec, local app-profile и safety call/screen hook checks, совместимость Python/OpenSSL signer с Java verifier;
- chat metadata JVM/source checks: identity/нормализация/границы, Tink/Keystore/AtomicFile/AAD, stable-user logout ordering, отсутствие сетевого слоя и исключение secret/Saved chats;
- `:TMessagesProj_App:assembleAfatDebug`: успешная arm64-v8a debug-сборка;
- `:TMessagesProj_App:connectedAfatDebugAndroidTest`: 1/1 Android instrumentation test на Android 16 API 36, включая отказ при tampering и cross-account replay;
- APK проверен `aapt2` и `apksigner`: `io.github.haewho.morok.beta`, version 12.10.1/70389, только `arm64-v8a`, debug certificate;
- на эмуляторе Android 16 APK установлен и холодно запущен; вручную открыты intro, MOROK connection, MOROK Settings, экран переноса, профили приложения, жесты, единая диагностика и безопасность; в Local tools виден новый список «Chat aliases and notes», а до входа он корректно отказывает без сбоя; диагностика показывает сборку/устройство, состояние direct-прокси, явное отсутствие account-local данных до входа, состояние кружочков и выполняет действие копирования без сбоя; Android shell не имеет доступа к clipboard приложения, точный отчёт проверен pure-Java тестом; экран жестов до входа показывает явное требование авторизации без сбоя навигации; защита экрана добавляет `FLAG_SECURE`, даёт полностью чёрный screenshot, сохраняется после cold restart и корректно снимается; ссылка открывает штатный setup кода-пароля, account-local уведомления до входа показывают явный отказ, переключатель preview кружочков виден и переживает restart. Авторизованные aliases/notes, diagnostics/archive/Memory, double-tap/reaction/swipe, profile apply/Custom/Previous, SAF export/import, реальные notification preview, подтверждение звонка и камера/отправка кружочка ещё не проверены на устройстве.

Локальный проверенный APK и машинный отчёт находятся в `artifacts/` и намеренно исключены из Git. Это debug/test-only сборка, не релиз для распространения.

## Внешние зависимости

Для проверки входа и реальной эксплуатации нужны собственные Telegram `api_id`/`api_hash`. Для push-уведомлений нужен собственный Firebase project. Для release APK нужен постоянный release keystore. Автоматическому режиму соединения нужны минимум два независимо размещённых прокси, HTTPS primary/backup endpoints и ключ подписи владельца.

Проверки на физическом устройстве, в российских сетях, с реальным аккаунтом, звонками, FCM, двумя аккаунтами, Doze и обновлением между двумя подписанными версиями ещё не выполнялись. Для кружочков отдельно не выполнена матрица OnePlus Android 16 / Samsung Android 10 / официальный клиент получателя из `ROUND_VIDEO_TESTS.md`.

## Следующий этап

Следующий P0-этап — предоставить собственные API/Firebase/release/proxy параметры, провести вход и сетевую матрицу на физическом устройстве, затем проверить автоархив/WAL/cache-only импорт и catch-up/fast edit-delete вторым аккаунтом. Без этого остаются неподтверждёнными реальные delivery/update комбинации и fault injection между WAL и индексом.
