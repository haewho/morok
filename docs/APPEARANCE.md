# MOROK appearance

The device preferences are local to `morok_device`, current schema 16. `AppearanceSettings` is immutable and typed. An older file receives additive defaults; a save writes the two effect keys, dialog-list density, avatar size and schema in one SharedPreferences editor operation. Reads do not rewrite preferences. Reset only changes those four keys. A newer schema rejects writes. SharedPreferences `apply()` provides normal Android asynchronous durability, not a synchronous crash-proof transaction; no stronger guarantee is claimed.

`SettingsRepository.accountNamespace(long)` validates a positive authenticated Telegram user ID and produces `morok_account_user_<id>`. `MorokSettings.forAccount(slot)` resolves and validates the authorized user before delegating to `forUser(id)`; appearance is deliberately device-wide. Account slots must never be passed as identities. No auth data, proxy credentials, or Telegram settings are exported or changed by this repository. The transfer format and preview are documented in `SETTINGS_TRANSFER.md`; reviewed whole-app profiles are documented in `APP_PROFILES.md`.

Native entry: Telegram Settings → MOROK Settings. English and Russian strings live in separate MOROK resource files. The native screen has text search, named appearance modes, reviewed app profiles, Liquid Glass, reduced effects, compact/standard/comfortable dialog-list density, small/standard/large list avatars, Telegram themes and power-saving shortcuts, category reset, Memory and connection settings links. The separate transfer screen exports/imports reviewed MOROK appearance, round-video and global privacy flags without account/chat/proxy/archive/Memory data. Theme/AMOLED customization beyond Telegram’s existing theme UI remains pending.

## Named modes and preview

The mode row is a deterministic view over the two independently editable settings; it does not add another stored flag that could drift from them. `As in Telegram` means glass allowed and MOROK animation reduction off. `No transparency` means solid covered panels with the normal animation policy. `Minimum effects` means solid covered panels plus the documented effective LiteMode reduction. Manually enabling both glass and reduced effects is shown as `Custom`.

Selecting a named mode opens a second confirmation dialog that lists its exact glass and animation values and states which Telegram settings remain unchanged. No setting is persisted from the selector itself. Apply uses the existing in-place renderer refresh, while Cancel leaves the current state intact. Modes do not change Telegram themes, power-saving preferences, media autoplay, navigation transitions, account settings or server state.

Dialog-list density is independent of named effect modes and whole-app behavior presets. Compact subtracts 8 dp from upstream row heights, Standard preserves them, and Comfortable adds 8 dp. The hook is limited to ordinary `DialogCell` rows in the main list and two user folders, including their existing forum/archive variants; selection/search/share lists retain upstream geometry. Text, avatars, previews, ordering and search identity are unchanged. A density change requests layout on the existing activity tree instead of recreating fragments, preserving drafts and navigation state; exact scroll anchoring and accessibility still require device testing.

Dialog-list avatar size is independently Small (−4 dp), Standard (upstream), or Large (+4 dp), bounded to 44–60 dp. Each avatar remains centered on the upstream rectangle. Story rings, archive animation, unread/status overlays and community cards consume the same adjusted bounds; text columns and media thumbnails stay unchanged. Compact+Large gives back half of the positive avatar delta (the ordinary 70 dp row becomes 64 rather than 62 dp) so the centered bounds are not clipped. It has the same Default/Folder1/Folder2 scope and live relayout as density. Exact story/community/RTL rendering still requires an authenticated device matrix.

## Rendering policy

Liquid Glass ON permits the upstream renderer. It does not override hardware limits, battery saver or Telegram’s effect flags. Native refraction is a real Android 13+ RuntimeShader in `blur3/LiquidGlassEffect`; it is disabled in this upstream’s default LiteMode presets. The settings screen explains when native refraction is unavailable/disabled. Existing upstream constructor-cached refraction capability is intentionally retained during MOROK OFF/ON so enabling again does not require reconstructing chats.

Liquid Glass OFF short-circuits both blur3 drawable implementations before traversing a bitmap/source or hardware display list, painting a solid themed surface instead. Fade wrappers also short-circuit their otherwise independent bitmap/gradient fast paths. Widget transition alpha is preserved: opacity refers to the surface fill, not elimination of all fade transitions. A transparent photo-viewer provider has an explicit dark fallback; remaining panels use their provider tint or source/theme color.

Off also suppresses blur3 RenderNode source updates, visible capture positions, downscaled capture and downstream GPU draw calls. LiteMode’s effective CHAT_BLUR flag is cleared so consumers using `SharedConfig.chatBlurEnabled()` stop the legacy chat blur path. Stored Telegram LiteMode preferences are not rewritten. Existing drawables and the activity view tree are invalidated in place; fragments are not rebuilt, so this implementation does not deliberately reset chat drafts or scroll positions.

### Source coverage map

