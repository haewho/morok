#!/usr/bin/env python3
"""Guard the pre-storage ordering of MOROK Memory hooks in upstream catch-up paths."""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
source = (root / "TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java").read_text()
checks = 0


def expect(value, message):
    global checks
    checks += 1
    if not value:
        raise AssertionError(message)


channel_start = source.index("protected void getChannelDifference(long channelId, int newDialogType")
difference_start = source.index("public void getDifference()", channel_start)
channel = source[channel_start:difference_start]
channel_new_hook = channel.index("MemoryObserver.beforeTelegramStorageNew(currentAccount, messages)")
channel_new_write = channel.index("putMessages(res.new_messages", channel_new_hook)
expect(channel_new_hook < channel_new_write, "Channel difference new messages must be journaled before putMessages")

channel_reset_hook = channel.index("MemoryObserver.beforeTelegramStorageRawNew(currentAccount, res.messages)")
channel_overwrite = channel.index("overwriteChannel(channelId", channel_reset_hook)
expect(channel_reset_hook < channel_overwrite, "Too-long channel snapshots must be journaled before overwriteChannel")

updates_start = source.index("public void processUpdates(", difference_start)
difference = source[difference_start:updates_start]
difference_hook = difference.index("MemoryObserver.beforeTelegramStorageNew(currentAccount, messages)")
difference_write = difference.index("putMessages(res.new_messages", difference_hook)
expect(difference_hook < difference_write, "Global difference new messages must be journaled before putMessages")

array_start = source.index("public boolean processUpdateArray(", updates_start)
array_end = source.index("public void checkUnreadReactions", array_start)
update_array = source[array_start:array_end]
live_hook = update_array.index("MemoryObserver.beforeTelegramStorageNew(currentAccount, messages)")
live_write = update_array.index("putMessages(messagesArr", live_hook)
expect(live_hook < live_write, "Live new messages must be journaled before putMessages")

observer = (root / "TMessagesProj/src/main/java/org/morok/history/MemoryObserver.java").read_text()
raw_start = observer.index("public static void beforeTelegramStorageRawNew")
raw_end = observer.index("private static void afterTelegramStorageNew", raw_start)
raw = observer[raw_start:raw_end]
expect("TL_messageEmpty" in raw, "Raw difference conversion must exclude empty messages")
expect("beforeTelegramStorageNew(account, messages)" in raw, "Raw difference conversion must reuse normal eligibility")

store = (root / "TMessagesProj/src/main/java/org/morok/memory/MorokMemoryStore.java").read_text()
persist = store.index("Future<Boolean> append = JOURNAL_QUEUE.submit")
pending = store.index("tracking.addPending(pending)", persist)
replay = store.index("scheduleJournalReplay()", pending)
expect(persist < pending < replay, "Pending identities must become visible after journal commit and before replay")

retry_start = store.index("public void retryAttachment(")
retry_end = store.index("public void update(", retry_start)
retry = store[retry_start:retry_end]
expect("MemoryCapture.restoreCachedMessage" in retry and "copyAttachment(message, target, database, false)" in retry,
       "Attachment retry must use the authenticated stored snapshot and existing store transaction")
expect("loadFile(" not in retry and "download" not in retry.lower(),
       "Attachment retry must remain cache-only and never start a download")

expect("enforceAutomaticPolicy(database, archiveSettings())" in store,
       "Every committed Memory index must enforce the selected automatic retention and storage policy")
expect("policy.allowsAutomaticAttachment(isUnmeteredNetwork())" in store,
       "Automatic original retention must consult the selected network policy")
expect("MemoryPolicy.canCopy(size, estimatedUsedBytes(database)" in store,
       "Automatic originals must use projected referenced bytes rather than orphaned cache files")
expect("cleanAutomaticArchive" in store and "beforeCards - automaticCount(database)" in store,
       "The explicit cleanup action must report only removed automatic cards")

export_start = store.index("public void exportSelected(")
export_end = store.index("private long usedBytes()", export_start)
memory_export = store[export_start:export_end]
expect('"content".equals(destination.getScheme())' in memory_export
       and "openOutputStream(destination" in memory_export and "ZipOutputStream" in memory_export,
       "Memory export must stream only to an explicit SAF content destination")
expect("FileOutputStream" not in memory_export and "readAttachmentBlob" in memory_export,
       "Memory export must not create a decrypted temporary file")
expect("MessageDigest.getInstance(\"SHA-256\")" in memory_export
       and "requireExportAllowed()" in memory_export,
       "Every exported original must pass integrity and active unlocked-session checks")
expect('put("tl"' not in memory_export and "serializedMessage" not in memory_export,
       "The readable export must omit internal serialized Telegram payloads")

activity = (root / "TMessagesProj/src/main/java/org/morok/ui/MorokMemoryActivity.java").read_text()
expect("setOnItemLongClickListener" in activity and "selected.add(card.id)" in activity,
       "Memory cards must support explicit multi-selection")
expect("Intent.ACTION_CREATE_DOCUMENT" in activity and 'setType("application/zip")' in activity
       and "MorokMemoryExportConfirm" in activity,
       "Memory export must disclose plaintext and let Android choose the destination")

filter_start = activity.index("private void applyFilter()")
filter_end = activity.index("private void chooseTagFilter()", filter_start)
memory_filter = activity[filter_start:filter_end]
expect("MemoryFilterPolicy.hasTag(card.tags, tag)" in memory_filter
       and "card.key.dialogId() == dialogId" in memory_filter,
       "Memory tag and chat facets must be exact account-local card filters")
expect("Utilities.searchQueue.postRunnable" in memory_filter
       and "MessagesController" not in memory_filter and "MessagesStorage" not in memory_filter,
       "Memory facets must stay on the local search queue without Telegram reads or network paths")

context_start = activity.index("private ArrayList<MemoryCard> savedContext(")
context_end = activity.index("private void chooseReminder(", context_start)
saved_context = activity[context_start:context_end]
expect("MemoryFilterPolicy.sameContext(" in saved_context
       and "MemoryFilterPolicy.MAX_CONTEXT_CARDS" in saved_context
       and "MemoryFilterPolicy.messageDistance" in saved_context,
       "Saved context must be bounded to nearest cards from the same dialog and topic")
expect("openSource(" not in saved_context and "MessagesController" not in saved_context
       and "MessagesStorage" not in saved_context and "FileLoader" not in saved_context,
       "Viewing saved context must not open Telegram, read storage or start a download")

print(f"Memory hook order: {checks} checks passed")
