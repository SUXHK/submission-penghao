//  @ts-check

/** @type {import('prettier').Config & import('prettier-plugin-tailwindcss').PluginOptions} */

const config = {
    endOfLine: "lf",
    tabWidth: 4,
    semi: false,
    singleQuote: false,
    trailingComma: "all",
    plugins: ["prettier-plugin-tailwindcss"],
    tailwindStylesheet: "src/styles.css",
    tailwindFunctions: ["cn", "cva"],
}

export default config
