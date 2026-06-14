import { defineConfig } from "@esmate/eslint"

export default defineConfig(
    {
        type: "app",
        react: true,
        tanstack: {
            query: true,
            router: true,
        },
        ignores: ["./src/routeTree.gen.ts","src/components/ui"],
    },

    {
        rules: {
            "node/prefer-global/process": "off",
            "no-console": "off",
            "jsonc/indent": ["error", 4, {}],
            "unicorn/filename-case": "off",
            "node/no-process-env": "off",
        },
    },
)
