# Шаблоны ответов

Шаблоны — account-local заготовки обычного текста. Управление находится в MOROK Settings → «Шаблоны ответов». В обычном доступном для отправки чате пункт `⋮` → «Шаблоны ответов» открывает режим выбора.

После выбора текст вставляется в текущую позицию курсора через штатный `ChatActivityEnterView.replaceWithText`. Существующий черновик и выделенный текст не удаляются; вокруг вставки добавляются пробелы только там, где иначе склеились бы слова. Если итог превысит текущий Telegram message-length limit, вставка отклоняется целиком. Поле получает обычный фокус, но send button, `SendMessagesHelper` и protocol layer не вызываются: пользователь может изменить результат и отдельно нажимает отправку.

## Хранение

- До 100 шаблонов на устойчивый Telegram user ID; название до 64 символов, текст до 4096 символов, открытая база до 512 КиБ.
- UUID, название, текст и timestamps сериализуются в памяти. На диск в `noBackupFilesDir/morok/reply-templates/<userId>/templates.tink` атомарно пишется только ciphertext.
- Используются Google Tink AEAD и отдельный Android Keystore key `morok.templates.v1.<stableUserId>`. AAD содержит версию, user ID и назначение базы.
- Existing ciphertext без доступного ключа не перезаписывается новым ключом. Ошибка остаётся видимой пользователю.
- Logout синхронно фиксирует durable revocation marker до очистки Telegram account slot, затем удаляет ключ и каталог. Все три MOROK private stores вызываются во вложенных `finally`, поэтому ошибка очистки одного пространства не пропускает revocation следующих.
- Шаблоны не входят в перенос настроек, Telegram database или Android backup. Редактор отключает view-state persistence, autofill и personalized keyboard learning на поддерживаемом API.

Пункт выбора исключён из secret, Saved Messages, Replies/Anonymous, read-only channel и служебных режимов. Первая версия хранит plain text без formatting entities и variables. Полная приёмка требует авторизованного private/group/channel/topic сценария, restart и logout/login другим пользователем в том же slot.
