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

print(f"Memory hook order: {checks} checks passed")
