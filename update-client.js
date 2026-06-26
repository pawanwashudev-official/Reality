const fs = require('fs');

let client = fs.readFileSync('website/src/app/promembers/ProMembersClient.tsx', 'utf8');

// Update imports
client = client.replace(
  "import React, { useState, useMemo, useEffect } from 'react';",
  "import React, { useState, useMemo, useEffect, useRef } from 'react';"
);

// Fix the useEffect resetting to page 1 on mount
const searchSortResetLogic = `
  // Reset to page 1 when search or sort changes
  useEffect(() => {
    setCurrentPage(1);
    const params = new URLSearchParams(window.location.search);
    params.set('page', '1');
    router.replace(\`?\${params.toString()}\`);
  }, [searchQuery, sortOrder, router]);
`;

const fixedSearchSortResetLogic = `
  const isMounted = useRef(false);
  // Reset to page 1 when search or sort changes
  useEffect(() => {
    if (!isMounted.current) {
      isMounted.current = true;
      return;
    }
    setCurrentPage(1);
    const params = new URLSearchParams(window.location.search);
    params.set('page', '1');
    router.replace(\`?\${params.toString()}\`);
  }, [searchQuery, sortOrder, router]);
`;

client = client.replace(searchSortResetLogic.trim(), fixedSearchSortResetLogic.trim());

fs.writeFileSync('website/src/app/promembers/ProMembersClient.tsx', client);
