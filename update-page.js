const fs = require('fs');

let page = fs.readFileSync('website/src/app/promembers/page.tsx', 'utf8');

// Add Suspense import
page = page.replace(
  "import { Crown, Database, Heart } from 'lucide-react';",
  "import { Crown, Database, Heart } from 'lucide-react';\nimport { Suspense } from 'react';"
);

// Wrap ProMembersClient in Suspense
page = page.replace(
  "<ProMembersClient\n        initialMembers={members}\n      />",
  "<Suspense fallback={<div>Loading members...</div>}>\n        <ProMembersClient\n          initialMembers={members}\n        />\n      </Suspense>"
);

fs.writeFileSync('website/src/app/promembers/page.tsx', page);