| Surface / callers | Common boundary | Implemented scope |
| --- | --- | --- |
| Main tabs, dialogs top panels, chat top/bottom panels, chat input | blur3 drawables + RenderNode source + downscale capture | Solid drawing and stopped common capture/draw paths |
| Attachment menus, share sheet, emoji controls | Same blur3 factories and common renderer | Solid common panels; caller-specific extra rendering still requires device profiling |
| Message popup menus, scrim options, preview menus | Bitmap-backed blur3 drawable | Solid final panels; independently created scrim bitmaps can still be generated upstream |
| Photo viewer new glass controls | RenderNode/source drawables, explicit dark provider | Solid controls; photo viewer’s independent media processing remains active |
| Fade strips | BlurredBackgroundWithFadeDrawable | Bypasses bitmap/gradient source sampling when off |
| Classic chat blur | LiteMode → SharedConfig.chatBlurEnabled → SizeNotifierFrameLayout | Flag-governed blur suppressed; a previously queued bitmap job is not cancelled |
| Other older backdrop effects, calls, story editor, premium decorative effects | Independent legacy producers | No blanket coverage claim; pending inventory, hooks and device measurements |

Reduced effects clears only the effective LiteMode bits for animated stickers, emoji, chat backgrounds, scale/Thanos effects, calls and particles. It leaves stored masks and media autoplay choices intact. Animated emoji and wallpaper consumers are explicitly refreshed. This is not a universal reduced-motion implementation: navigation transitions, independently cached effects, system reduced-motion integration and TalkBack/large-font checks remain device verification work.

## Upstream hook contract

All UI hooks execute on the UI/drawing thread. `LiteMode.isEnabled` is read-only and may be queried from other threads; preferences are cached in a volatile immutable snapshot. Drawable weak-reference registration/snapshots are synchronized.

| File and symbol | Event/order | Invariant | Verification |
| --- | --- | --- | --- |
| `org/telegram/ui/ProfileActivity.java`: `updateRowsIds`, row click handler, `ListAdapter` bind/enable/type, row diff map, settings search builder | Insert own settings row and search result only into own-profile settings; route with the current account | Existing profile row numbering and own-user checks remain intact | Source inspection; Android compilation/device navigation pending |
| `org/telegram/messenger/LiteMode.java`: `isEnabled(int)` | Apply MOROK effective mask after upstream battery policy and before result | Do not mutate native presets or persistent flags; retain tablet forum behavior | Source inspection; runtime animation regression pending |
| `org/telegram/ui/Cells/DialogCell`: `computeHeight` / `getCollapsedHeight`; avatar rectangle construction/community draw | After upstream chooses the row and avatar variants, before dp conversion/draw | Only Default/Folder1/Folder2 lists receive bounded height −8/0/+8 dp and centered avatar −4/0/+4 dp; content, sort/search and non-list pickers remain upstream | JVM/source tests; density/avatar list/folder/forum/archive/story/community/RTL/large-font device matrix |
| `blur3/drawable/BlurredBackgroundDrawable`: constructor, `drawSource`, `drawMorokOpaqueSurface` | Register weakly; branch before source draw | Solid fill with preserved widget alpha; no sampled source or bitmap shader in off path | Source inspection; device GPU/render tests pending |
| `blur3/drawable/BlurredBackgroundDrawableRenderNode`: `draw` | Opaque branch before display-list generation/hardware draw | No glass shader or source draw in off path | Source inspection; device GPU/render tests pending |
| `blur3/BlurredBackgroundWithFadeDrawable`: `draw` | Branch before bitmap/color fast paths | Wrapper cannot bypass opaque policy; wrapper alpha preserved | Source inspection; device fade/scroll tests pending |
| `blur3/source/BlurredBackgroundSourceRenderNode`: `updateDisplayListIfNeeded`, `needUpdateDisplayList`, `draw`, `getVisiblePositions` | Off returns before update/capture visibility/source rendering | Existing source retained for re-enable; off exposes zero required capture positions | Source inspection; device off/on tests pending |
| `blur3/DownscaleScrollableNoiseSuppressor`: `draw`, `drawInline`, `invalidateResultRenderNodes`, `setupRenderNodes` | Off stops capture/GPU draw and sets active source rectangle count to zero | No hidden common downscale capture while solid panels shown; allocated resources reused | Source inspection; device profiling pending |
| `blur3/drawable/color/impl/BlurredBackgroundProviderImpl`: `photoViewer` | Resolve solid dark fill only under MOROK off | White viewer controls retain a dark surface | Source inspection; light/dark device screenshot tests pending |

Portable validation: `JAVA_HOME=<JDK> tests/settings/run.sh` compiles production repository classes with Java 8 compatibility and checks mode mapping/custom detection/invalid selection, density/avatar bounds/default/migration/profile preservation, defaults, additive migration, restart, category reset preserving unrelated settings, stable 64-bit account identity isolation, invalid IDs and downgrade refusal. Source checks pin the three dialog-list types, centered avatar rectangles, community sizing, live relayout and transfer preview. They do not establish Android rendering correctness or GPU savings. APK compilation, native runtime, TalkBack, large text, geometry changes during scroll, story/community/RTL rendering, old API fallback, off/on without draft/scroll loss and whole-surface coverage must be reported separately.
