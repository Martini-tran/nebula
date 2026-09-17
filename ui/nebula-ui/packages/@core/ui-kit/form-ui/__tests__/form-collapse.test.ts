import type { App } from 'vue';

import type { nebulaFormProps } from '../src/types';

import { createApp, defineComponent, h, nextTick } from 'vue';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { COMPONENT_MAP } from '../src/config';
import NebulaForm from '../src/nebula-form.vue';
import { usenebulaForm } from '../src/use-nebula-form';

describe('search form collapse actions', () => {
  let app: App;
  let container: HTMLDivElement;
  const originalComponents = { ...COMPONENT_MAP };

  beforeEach(() => {
    container = document.createElement('div');
    document.body.append(container);
    const Button = defineComponent((_, { attrs, slots }) => () =>
      h('button', attrs, slots.default?.()),
    );
    COMPONENT_MAP.DefaultButton = Button;
    COMPONENT_MAP.PrimaryButton = Button;
    COMPONENT_MAP.nebulaInput = defineComponent((_, { attrs }) => () =>
      h('input', attrs),
    );

    // happy-dom 不计算布局：模拟桌面每行三个条件，操作区单独占一列。
    // 其余表单渲染、折叠判断、插槽传值和按钮均走真实组件。
    const style = document.createElement('style');
    style.textContent = '.grid { grid-template-rows: 40px 40px; }';
    container.append(style);
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(
      function (this: Element) {
        const parent = this.parentElement;
        const index = parent?.classList.contains('grid')
          ? [...parent.children].indexOf(this)
          : 0;
        return new DOMRect(0, Math.floor(index / 3) * 40, 200, 40);
      },
    );
  });

  afterEach(() => {
    app?.unmount();
    container.remove();
    Object.assign(COMPONENT_MAP, originalComponents);
    vi.restoreAllMocks();
  });

  function mountForm(count: number, direct = false, showCollapseButton = true) {
    const options: nebulaFormProps = {
      collapsed: true,
      schema: Array.from({ length: count }, (_, index) => ({
        component: 'nebulaInput',
        fieldName: `condition${index}`,
        label: `条件 ${index + 1}`,
      })),
      showCollapseButton,
      showDefaultActions: true,
    };
    app = createApp({
      setup() {
        if (direct) {
          return () => h(NebulaForm, options);
        }
        const [Form] = usenebulaForm(options);
        return () => h(Form);
      },
    });
    const root = document.createElement('div');
    container.append(root);
    app.mount(root);
  }

  it.each([2, 3])(
    'hides the toggle for %i conditions through the table form hook',
    async (count) => {
      mountForm(count);
      await nextTick();
      await nextTick();
      expect(container.querySelectorAll('input')).toHaveLength(count);
      expect(container.querySelector('[aria-expanded]')).toBeNull();
      expect(container.querySelectorAll('button')).toHaveLength(2);
    },
  );

  it('shows the toggle before reset/submit and expands only overflowing conditions', async () => {
    mountForm(4);
    await vi.waitFor(() => {
      expect(container.querySelector('[aria-expanded]')).not.toBeNull();
    });
    const toggle = container.querySelector<HTMLButtonElement>('[aria-expanded]')!;
    expect(container.querySelector('button')).toBe(toggle);
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(container.querySelectorAll('.grid > .hidden')).toHaveLength(1);

    toggle.click();
    await nextTick();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(container.querySelectorAll('.grid > .hidden')).toHaveLength(0);

    toggle.click();
    await nextTick();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(container.querySelectorAll('.grid > .hidden')).toHaveLength(1);
  });

  it('also hides the toggle through the direct form entry', async () => {
    mountForm(2, true);
    await nextTick();
    await nextTick();
    expect(container.querySelector('[aria-expanded]')).toBeNull();
  });

  it('respects explicitly disabled collapse actions', async () => {
    mountForm(4, false, false);
    await nextTick();
    await nextTick();
    expect(container.querySelector('[aria-expanded]')).toBeNull();
    expect(container.querySelectorAll('.grid > .hidden')).toHaveLength(0);
  });
});
