import type { Linter } from 'eslint';

import { interopDefault } from '../util';

export async function perfectionist(): Promise<Linter.Config[]> {
  const perfectionistPlugin = await interopDefault(
    import('eslint-plugin-perfectionist'),
  );

  return [
    perfectionistPlugin.configs['recommended-natural'],
    {
      rules: {
        'perfectionist/sort-exports': [
          'error',
          {
            order: 'asc',
            type: 'natural',
          },
        ],
        'perfectionist/sort-imports': [
          'error',
          {
            customGroups: [
              {
                selector: 'type',
                groupName: 'nebula-core-type',
                elementNamePattern: '^@nebula-core/.+',
              },
              {
                selector: 'type',
                groupName: 'nebula-type',
                elementNamePattern: '^@nebula/.+',
              },
              {
                selector: 'type',
                groupName: 'vue-type',
                elementNamePattern: ['^vue$', '^vue-.+', '^@vue/.+'],
              },
              {
                groupName: 'nebula',
                elementNamePattern: '^@nebula/.+',
              },
              {
                groupName: 'nebula-core',
                elementNamePattern: '^@nebula-core/.+',
              },
              {
                groupName: 'vue',
                elementNamePattern: ['^vue$', '^vue-.+', '^@vue/.+'],
              },
            ],
            environment: 'node',
            groups: [
              ['type-external', 'type-builtin', 'type-import'],
              'vue-type',
              'nebula-type',
              'nebula-core-type',
              ['type-parent', 'type-sibling', 'type-index'],
              ['type-internal'],
              'value-builtin',
              'vue',
              'nebula',
              'nebula-core',
              'value-external',
              'value-internal',
              ['value-parent', 'value-sibling', 'value-index'],
              'side-effect',
              'side-effect-style',
              'style',
              'ts-equals-import',
              'unknown',
            ],
            internalPattern: ['^#/.+'],
            newlinesBetween: 1,
            order: 'asc',
            type: 'natural',
          },
        ],
        'perfectionist/sort-modules': 'off',
        'perfectionist/sort-named-exports': [
          'error',
          {
            order: 'asc',
            type: 'natural',
          },
        ],
        'perfectionist/sort-objects': [
          'off',
          {
            customGroups: {
              items: 'items',
              list: 'list',
              children: 'children',
            },
            groups: ['unknown', 'items', 'list', 'children'],
            ignorePattern: ['children'],
            order: 'asc',
            type: 'natural',
          },
        ],
      },
    },
  ];
}






