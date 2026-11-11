// @ts-check
const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = tseslint.config(
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],
      'no-restricted-imports': [
        'error',
        { patterns: ['**/features/**'] },
      ],
    },
  },
  {
    // A regra vale para shared/: nada aqui pode depender de uma feature.
    files: ['src/app/shared/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/features/**', '../features/*', '../../features/*'],
              message:
                'shared/ não pode importar de features/. Mova o que for comum para shared/ ou mantenha o código na feature.',
            },
          ],
        },
      ],
    },
  },
  {
    // features/ e core/ podem importar shared/ livremente.
    files: ['src/app/features/**/*.ts', 'src/app/core/**/*.ts'],
    rules: {
      'no-restricted-imports': 'off',
    },
  },
  {
    files: ['**/*.html'],
    extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
    rules: {},
  },
);
