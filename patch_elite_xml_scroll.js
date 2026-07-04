const fs = require('fs');
const file = './app/src/main/res/layout/activity_reality_elite.xml';
let content = fs.readFileSync(file, 'utf8');

if (!content.includes('android:id="@+id/scroll_view"')) {
    content = content.replace('<ScrollView\n        android:layout_width="match_parent"', '<ScrollView\n        android:id="@+id/scroll_view"\n        android:layout_width="match_parent"');
}

fs.writeFileSync(file, content);
