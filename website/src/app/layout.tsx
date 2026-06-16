import type { Metadata } from "next";
import { Inter } from "next/font/google";
import Link from 'next/link';
import "./globals.css";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Reality - The Intelligent Life OS",
  description: "Stop managing your life. Start commanding it. The 100% Open Source, Free, No Ads, AI-powered productivity OS.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className={`${inter.className} bg-gray-50 text-gray-900`}>
        {children}
        <footer className="bg-gray-100 py-12 border-t border-gray-200 mt-20">
            <div className="max-w-4xl mx-auto px-6 text-center">
                <div className="flex justify-center space-x-6 mb-6">
                    <Link href="/" className="text-blue-600 hover:underline">Home</Link>
                    <Link href="/privacypolicy" className="text-blue-600 hover:underline">Privacy Policy</Link>
                    <Link href="/termsofservice" className="text-blue-600 hover:underline">Terms of Service</Link>
                </div>
                <p className="text-gray-500 text-sm">&copy; {new Date().getFullYear()} Neubofy. All rights reserved.</p>
            </div>
        </footer>
      </body>
    </html>
  );
}
