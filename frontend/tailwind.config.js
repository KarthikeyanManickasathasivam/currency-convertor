/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  // Disable preflight so Tailwind's CSS reset doesn't override Angular Material's
  // form field border/pseudo-element styles (causes label-overlap bug).
  corePlugins: {
    preflight: false,
  },
  theme: {
    extend: {}
  },
  plugins: []
};
