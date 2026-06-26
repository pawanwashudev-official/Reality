// simple syntax check
require("child_process").execSync("cd website && npx tsc --noEmit", {stdio: 'inherit'});
