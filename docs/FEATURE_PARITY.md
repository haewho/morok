# Матрица функций

Проверка источников: 2026-09-06. База MOROK: Telegram12.10.1/7038; закреплён release-коммит. Текущий официальный APK и полное соответствие всех новых серверных функций исходникам отдельно не проверены. [Release notes25.08.2026](https://telegram.org/blog/welcome-messages-buttons-TG-13).

Референсы: [exteraGram features](https://exteragram.app/), [публичные исходники](https://github.com/exteraSquad/exteraGram), [AyuGram Android](https://docs.ayugram.one/android/), [Ghost Mode](https://docs.ayugram.one/shared/ghost/). Номер фактически распространяемого APK и его соответствие публичному коду не установлены. Код этих клиентов не переносился. «XTelegram»/«Viogram» не отождествляются с ними без подтверждения.

«Реализуется» означает частичный код, а не готовность беты. Реальные сборки/проверки отдельно в `PROGRESS.md`.

| Требование | Приоритет | Статус | Фактический объём / остаток |
|---|---|---|---|
| Текст, voice/video, файлы, реакции, группы/темы, камера, аккаунты | P0 | реализуется | Официальный клиент сохранён; вход/регрессия требуют API-данных и устройства |
| Звонки audio/video/group | P0 | требование | Стек сохранён, транспортные проверки не проведены |
| Бренд, identity, providers, контакты | P0 | реализуется | MOROK/Морок, Личина, отдельный пакет; требуется соседняя установка |
| Liquid Glass on/off, минимум эффектов | P0 | реализуется | Общие blur3 capture/draw paths; старые самостоятельные scrim paths охвачены не полностью |
| Светлая/тёмная/AMOLED, семантические overrides, dynamic colors | P0 | реализуется | Доступ к штатным темам; полная карта override/контраста впереди |
| Схема настроек и стабильный ID аккаунта | P0 | реализовано | Отдельный слой с JVM-тестами; UI использует настройки устройства |
| Автоматический архив new/edit/delete/difference | P0 | реализуется | Для явно созданных карточек: ранние live/getDifference edit/delete/history hooks + UI fallback; общий архив и устойчивый входной WAL ещё не готовы |
| Действительные файлы вне обычного кеша | P0 | реализуется | Для явных карточек; общий архив и reference counting впереди |
| Шифрование, logout, replay/tombstones, лимиты, поиск | P0 | реализуется | Слой Памяти; device/crash/key-loss проверки впереди |
| Ghost preset, online/read/content-read/typing, исключения, local cursor | P0 | реализуется | Account-local Ghost preset; semantic suppression typing/recording/upload/sticker/emoji, ordinary foreground online, ordinary server read с локальным cursor и content-read входящих voice/round video; явный mark-read синхронизирует; read-on-reply/истории/исключения впереди; API terms conflict сохраняется |
| Stories, mark-read, read-on-reply, scheduled ghost send | P0 | требование | Нужны semantic hooks и второй аккаунт; серверные ограничения сохраняются |
| Прокси до входа, импорт, проверки и ротация | P0 | реализуется | Штатный транспорт; целевая сеть не проверена |
| Подписанный bootstrap, зеркала, antirollback и 2 независимых узла | P0 | заблокировано зависимостью | Основы/шаблоны; нет серверов, endpoints и ключей владельца |
| Память: карточка/цитата/файл/заметка/теги/поиск | P0 | реализуется | Native экран и encrypted private store |
| Память: reminder/cancel/restart/правильный контекст | P0 | реализуется | Local alarms/stable-user routing; Doze/permission/device проверки впереди |
| Подпись и данные при update APK | P0 | заблокировано зависимостью | Нет release key; двухверсийный тест ещё нужен |
| Upstream lock, upgrade worktree, CI | P0 | реализуется | Скрипты подготовлены, перенос разных upstream-версий ещё не проверен |
| Подписанный APK-updater | P0 | требование | Upstream APK-updater отключён |
| Навигация: tabs/folders/order/start screen | P1 | требование | Штатная навигация сохранена |
| Плотность/аватары/пузыри/шрифты/timestamps/анимации | P1 | требование | Полный собственный набор впереди |
| Жесты/long press/быстрые действия | P1 | реализуется | «Запомнить»; редактор жестов/профилей впереди |
| Copy selection/templates/drafts/chat aliases/notes | P1 | требование | Штатные сценарии сохранены; локальные дополнения впереди |
| Call/video confirm/double tap/local undo send | P1 | требование | Фиктивной отмены после отправки нет |
| Плеер/позиция/скорость/sleep timer/autoplay | P1 | требование | Штатный плеер сохранён |
| Downloads queue/pause/priority/traffic limits | P1 | требование | Штатный stack; ускорение не заявляется |
| Local filters/spoilers/noise | P1 | требование | Sponsored messages и серверная синхронизация сохраняются |
| Notifications/lock/screen sharing/privacy profiles | P1 | реализуется | Reminders без цитат на lock screen; общий профиль впереди |
| Перевод/расшифровка | P1 | ограничено сервером | Штатные API/Premium-условия; проверка аккаунта не проведена |
| Export/import profiles/schema/preview | P1 | требование | Пользовательский перенос не реализован |
| Плагины/SDK/safe mode, камера, OCR/ASR, перенос данных | P2 | требование | Отдельные будущие этапы; основной клиент не зависит от плагинов |
| Чужие keys, fake Premium, снятие secret/TTL/noforwards | — | вне согласованного объёма | Явно исключены утверждённым ТЗ |

P0 не завершён. Первая сборка и частичная Память не означают полного паритета с референсами.
