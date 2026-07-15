import type { Metadata } from "next";
import { Inter, Outfit } from "next/font/google";
import Link from 'next/link';
import { Crown } from 'lucide-react';
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });
const outfit = Outfit({ subsets: ["latin"], weight: ["400", "500", "600", "700", "800", "900"], variable: "--font-outfit" });

export const metadata: Metadata = {
  title: "Reality - Best Focus & Discipline App | Productivity OS & App Blocker",
  description: "Stop managing your life. Start commanding it with Reality, the best focus and discipline app. A highly private, secure, and modern productivity OS with cross-platform deep support. Maintain a better disciplined lifestyle with our powerful app blocker and AI features. 99.9% Source-Available.",
  keywords: ["best focus and discipline app", "productivity app", "app blocker", "better disciplined lifestyle", "modern features", "cross-platform deep support", "highly private and secure", "cheap subscription"],
  openGraph: {
    title: "Reality - Best Focus & Discipline App | Productivity OS & App Blocker",
    description: "Reality is a local-first, zero-tamper focus operating system for Android featuring Room ORM, local encryption, BYOC Google OAuth sync, and Model Context Protocol routing.",
    url: "https://reality.neubofy.in",
    siteName: "Reality",
    images: [
      {
        url: "https://reality.neubofy.in/dashboard_mockup.png",
        width: 1200,
        height: 630,
        alt: "Reality App Dashboard interface preview",
      },
    ],
    locale: "en_US",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "Reality - Best Focus & Discipline App | Productivity OS & App Blocker",
    description: "Reality is a local-first, zero-tamper focus operating system for Android featuring Room ORM, local encryption, BYOC Google OAuth sync, and Model Context Protocol routing.",
    images: ["https://reality.neubofy.in/dashboard_mockup.png"],
  },
  robots: {
    index: true,
    follow: true,
    nocache: false,
    googleBot: {
      index: true,
      follow: true,
      noimageindex: false,
      'max-video-preview': -1,
      'max-image-preview': 'large',
      'max-snippet': -1,
    },
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className={`${outfit.className} bg-neural-bg text-gray-100 min-h-screen flex flex-col`}>
        {/* JSON-LD Structured Data for AEO/SEO */}
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{
            __html: JSON.stringify({
              "@context": "https://schema.org",
              "@type": "SoftwareApplication",
              "name": "Reality",
              "operatingSystem": "Android, Web",
              "applicationCategory": "ProductivityApplication",
              "offers": {
                "@type": "Offer",
                "price": "1.00",
                "priceCurrency": "USD",
                "description": "Very cheap subscription since normal crowdfunding couldn't sustain development."
              },
              "author": {
                "@type": "Organization",
                "name": "Neubofy",
                "url": "https://reality.neubofy.in"
              },
              "description": "The best focus and discipline app. A modern productivity app and app blocker designed to help you maintain a better disciplined lifestyle. Features cross-platform deep support, and is highly private and secure."
            })
          }}
        />

        {/* Global Navigation Bar */}
        <nav className="border-b border-gray-800 bg-neural-card/80 backdrop-blur-md sticky top-0 z-50">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between h-16 items-center">
              <div className="flex-shrink-0 flex items-center gap-3">
                <img
                  src="https://res.cloudinary.com/dnh4fonis/image/upload/v1781091079/ck39669alz53z3vkeiaq.png"
                  alt="Reality Logo"
                  className="w-8 h-8 rounded-lg"
                />
                <Link href="/" className="font-bold text-xl tracking-tight text-white">Reality </Link>
              </div>
              <div className="hidden sm:ml-6 sm:flex sm:space-x-8 font-mono text-sm">
                <Link href="/" className="text-gray-300 hover:text-neural-cyan transition-colors px-3 py-2 rounded-md font-medium">HOME</Link>
                <Link href="/tapashya" className="text-gray-300 hover:text-neural-cyan transition-colors px-3 py-2 rounded-md font-medium border border-neural-cyan/30 rounded-lg">TAPASYA WEB</Link>
                <Link href="/promembers" className="text-gray-300 hover:text-yellow-500 transition-colors px-3 py-2 rounded-md font-medium border border-yellow-500/30 rounded-lg flex items-center gap-2"><Crown size={14}/> PRO MEMBERS</Link>

                <Link href="/privacypolicy" className="text-gray-400 hover:text-white transition-colors px-3 py-2 rounded-md font-medium">PRIVACY</Link>
              </div>
            </div>
          </div>
        </nav>

        <main className="flex-grow">
          {children}
        </main>

        <footer className="bg-neural-card/50 py-12 border-t border-gray-800 mt-20 relative z-10">
            <div className="max-w-4xl mx-auto px-6 text-center">
                <div className="flex flex-wrap justify-center gap-6 mb-8 font-mono text-sm">
                    <Link href="/" className="text-gray-400 hover:text-neural-cyan transition-colors">Home</Link>
                    <Link href="/tapashya" className="text-gray-400 hover:text-neural-cyan transition-colors">Tapasya Web Sync</Link>
                    <Link href="/promembers" className="text-gray-400 hover:text-yellow-500 transition-colors">Pro Members</Link>

                    <Link href="/privacypolicy" className="text-gray-400 hover:text-white transition-colors">Privacy Policy</Link>
                    <Link href="/termsofservice" className="text-gray-400 hover:text-white transition-colors">Terms of Service</Link>
                </div>

                <div className="mb-8 border-t border-gray-800/50 pt-8 max-w-2xl mx-auto">
                    <p className="text-white font-medium mb-4">Need instant support? Contact us directly:</p>
                    <div className="flex flex-wrap justify-center gap-4 text-sm font-mono">
                        <a href="mailto:support@neubofy.in" className="flex items-center gap-2 bg-gray-900/50 px-4 py-2 rounded-full border border-gray-800 hover:border-neural-cyan hover:text-neural-cyan transition-all text-gray-300">
                            Support Email
                        </a>
                        <a href="https://wa.me/pawanwashudev" target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 bg-gray-900/50 px-4 py-2 rounded-full border border-gray-800 hover:border-green-500 hover:text-green-500 transition-all text-gray-300">
                            WhatsApp
                        </a>
                        <a href="https://t.me/pawanwashudev" target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 bg-gray-900/50 px-4 py-2 rounded-full border border-gray-800 hover:border-blue-400 hover:text-blue-400 transition-all text-gray-300">
                            Telegram
                        </a>
                        <a href="https://instagram.com/pawanwashudev" target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 bg-gray-900/50 px-4 py-2 rounded-full border border-gray-800 hover:border-pink-500 hover:text-pink-500 transition-all text-gray-300">
                            Instagram
                        </a>
                        <a href="https://linkedin.com/in/pawanwashudev" target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 bg-gray-900/50 px-4 py-2 rounded-full border border-gray-800 hover:border-blue-600 hover:text-blue-600 transition-all text-gray-300">
                            LinkedIn
                        </a>
                    </div>
                </div>

                <p className="text-gray-600 text-sm font-mono">&copy; {new Date().getFullYear()} Neubofy. All rights reserved.</p>
            </div>
        </footer>
      </body>
    </html>
  );
}
