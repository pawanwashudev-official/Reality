/* eslint-disable react/no-unescaped-entities */
import Link from "next/link";
import { ArrowLeft, FileText, CheckCircle, Smartphone, AlertTriangle, Scale, Mail, ShieldCheck } from "lucide-react";

export default function TermsOfService() {
  return (
    <div className="min-h-screen bg-neutral-50 text-neutral-900 py-12 px-4">
      <div className="container mx-auto max-w-3xl">
        <Link href="/" className="inline-flex items-center text-blue-600 hover:text-blue-800 font-medium mb-8 transition-colors">
          <ArrowLeft className="w-4 h-4 mr-2" /> Back to Home
        </Link>

        <div className="bg-white rounded-3xl p-8 md:p-12 shadow-sm border border-neutral-200">
          <div className="text-center mb-10 border-b border-neutral-100 pb-10">
            <div className="inline-flex items-center justify-center w-16 h-16 bg-blue-50 text-blue-600 rounded-2xl mb-6">
              <FileText className="w-8 h-8" />
            </div>
            <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight mb-4 text-neutral-900">Terms of Service</h1>
            <p className="text-neutral-500 font-medium">Effective Date: June 11, 2024</p>
          </div>

          <div className="prose prose-lg prose-neutral max-w-none">
            <div className="space-y-12">
              <section>
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">
                  <CheckCircle className="w-6 h-6 mr-3 text-neutral-400" /> 1. Acceptance of Terms
                </h2>
                <p className="text-neutral-700">By downloading, accessing, or using the Reality application ("the App"), you agree to be bound by these Terms of Service. If you do not agree to these terms, please do not use the App.</p>
              </section>

              <section>
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">
                  <FileText className="w-6 h-6 mr-3 text-neutral-400" /> 2. Open Source License
                </h2>
                <p className="text-neutral-700">Reality is open-source software licensed under the Apache License 2.0. You may use, reproduce, and distribute the software subject to the terms of the Apache License 2.0. The source code is available publicly on GitHub.</p>
              </section>

              <section>
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">
                  <Smartphone className="w-6 h-6 mr-3 text-neutral-400" /> 3. Description of Service
                </h2>
                <p className="text-neutral-700">Reality is a productivity and lifestyle operating system designed to help users manage their time, restrict access to distracting apps, and integrate with Google Workspace and third-party AI services via a "Bring Your Own Key" (BYOK) model. The App operates entirely on your local device.</p>
              </section>

              <section>
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">
                  <AlertTriangle className="w-6 h-6 mr-3 text-neutral-400" /> 4. User Responsibilities
                </h2>
                <ul className="list-disc pl-6 space-y-4 text-neutral-700">
                  <li><strong>Device Admin Permissions:</strong> The App utilizes features such as "Armored Strict Mode" which may request Device Administrator privileges to prevent the App from being uninstalled during active focus sessions. You are solely responsible for enabling this feature and understanding its implications.</li>
                  <li><strong>Third-Party Accounts:</strong> The App allows you to integrate with your personal Google account (Google Drive, Docs, Tasks, Calendar) and third-party AI providers (via API keys). You are responsible for maintaining the security of these accounts and any API keys you provide to the App.</li>
                  <li><strong>Data Loss:</strong> As the App operates locally and saves backups to your personal Google Drive, we are not responsible for any data loss resulting from device failure, accidental deletion, or misconfiguration.</li>
                </ul>
              </section>

              <section>
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">
                  <ShieldCheck className="w-6 h-6 mr-3 text-neutral-400" /> 5. Privacy
                </h2>
                <p className="text-neutral-700">Your privacy is critically important to us. Please review our <Link href="/privacypolicy" className="text-blue-600 hover:underline">Privacy Policy</Link> to understand how your local data is handled. We do not collect, store, or transmit your data to our servers.</p>
              </section>

              <section>
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">
                  <AlertTriangle className="w-6 h-6 mr-3 text-neutral-400" /> 6. Disclaimers
                </h2>
                <p className="text-neutral-700 mb-4">The App is provided on an "AS IS" and "AS AVAILABLE" basis, without warranties of any kind, either express or implied. We do not guarantee that the App will be uninterrupted, error-free, or completely secure.</p>
                <p className="text-neutral-700">We are not responsible for the actions or content of third-party services (such as Google or AI API providers) that you choose to integrate with the App.</p>
              </section>

              <section>
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">
                  <Scale className="w-6 h-6 mr-3 text-neutral-400" /> 7. Limitation of Liability
                </h2>
                <p className="text-neutral-700">To the fullest extent permitted by applicable law, Neubofy and its developers shall not be liable for any indirect, incidental, special, consequential, or punitive damages, or any loss of profits or revenues, whether incurred directly or indirectly, or any loss of data, use, goodwill, or other intangible losses, resulting from your access to or use of or inability to access or use the App.</p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-neutral-900 mb-4 pb-2 border-b border-neutral-100">8. Changes to Terms</h2>
                <p className="text-neutral-700">We reserve the right to modify these Terms at any time. We will provide notice of significant changes by updating the "Effective Date" at the top of these Terms. Your continued use of the App after any such changes constitutes your acceptance of the new Terms.</p>
              </section>

              <section className="bg-neutral-50 p-6 rounded-xl border border-neutral-200 mt-8">
                <h2 className="flex items-center text-2xl font-bold text-neutral-900 mt-0 mb-3">
                  <Mail className="w-6 h-6 mr-3 text-neutral-400" /> 9. Contact Information
                </h2>
                <p className="m-0 text-neutral-700">If you have any questions about these Terms, please contact us at: <a href="mailto:founder@neubofy.in" className="text-blue-600 font-bold hover:underline">founder@neubofy.in</a></p>
              </section>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
