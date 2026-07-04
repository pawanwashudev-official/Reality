const fs = require('fs');
const file = './app/src/main/java/com/neubofy/reality/ui/activity/RealityEliteActivity.kt';
let content = fs.readFileSync(file, 'utf8');

// The instruction requires: "if tried without signin then properly scroll page and highlisht sign in button to sign in first"
// We need to add this logic to Step 1, Step 2, and Step 3 buttons in RealityEliteActivity.kt.

const smoothScrollLogic = `
    private fun requireSignInAndScroll(): Boolean {
        if (!isSignedIn || userId == null) {
            Toast.makeText(this, "Please sign in first to continue.", Toast.LENGTH_SHORT).show()
            findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll_view)?.smoothScrollTo(0, 0)
            btnUnifiedSignin.requestFocus()
            return false
        }
        return true
    }
`;

content = content.replace('class RealityEliteActivity : BaseActivity() {', 'class RealityEliteActivity : BaseActivity() {\n' + smoothScrollLogic);

// Add scroll view ID to the XML if it doesn't exist, we will do it below, wait, RealityEliteActivity uses ScrollView not NestedScrollView, let's just use ScrollView.
content = content.replace('findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll_view)', 'findViewById<android.widget.ScrollView>(R.id.scroll_view)');

content = content.replace(
    'btnRegister.setOnClickListener {\n            registerEliteMember()\n        }',
    'btnRegister.setOnClickListener {\n            if(requireSignInAndScroll()) registerEliteMember()\n        }'
);

content = content.replace(
    'btnPayUpi.setOnClickListener {\n            showUpiPaymentDialog()\n        }',
    'btnPayUpi.setOnClickListener {\n            if(requireSignInAndScroll()) showUpiPaymentDialog()\n        }'
);

content = content.replace(
    'btnVerify.setOnClickListener {\n            showVerifyDialog()\n        }',
    'btnVerify.setOnClickListener {\n            if(requireSignInAndScroll()) showVerifyDialog()\n        }'
);


fs.writeFileSync(file, content);
