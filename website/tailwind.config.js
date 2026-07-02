/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        'neural-cyan': '#00E5FF',
        'neural-bg': '#05050A',
        'neural-card': '#0D0D14',
        'neural-purple': '#7B61FF',
      },
      fontFamily: {
        outfit: ['Outfit', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', "Liberation Mono", "Courier New", 'monospace'],
      },
      boxShadow: {
        'neon': '0 0 15px rgba(0, 229, 255, 0.5)',
      },
      typography: (theme) => ({
        neural: {
          css: {
            '--tw-prose-body': theme('colors.gray[300]'),
            '--tw-prose-headings': theme('colors.white'),
            '--tw-prose-links': theme('colors.neural-cyan'),
            '--tw-prose-bold': theme('colors.white'),
            '--tw-prose-quotes': theme('colors.neural-purple'),
            '--tw-prose-code': theme('colors.neural-cyan'),
            '--tw-prose-hr': theme('colors.gray[800]'),
            '--tw-prose-th-borders': theme('colors.gray[700]'),
            '--tw-prose-td-borders': theme('colors.gray[800]'),
          },
        },
      }),
    },
  },
  plugins: [
    require('@tailwindcss/typography'),
  ],
}
