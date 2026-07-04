const fs = require('fs');
const file = './app/src/main/java/com/neubofy/reality/ui/activity/RealityEliteActivity.kt';
let content = fs.readFileSync(file, 'utf8');

// isSignedIn and userId are likely properties I need to calculate or use the local variables correctly.
const smoothScrollLogic = `
    private fun requireSignInAndScroll(): Boolean {
        val isSignedIn = GoogleAuthManager.isSignedIn(this)
        val userId = IdentityManager.getUserId(this)
        if (!isSignedIn || userId == "Unknown") {
            Toast.makeText(this, "Please sign in first to continue.", Toast.LENGTH_SHORT).show()
            findViewById<android.widget.ScrollView>(R.id.scroll_view)?.smoothScrollTo(0, 0)
            btnUnifiedSignin.requestFocus()
            return false
        }
        return true
    }
`;

content = content.replace(
    /private fun requireSignInAndScroll\(\): Boolean \{[\s\S]*?return true\n    \}/,
    smoothScrollLogic.trim()
);

fs.writeFileSync(file, content);
