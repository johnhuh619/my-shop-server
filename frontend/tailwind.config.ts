import seedPlugin from '@seed-design/tailwind3-plugin'
import type { Config } from 'tailwindcss'

const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {},
  },
  plugins: [seedPlugin],
}

export default config

