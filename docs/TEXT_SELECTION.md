# Частичное выделение и вставка

Telegram 12.10.1 уже содержит собственный `TextSelectionHelper`: долгий тап по поддержанному тексту запускает выделение слова, draggable handles меняют границы, а системная floating toolbar предоставляет Copy, Select All, Quote и доступный Translate. MOROK сохраняет этот механизм и его визуальное поведение.

В обычном writable non-secret чате MOROK добавляет в ту же toolbar действие «Вставить в редактор». Оно появляется только тогда, когда штатный `canCopy()` разрешает работу с выбранным сообщением. Поэтому `noforwards` на peer или отдельном message продолжает скрывать и Copy, и MOROK-вставку. Secret, Saved Messages, Replies/Anonymous, read-only и служебные режимы дополнительно исключены MOROK-политикой.

Выбранный `CharSequence` передаётся прямо в `ChatActivity`, без Android clipboard и промежуточного файла. Текст вставляется в текущую позицию курсора через `ChatActivityEnterView.replaceWithText(cursor, 0, …)`. Существующий черновик и выделенные символы редактора не удаляются; пробелы добавляются только для предотвращения склейки слов. Текущий Telegram message-length limit проверяется до изменения поля.

Действие не вызывает send button, `SendMessagesHelper`, Telegram storage или protocol layer. После вставки пользователь может отредактировать результат и отдельно нажимает Send. Formatting spans выбранного сообщения в первой версии преобразуются в обычный текст; создание entities редактора отдельно не заявляется.

Полная приёмка требует авторизованной проверки handles и toolbar на plain text, caption, grouped media, emoji, RTL и длинном сообщении, а также отрицательной матрицы secret/TTL/noforwards/read-only.
