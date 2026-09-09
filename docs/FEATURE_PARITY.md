# Матрица функций

Проверка источников: 2026-09-06. База MOROK: Telegram12.10.1/7038; закреплён release-коммит. Текущий официальный APK и полное соответствие всех новых серверных функций исходникам отдельно не проверены. [Release notes25.08.2026](https://telegram.org/blog/welcome-messages-buttons-TG-13).

Референсы: [exteraGram features](https://exteragram.app/), [публичные исходники](https://github.com/exteraSquad/exteraGram), [AyuGram Android](https://docs.ayugram.one/android/), [Ghost Mode](https://docs.ayugram.one/shared/ghost/). Номер фактически распространяемого APK и его соответствие публичному коду не установлены. Код этих клиентов не переносился. «XTelegram»/«Viogram» не отождествляются с ними без подтверждения.

«Реализуется» означает частичный код, а не готовность беты. Реальные сборки/проверки отдельно в `PROGRESS.md`.

| Требование | Приоритет | Статус | Фактический объём / остаток |
|---|---|---|---|
| Текст, voice/video, файлы, реакции, группы/темы, камера, аккаунты | P0 | реализуется | Официальный клиент сохранён; вход/регрессия требуют API-данных и устройства |
| Звонки audio/video/group | P0 | требование | Стек сохранён, транспортные проверки не проведены |
| Бренд, identity, providers, контакты | P0 | реализуется | MOROK/Морок, Личина, отдельный пакет; требуется соседняя установка |
| Liquid Glass on/off, минимум эффектов | P0 | реализуется | Три именованных режима с review-before-apply и независимые переключатели управляют общими blur3 capture/draw paths; старые самостоятельные scrim paths охвачены не полностью |
| Светлая/тёмная/AMOLED, семантические overrides, dynamic colors | P0 | реализуется | Доступ к штатным темам; полная карта override/контраста впереди |
| Схема настроек и стабильный ID аккаунта | P0 | реализовано | Отдельный слой с JVM-тестами; UI использует настройки устройства |
| Автоматический архив new/edit/delete/difference | P0 | реализуется | OFF-by-default account policy и allowlist до 256 чатов; live, direct difference/channel-difference message payload и стандартные edit/delete/history updates проходят через bounded encrypted WAL/replay. Есть pending bridge для fast new→edit/delete и явный cache-only импорт последних 100 локальных сообщений; payload-less differenceTooLong, более глубокий импорт и device crash/catch-up тест впереди |
| Действительные файлы вне обычного кеша | P0 | реализуется | Ручные и автоматические карточки сохраняют только полные уже скачанные байты, переиспользуют общий зашифрованный blob до удаления последней ссылки; есть cache-only retry и счётчики хранилища. Device/fault проверка и отдельный thumbnail UI впереди |
| Шифрование, logout, replay/tombstones, лимиты, поиск | P0 | реализуется | Слой Памяти; device/crash/key-loss проверки впереди |
| Ghost preset, online/read/content-read/typing, исключения, local cursor | P0 | реализуется | Account-local Ghost preset; semantic suppression typing/recording/upload/sticker/emoji, ordinary foreground online, ordinary server read с локальным cursor, content-read входящих voice/round video и story-view; явный mark-read синхронизирует; chat exceptions имеют централизованный список/удаление; API terms conflict сохраняется |
| Stories, mark-read, read-on-reply, scheduled ghost send | P0 | реализуется | Story read/view подавляется после локального state; read-on-reply срабатывает после успешной немедленной отправки; отдельный opt-in ставит поддержанные Ghost-отправки в видимое серверное расписание на минуту и блокирует неподдержанные без immediate fallback; нужны API credentials и второй аккаунт |
| Прокси до входа, импорт, проверки и ротация | P0 | реализуется | Штатный транспорт; целевая сеть не проверена |
| Подписанный bootstrap, зеркала, antirollback и 2 независимых узла | P0 | заблокировано зависимостью | Основы/шаблоны; нет серверов, endpoints и ключей владельца |
| Память: карточка/цитата/файл/заметка/теги/поиск | P0 | реализуется | Native экран, encrypted private store, cache-only повтор оригинала и диагностика хранилища |
| Память: reminder/cancel/restart/правильный контекст | P0 | реализуется | Local alarms/stable-user routing; Doze/permission/device проверки впереди |
| Подпись и данные при update APK | P0 | заблокировано зависимостью | Нет release key; двухверсийный тест ещё нужен |
| Upstream lock, upgrade worktree, CI | P0 | реализуется | Скрипты подготовлены, перенос разных upstream-версий ещё не проверен |
| Подписанный APK-updater | P0 | требование | Upstream APK-updater отключён |
| Навигация: tabs/folders/order/start screen | P1 | требование | Штатная навигация сохранена |
| Плотность/аватары/пузыри/шрифты/timestamps/анимации | P1 | требование | Полный собственный набор впереди |
| Жесты/long press/быстрые действия | P1 | реализуется | «Запомнить»; account-local выключатель реакции по двойному нажатию, штатный выбор emoji и встроенный выбор действия свайпа списка чатов. Свайпы сообщений и остальные действия впереди |
| Copy selection/templates/drafts/chat aliases/notes | P1 | реализуется | Локальные alias + private note имеют account-bound Tink/Keystore store и searchable список. Reply templates вставляются в cursor без auto-send. Отдельный encrypted plain-text draft snapshot привязан к chat/topic и восстанавливается с подтверждением замены. Штатные handles частичного выделения сохранены; guarded-действие вставляет выбранный текст прямо в composer только при разрешённом upstream Copy. Secret/noforwards исключены; alias в основном списке Telegram, formatting/variables и снимки reply/effect/media state впереди |
| Call/video confirm/double tap/local undo send | P1 | реализуется | Opt-in подтверждение исходящих личных audio/video calls стоит после permissions и до initiateCall; отдельный opt-in заменяет немедленную отправку кружочка штатным preview; double tap reaction можно отключить и выбрать через штатный picker. Настоящий pre-network undo send впереди; фиктивной отмены после отправки нет |
| Плеер/позиция/скорость/sleep timer/autoplay | P1 | реализуется | Штатный плеер сохранён; именованные профили управляют существующими LiteMode autoplay video/GIF. Позиция, sleep timer и дополнительные скорости впереди |
| Downloads queue/pause/priority/traffic limits | P1 | требование | Штатный stack; ускорение не заявляется |
| Единая диагностика соединения/архива | P1 | реализуется | Read-only экран показывает сборку/устройство, прокси, автоархив/Память и кружочки; копируемый отчёт исключает identity, endpoints, content и secrets. Нужна authenticated/device error-state матрица |
| Local filters/spoilers/noise | P1 | требование | Sponsored messages и серверная синхронизация сохраняются |
| Notifications/lock/screen sharing/privacy profiles | P1 | реализуется | Четыре локальных профиля и Custom; Stealth сужает notification preview. Отдельный opt-in `FLAG_SECURE` охватывает main/bubble/external окна и известные story/photo/payment/translate clear paths. Имена в уведомлениях могут оставаться; FCM/lock-screen/physical screen-sharing matrix впереди |
| Перевод/расшифровка | P1 | ограничено сервером | Штатные API/Premium-условия; проверка аккаунта не проведена |
| Export/import profiles/schema/preview | P1 | реализуется | SAF export/import переносимых MOROK appearance/round-video/account privacy флагов и отдельные именованные локальные профили со строгими схемами и preview реализованы; chat/account IDs, secrets, proxy, archive/Memory исключены. Темы Telegram и отдельный зашифрованный перенос архива впереди |
| Камера и качество кружочков | P1 | реализуется | Experimental OFF-by-default профили, Camera2 AF/EIS/FPS, codec fallback и opt-in bounded metadata diagnostics реализованы; физические A/B и recipient-тесты не проведены, см. `ROUND_VIDEO_AUDIT.md` |
| Плагины/SDK/safe mode, OCR/ASR, перенос данных | P2 | требование | Отдельные будущие этапы; основной клиент не зависит от плагинов |
| Чужие keys, fake Premium, снятие secret/TTL/noforwards | — | вне согласованного объёма | Явно исключены утверждённым ТЗ |

P0 не завершён. Первая сборка и частичная Память не означают полного паритета с референсами.
