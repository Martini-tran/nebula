import type { MenuRecordRaw } from '@nebula/types';

import { describe, expect, it } from 'vitest';

import { buildNavigationGroups } from './navigation';

const menu = (path: string, children?: MenuRecordRaw[]): MenuRecordRaw => ({
  name: path,
  path,
  children,
});

describe('buildNavigationGroups', () => {
  it('only displays domains represented in the permitted menu tree', () => {
    expect(buildNavigationGroups([])).toEqual([]);
    const groups = buildNavigationGroups([
      menu('/ai-flow', [menu('/ai-flow/list')]),
      menu('/blog', [menu('/blog/article')]),
    ]);
    expect(groups.map((group) => group.id)).toEqual(['ai', 'blog']);
    expect(groups[0]?.menus.map((item) => item.path)).toEqual([
      '/ai-flow/list',
    ]);
  });

  it('preserves route data without mutating the access tree', () => {
    const child = {
      ...menu('/ai-knowledge/list'),
      query: { scope: 'mine' },
      parents: ['/ai-knowledge'],
      badge: '2',
    };
    const input = [menu('/ai-knowledge', [child])];
    const before = structuredClone(input);
    const result = buildNavigationGroups(input);
    expect(result[0]?.menus[0]).toEqual(child);
    expect(result[0]?.menus[0]).not.toBe(child);
    expect(input).toEqual(before);
  });

  it('removes hidden parents, descendants and now-empty folders', () => {
    const groups = buildNavigationGroups([
      { ...menu('/system', [menu('/system/user')]), show: false },
      menu('/ai-tool', [{ ...menu('/ai-tool/list'), show: false }]),
      menu('/ai-flow', [
        menu('/ai-flow/list'),
        { ...menu('/ai-flow/editor'), show: false },
      ]),
    ]);
    expect(groups.map((group) => group.id)).toEqual(['ai']);
    expect(groups[0]?.menus.map((item) => item.path)).toEqual([
      '/ai-flow/list',
    ]);
  });

  it('keeps disabled parent semantics when a group contains only one child', () => {
    const disabled = {
      ...menu('/ai-flow', [menu('/ai-flow/list')]),
      disabled: true,
    };
    expect(buildNavigationGroups([disabled])[0]?.menus[0]).toEqual(disabled);
  });

  it('keeps custom modules and external links instead of dropping them', () => {
    const custom = menu('/reports', [menu('/reports/usage')]);
    const external = menu('https://example.test/docs');
    expect(
      buildNavigationGroups([custom, external]).map((group) => group.menus),
    ).toEqual([[custom], [external]]);
  });

  it('does not confuse overlapping prefixes', () => {
    const groups = buildNavigationGroups([
      menu('/ai-relay/model'),
      menu('/ai-flow-extension'),
      menu('/ai-flow/list'),
    ]);
    expect(groups.map((group) => group.id)).toEqual([
      'ai',
      'relay',
      'custom:/ai-flow-extension',
    ]);
  });

  it('recognizes a backend wrapper only when all its children share a domain', () => {
    expect(
      buildNavigationGroups([
        menu('/ai', [menu('/ai-flow/list'), menu('/ai-tool/list')]),
      ])[0]?.id,
    ).toBe('ai');
    expect(
      buildNavigationGroups([
        menu('/mixed', [menu('/ai-flow/list'), menu('/system/user')]),
      ])[0]?.id,
    ).toBe('custom:/mixed');
  });
});
