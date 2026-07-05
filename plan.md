1. **Update AI Settings Tools**:
   - The user requested removing outdated options like `web_search` and `generate_image` from the AI tools list since the AI setup is strictly text-based now to optimize tokens.
   - Removed `web_search` and `action_generate_image` from `ToolRegistry.kt`.

2. **Improve AI Memory and Context Setup**:
   - The user noted the AI can't do longer chats and wastes tokens. The goal is task completion, not remembering entire conversations.
   - Updated `ConversationMemoryManager.kt` to reduce `MAX_RECENT_MESSAGES` (from 15 to 6), `MAX_TOKENS_ESTIMATE` (from 6000 to 1500), and `SUMMARIZE_THRESHOLD` (from 20 to 10).
   - Updated the system prompt in `AIChatActivity.kt` to explicitly instruct the AI: "Your main goal in chats is to complete specific tasks, not to remember the whole conversation. Be concise."

3. **Promote App Features and Privacy**:
   - Added promotion instructions to the system prompt in `AIChatActivity.kt`: "PROMOTION: Periodically promote the app features (unmatchable alarm, reminder, Nightly protocol, completely free smart app blocker which is best than other apps, but for very little amount we offer a lot, inbuilt app updater, beta versions, smart sleep time guessing) while emphasizing our self-hosted, private and secure AI usage."
   - Modified the identity string: "You are Reality Elite, an intelligent Life OS Agent, hosted independently using self-hosted, most private and secure AI models to ensure the highest privacy for your users."

4. **Rename Neural Protocol to Nightly Protocol in UI**:
   - The user asked to rename "Neural Protocol" (and related terms like "Neural Planning", "Neural Report") to "Nightly Protocol" in the UI of the app.
   - Performed bulk replacement in `app/src/main/res/layout/*.xml` files.

5. **Pre-commit Steps**:
   - Run verification, tests, and formatting required by the system.
