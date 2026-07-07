const fs = require('fs');

let code = fs.readFileSync('app/src/main/java/com/neubofy/reality/data/nightly/NightlyPhaseData.kt', 'utf8');
code = code.split('content.replace(Regex("[*#]+"), "")').join('content.replace(Regex("\\\\*\\\\*|##"), "")');
fs.writeFileSync('app/src/main/java/com/neubofy/reality/data/nightly/NightlyPhaseData.kt', code);

let code2 = fs.readFileSync('app/src/main/java/com/neubofy/reality/data/nightly/NightlyPhasePlanning.kt', 'utf8');
code2 = code2.split('content.replace(Regex("[*#]+"), "")').join('content.replace(Regex("\\\\*\\\\*|##"), "")');
fs.writeFileSync('app/src/main/java/com/neubofy/reality/data/nightly/NightlyPhasePlanning.kt', code2);
console.log("Regex fixed based on review");
