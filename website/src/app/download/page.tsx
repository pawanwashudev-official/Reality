'use client';

import React, { useState, useEffect } from 'react';
import { Download, Package, FileCode } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import rehypeRaw from 'rehype-raw';

export default function DownloadPage() {
  const [releaseData, setReleaseData] = useState<any>(null);
  const [bestApkUrl, setBestApkUrl] = useState<string>('');

  useEffect(() => {
    fetch('https://api.github.com/repos/pawanwashudev-official/Reality/releases')
      .then((res) => res.json())
      .then((releases) => {
         if (releases && releases.length > 0) {
             // Find latest non-prerelease or first release if none
             const stable = releases.find((r: any) => !r.prerelease) || releases[0];
             setReleaseData(stable);

             // Find max timestamp APK
             let maxTs = 0;
             let apkUrl = '';
             const regex = /.*-(\d{13,})-.*\.apk/;

             stable.assets.forEach((asset: any) => {
                 if (asset.name.endsWith('.apk')) {
                     const match = asset.name.match(regex);
                     if (match && match[1]) {
                         const ts = parseInt(match[1], 10);
                         if (ts > maxTs) {
                             maxTs = ts;
                             apkUrl = asset.browser_download_url;
                         }
                     } else if (!apkUrl) {
                         apkUrl = asset.browser_download_url;
                     }
                 }
             });
             setBestApkUrl(apkUrl);
         }
      })
      .catch(console.error);
  }, []);

  return (
    <div className="min-h-screen py-16 bg-neural-bg">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="bg-neural-bg border border-gray-700 rounded-2xl w-full flex flex-col relative shadow-2xl">
          <div className="flex justify-between items-center p-6 border-b border-gray-800 shrink-0 bg-neural-card/50">
            <h2 className="text-2xl font-bold flex items-center gap-2 text-white">
              <Package className="text-neural-cyan" /> Latest Release: {releaseData ? releaseData.tag_name : 'Loading...'}
            </h2>
          </div>
          <div className="p-6 overflow-y-auto max-w-none flex flex-col gap-6">
            {!releaseData ? (
              <div className="flex justify-center py-20 text-neural-cyan animate-pulse">Fetching latest release info...</div>
            ) : (
              <>
                {bestApkUrl && (
                  <div className="bg-neural-card border border-neural-cyan/30 p-6 rounded-xl text-center">
                      <h3 className="text-xl font-bold text-white mb-2">Ready to Install</h3>
                      <p className="text-gray-400 mb-6">This APK has been intelligently selected as the latest stable build.</p>
                      <a href={bestApkUrl} download className="inline-flex items-center gap-2 bg-neural-cyan text-black px-8 py-4 rounded-xl font-bold text-lg hover:bg-cyan-400 transition transform hover:scale-105">
                          <Download /> Download APK Now
                      </a>
                  </div>
                )}

                <div className="bg-black/40 border border-gray-800 p-6 rounded-xl">
                    <h3 className="text-lg font-bold text-white mb-4 border-b border-gray-800 pb-2">Release Notes</h3>
                    <div className="prose prose-invert prose-neural max-w-none">
                        <ReactMarkdown
                            remarkPlugins={[remarkGfm, remarkBreaks]}
                            rehypePlugins={[rehypeRaw]}
                            components={{
                              img: ({node, ...props}) => <img style={{maxWidth: '100%', height: 'auto', borderRadius: '0.5rem'}} {...props} />
                            }}
                          >{releaseData.body || 'No release notes provided.'}</ReactMarkdown>
                    </div>
                </div>

                <div className="bg-black/40 border border-gray-800 p-6 rounded-xl">
                    <h3 className="text-lg font-bold text-white mb-4 border-b border-gray-800 pb-2">All Release Assets</h3>
                    <div className="grid gap-3">
                        {releaseData.assets.map((asset: any) => (
                            <a key={asset.id} href={asset.browser_download_url} className="flex items-center justify-between p-3 rounded-lg bg-neural-bg hover:bg-neural-card border border-gray-800 transition">
                                <div className="flex items-center gap-3">
                                    <FileCode className="text-gray-400" size={20} />
                                    <span className="font-mono text-sm text-gray-300 break-all">{asset.name}</span>
                                </div>
                                <span className="text-xs text-gray-500 whitespace-nowrap bg-black px-2 py-1 rounded">
                                    {(asset.size / 1024 / 1024).toFixed(2)} MB
                                </span>
                            </a>
                        ))}
                    </div>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
