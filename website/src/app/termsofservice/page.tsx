import React from 'react';
import Link from 'next/link';

export default function TermsOfService() {
  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-3xl mx-auto">
        <Link href="/" className="inline-flex items-center text-blue-600 hover:text-blue-800 mb-8 transition-colors">
          <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" /></svg>
          Back to Home
        </Link>

        <div className="bg-white shadow-sm rounded-xl p-8 sm:p-12 border border-gray-100">
          <div className="text-center mb-10 border-b border-gray-100 pb-8">
            <h1 className="text-4xl font-extrabold text-gray-900 tracking-tight mb-2">Terms of Service</h1>
            <p className="text-gray-500 font-medium">Effective Date: May 19, 2030</p>
          </div>

          <div className="prose prose-blue max-w-none text-gray-600">
            <div className="space-y-10">
              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">1. Acceptance of Terms</h2>
                <p>By downloading, accessing, or using the Reality Engine application (&quot;the App&quot;), you agree to be bound by these Terms of Service. If you do not agree to these terms, please do not use the App.</p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">2. Open Source License</h2>
                <p>Reality is open-source software licensed under the Apache License 2.0. You may use, reproduce, and distribute the software subject to the terms of the Apache License 2.0. The source code is available publicly on GitHub.</p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">3. Description of Service</h2>
                <p>Reality is a productivity and lifestyle operating system designed to help users manage their time, restrict access to distracting apps, and integrate with Google Workspace and third-party AI services via a &quot;Bring Your Own Key&quot; (BYOK) model. The App operates entirely on your local device.</p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">4. User Responsibilities</h2>
                <ul className="list-disc pl-6 space-y-4">
                  <li><strong>Device Admin Permissions:</strong> The App utilizes features such as &quot;Armored Strict Mode&quot; which may request Device Administrator privileges to prevent the App from being uninstalled during active focus sessions. You are solely responsible for enabling this feature and understanding its implications.</li>
                  <li><strong>Third-Party Accounts:</strong> The App allows you to integrate with your personal Google account (Google Drive, Docs, Tasks, Calendar) and third-party AI providers (via API keys). You are responsible for maintaining the security of these accounts and any API keys you provide to the App.</li>
                  <li><strong>Data Loss:</strong> As the App operates locally and saves backups to your personal Google Drive, we are not responsible for any data loss resulting from device failure, accidental deletion, or misconfiguration.</li>
                </ul>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">5. Privacy</h2>
                <p>Your privacy is critically important to us. Please review our <Link href="/privacypolicy" className="text-blue-600 font-medium hover:underline">Privacy Policy</Link> to understand how your local data is handled. We do not collect, store, or transmit your data to our servers.</p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">6. Disclaimers</h2>
                <p className="mb-4">The App is provided on an &quot;AS IS&quot; and &quot;AS AVAILABLE&quot; basis, without warranties of any kind, either express or implied. We do not guarantee that the App will be uninterrupted, error-free, or completely secure.</p>
                <p>We are not responsible for the actions or content of third-party services (such as Google or AI API providers) that you choose to integrate with the App.</p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">7. Limitation of Liability</h2>
                <p>To the fullest extent permitted by applicable law, Neubofy and its developers shall not be liable for any indirect, incidental, special, consequential, or punitive damages, or any loss of profits or revenues, whether incurred directly or indirectly, or any loss of data, use, goodwill, or other intangible losses, resulting from your access to or use of or inability to access or use the App.</p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">8. Changes to Terms</h2>
                <p>We reserve the right to modify these Terms at any time. We will provide notice of significant changes by updating the &quot;Effective Date&quot; at the top of these Terms. Your continued use of the App after any such changes constitutes your acceptance of the new Terms.</p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">9. Contact Information</h2>
                <p>If you have any questions about these Terms, please contact us at: <a href="mailto:founder@neubofy.in" className="text-blue-600 font-medium hover:underline">founder@neubofy.in</a></p>
              </section>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
