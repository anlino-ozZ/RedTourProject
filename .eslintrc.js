// 全局 ESLint 配置：Vue3 + TypeScript 强制校验，三套前端与 public-common 共用
module.exports = {
  root: true,
  env: {
    browser: true,
    es2022: true,
    node: true,
  },
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    '@vue/eslint-config-typescript',
  ],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
  rules: {
    // 组件名允单词（如 Home、Login）
    'vue/multi-word-component-names': 'off',
    // any 仅警告，便于渐进式类型化
    '@typescript-eslint/no-explicit-any': 'warn',
    // 未使用变量警告，下划线前缀参数忽略
    '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    // 生产环境 console 警告
    'no-console': process.env.NODE_ENV === 'production' ? 'warn' : 'off',
    // 关闭过度严格的模板格式规则，统一交给 Prettier
    'vue/max-attributes-per-line': 'off',
    'vue/singleline-html-element-content-newline': 'off',
    'vue/html-self-closing': 'off',
  },
  ignorePatterns: ['dist/', 'node_modules/', '*.config.js', '*.config.ts', 'env.d.ts'],
}
