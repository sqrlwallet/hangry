# Palette's UX Journal - Critical Learnings

## 2026-09-22 - Compose Chat Input Usability & IME Actions
**Learning:** Jetpack Compose `OutlinedTextField` defaults to `ImeAction.Default` without auto-capitalization, causing software keyboards to insert carriage returns or display inert Enter keys instead of dispatching messages. Additionally, text fields lack screen reader role identification unless explicitly annotated with semantics `contentDescription`.
**Action:** Always configure conversational text inputs with `KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send)`, connect `KeyboardActions(onSend = ...)`, provide a conditional 1-tap clear trailing icon with haptic feedback, and attach semantics `contentDescription` for TalkBack.
