'use client';

import React, { useState, useEffect } from 'react';
import { BookOpen } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import rehypeRaw from 'rehype-raw';

export default function ReadmePage() {
  const [readmeContent, setReadmeContent] = useState('');

  useEffect(() => {
    fetch('https://raw.githubusercontent.com/pawanwashudev-official/Reality/main/README.md')
      .then((res) => res.text())
      .then((text) => setReadmeContent(text))
      .catch(() => setReadmeContent('Failed to load README.'));
  }, []);

  return (
    <div className="min-h-screen py-16 bg-neural-bg">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="bg-neural-bg border border-gray-700 rounded-2xl w-full flex flex-col relative shadow-2xl">
          <div className="flex justify-between items-center p-6 border-b border-gray-800 shrink-0 bg-neural-card/50">
            <h2 className="text-2xl font-bold flex items-center gap-2 text-white">
              <BookOpen className="text-neural-cyan" /> Project Readme
            </h2>
          </div>
          <div className="p-6 overflow-y-auto prose prose-invert prose-neural max-w-none">
            {readmeContent ? (
              <ReactMarkdown
                remarkPlugins={[remarkGfm, remarkBreaks]}
                rehypePlugins={[rehypeRaw]}
                components={{
                  img: ({node, ...props}) => <img style={{maxWidth: '100%', height: 'auto', borderRadius: '0.5rem', marginTop: '1rem', marginBottom: '1rem'}} {...props} />
                }}
              >{readmeContent}</ReactMarkdown>
            ) : (
              <div className="flex justify-center py-20 text-neural-cyan animate-pulse">Loading...</div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
