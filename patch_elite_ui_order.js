const fs = require('fs');
const file = './app/src/main/res/layout/activity_reality_elite.xml';
let content = fs.readFileSync(file, 'utf8');

// The new requirement is:
// 1. "we have trial plan also rewrite them as experience our Elite member features for 3 days without any stake this trial plan should be below paid plan card"
// 2. "page should be super smooth and when user sign in and app captures token then give log as successfully signed in and super smooth ui and each click fast and responsive"
// 3. "Card 1: Sign In (Mandatory)... In paid plan card Step 1 (Register)... Step 2... Step 3..."
// We need to move the trial card below the paid plan cards.

// Find the trial card block
const trialStart = content.indexOf('<com.google.android.material.card.MaterialCardView\n                android:id="@+id/card_trial_plan_pro"');
let trialEnd = -1;
if (trialStart !== -1) {
    let count = 1;
    let currentIndex = trialStart + 10;
    while(count > 0 && currentIndex < content.length) {
        let open = content.indexOf('<com.google.android.material.card.MaterialCardView', currentIndex);
        let close = content.indexOf('</com.google.android.material.card.MaterialCardView>', currentIndex);
        if (close === -1) break;
        if (open !== -1 && open < close) {
            count++; currentIndex = open + 1;
        } else {
            count--; currentIndex = close + '</com.google.android.material.card.MaterialCardView>'.length;
        }
    }
    if (count === 0) trialEnd = currentIndex;
}

if (trialStart !== -1 && trialEnd !== -1) {
    const trialBlock = content.substring(trialStart, trialEnd);
    content = content.substring(0, trialStart) + content.substring(trialEnd);

    // Insert it before btn_cancel
    const insertPoint = content.indexOf('<com.google.android.material.button.MaterialButton\n                android:id="@+id/btn_cancel"');
    if (insertPoint !== -1) {
        content = content.substring(0, insertPoint) + trialBlock + '\n\n' + content.substring(insertPoint);
    }
}

// Update text in trial card
content = content.replace('Start 3-Day Trial', 'Experience Elite features for 3 days without any stake');
content = content.replace('3-Day Trial', 'Experience our Elite member features for 3 days without any stake');

fs.writeFileSync(file, content);
