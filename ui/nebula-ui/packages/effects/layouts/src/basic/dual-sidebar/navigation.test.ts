import type { MenuRecordRaw } from '@nebula/types';

import { describe, expect, it } from 'vitest';

import { findNavigationMenu, firstNavigationMenu } from './navigation';

describe('dual sidebar navigation targets', () => {
  const menus: MenuRecordRaw[] = [
    {
      name: 'hidden',
      path: '/hidden',
      show: false,
      children: [{ name: 'child', path: '/hidden/child' }],
    },
    {
      name: 'disabled',
      path: '/disabled',
      disabled: true,
      children: [{ name: 'child', path: '/disabled/child' }],
    },
    {
      name: 'AI',
      path: '/ai-flow',
      children: [
        { name: 'hidden leaf', path: '/ai-flow/editor', show: false },
        { name: 'flows', path: '/ai-flow/list', query: { owner: 'mine' } },
        { name: 'chat', path: '/ai-flow/chat' },
      ],
    },
  ];

  it('resolves the first usable leaf, not a directory or forbidden child', () => {
    expect(firstNavigationMenu(menus)?.path).toBe('/ai-flow/list');
    expect(firstNavigationMenu(menus.slice(0, 2))).toBeUndefined();
  });

  it('keeps query data and matches an activePath exactly', () => {
    expect(findNavigationMenu(menus, '/ai-flow/list')?.query).toEqual({
      owner: 'mine',
    });
    expect(findNavigationMenu(menus, '/ai-flow/list-extra')).toBeUndefined();
  });

  it('rejects hidden, disabled and revoked remembered targets', () => {
    for (const path of [
      '/hidden/child',
      '/disabled/child',
      '/ai-flow/editor',
      '/revoked',
    ]) {
      expect(findNavigationMenu(menus, path)).toBeUndefined();
    }
    expect(findNavigationMenu([], '/ai-flow/list')).toBeUndefined();
  });
});
