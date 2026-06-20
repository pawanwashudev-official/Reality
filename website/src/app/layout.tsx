import type { Metadata } from "next";
import { Inter } from "next/font/google";
import Link from 'next/link';
import "./globals.css";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Reality - The Intelligent Life OS",
  description: "Stop managing your life. Start commanding it. The 100% Open Source, Neural-powered productivity OS.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className={`${inter.className} bg-neural-bg text-gray-100 font-outfit min-h-screen flex flex-col`}>

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
                <div className="flex flex-wrap justify-center gap-6 mb-6 font-mono text-sm">
                    <Link href="/" className="text-gray-400 hover:text-neural-cyan transition-colors">Home</Link>
                    <Link href="/tapashya" className="text-gray-400 hover:text-neural-cyan transition-colors">Tapasya Web Sync</Link>

                    <Link href="/privacypolicy" className="text-gray-400 hover:text-white transition-colors">Privacy Policy</Link>
                    <Link href="/termsofservice" className="text-gray-400 hover:text-white transition-colors">Terms of Service</Link>
                </div>
                <p className="text-gray-600 text-sm font-mono">&copy; {new Date().getFullYear()} Neubofy. All rights reserved.</p>
            </div>
        </footer>
      </body>
    </html>
  );
}
