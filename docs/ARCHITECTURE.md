# Архитектура

Официальное дерево Telegram и его native MTProto/медиа-стек сохранены. `upstream.lock.json` фиксирует источник и submodules. Основной APK-модуль переиспользуется; другие upstream packaging-модули оставлены в дереве, но исключены из настроек сборки MOROK, чтобы случайно не выпустить чужую identity.

- `org.morok.settings` — версия схемы, настройки устройства и пространство настроек по устойчивому ID пользователя; строгий secret-free профиль переноса и локальные whole-app профили с отдельными версиями форматов. Именованные профили координируют существующие настройки, но не управляют сетевым маршрутом и server preferences.
- `org.morok.appearance` — политика эффектов поверх существующих render paths и bounded device-wide плотность обычного dialog list; перечень покрытия указан отдельно.
- `org.morok.memory` / `history` — ручные карточки, allowlisted автоархив live и catch-up message payload, явный ограниченный импорт из локальной Telegram SQLite, приватное хранение, encrypted journal/replay для new/edit/delete событий и напоминания. Автоархив выключен по умолчанию и использует настройки устойчивого ID аккаунта; bounded age/storage/original-size/network policy удаляет только автоматические карточки и никогда не запускает Telegram history/media loader.
- `org.morok.chatmeta` — bounded encrypted хранилище локальных псевдонимов и заметок обычных чатов по устойчивому ID аккаунта; для dialog list оно публикует immutable-after-publication memory snapshot после объединённого фонового decrypt и не читает/не меняет Telegram database/profile.
- `org.morok.templates` — bounded encrypted шаблоны plain-text ответов, чистая локальная подстановка документированных переменных и политика вставки в существующий composer draft; результат сначала показывается в preview, отправка остаётся отдельным штатным действием пользователя.
- `org.morok.update` — explicit-only проверка RSA-подписанного bounded manifest, anti-rollback/expiry, HTTPS primary/backup, потоковая проверка APK и same-certificate handoff штатному Android installer; без доверительной конфигурации сеть отключена.
- `org.morok.drafts` — отдельные bounded encrypted снимки plain-text редактора по account/dialog/topic и чистая проверка восстановления; штатная синхронизация Telegram drafts не подменяется.
- `org.morok.proxy` — проверка и выбор узлов через штатный `ConnectionsManager`; никакого второго сетевого стека.
- `org.morok.safety` — локальный confirmation gate перед исходящим private-call `initiateCall` и единая read-only политика secure-window; сетевых или auth-операций здесь нет.
- `org.morok.interactions` — read-only account-local gate для жестов; выбор самой быстрой реакции остаётся в штатном `MediaDataController`, а свайп сообщения маршрутизируется в upstream reply, локальную Memory queue или не перехватывается.
- `org.morok.diagnostics` — неизменяемый обезличенный снимок локальных состояний и агрегированных счётчиков для явного копирования.
- `org.morok.integration` — собственное имя, сохраняемое при загрузке облачных language packs.
- `org.morok.ui` — обычные Telegram `BaseFragment`, встроенные в навигацию приложения.

Профиль переноса настроек не является резервной копией аккаунта. SAF-файл не содержит идентификаторы аккаунта/чатов, Telegram auth, Memory, архив и прокси; импорт показывает diff и применяет только локальные MOROK-настройки после явного подтверждения. Подробности — в [SETTINGS_TRANSFER](SETTINGS_TRANSFER.md).

Локальные профили «Обычный / Скрытный / Рабочий / Экономный» и сохранённый «Свой» описаны в [APP_PROFILES](APP_PROFILES.md). Они показывают итоговые значения до применения, сохраняют предыдущий снимок и не подменяют connection policy.

Защита окна и подтверждение личных звонков описаны в [SAFETY](SAFETY.md). Они opt-in и используют штатные Android/Telegram границы: `FLAG_SECURE`, permission gate и `VoIPHelper`.

Настройки реакции по двойному нажатию и свайпа сообщения описаны в [INTERACTIONS](INTERACTIONS.md). Они переиспользуют штатные reaction/reply paths, а локальное сохранение проходит существующую Memory eligibility.

Единый read-only экран диагностики и точный состав копируемого отчёта описаны в [DIAGNOSTICS](DIAGNOSTICS.md).

Локальные псевдонимы и заметки чатов, их пределы и logout-erasure описаны в [CHAT_METADATA](CHAT_METADATA.md).

Зашифрованные шаблоны и composer-only путь вставки описаны в [REPLY_TEMPLATES](REPLY_TEMPLATES.md).
Собственный канал обновлений и его границы доверия описаны в [APP_UPDATES](APP_UPDATES.md).

Явные локальные снимки текста редактора и их границы описаны в [SAVED_DRAFTS](SAVED_DRAFTS.md).

Переиспользование штатного частичного выделения и guarded-вставка в composer описаны в [TEXT_SELECTION](TEXT_SELECTION.md).

Базовые сообщения продолжают жить в официальной БД. Память не заменяет сообщения «зомби-записями» и не отменяет delete updates. Обработка карточки не отправляет сообщение собеседнику. Переход в исходный чат использует обычную семантику Telegram с учётом выбранной экспериментальной privacy-политики.

FCM, Telegram API, maps/OAuth и подпись APK настраиваются независимо. Отсутствующие credentials не заменяются публичными upstream-ключами. Прокси Telegram не обещает туннелирование внешних сайтов, FCM или всего телефона.
