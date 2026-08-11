import { createApp } from 'vue';

import { afterEach, describe, expect, it } from 'vitest';

import CopilotActionCard from './CopilotActionCard.vue';

const mountedApps: Array<ReturnType<typeof createApp>> = [];

function renderOperation(result?: Record<string, any>) {
  const host = document.createElement('div');
  const app = createApp(CopilotActionCard, {
    operation: {
      operationId: 'op-1',
      action: 'REAL_RUN_DRAFT',
      draftId: 'draft-1',
      revision: 3,
      status: 'SUCCEEDED',
      ...(result === undefined ? {} : { result }),
    },
  });
  mountedApps.push(app);
  app.mount(host);
  return host;
}

afterEach(() => {
  mountedApps.splice(0).forEach((app) => app.unmount());
});

describe('CopilotActionCard', () => {
  it('renders a successful real-run result as formatted JSON', () => {
    const host = renderOperation({
      answer: 'done',
      details: { count: 2 },
    });

    expect(host.textContent).toContain('执行结果');
    expect(host.querySelector('.action-result-content')?.textContent).toContain(
      '"answer": "done"',
    );
    expect(host.querySelector('.action-result-content')?.textContent).toContain(
      '"count": 2',
    );
  });

  it('shows an explicit empty state when a successful run has no output', () => {
    const host = renderOperation();

    expect(host.textContent).toContain('流程已成功执行，未返回输出数据');
    expect(host.querySelector('.action-result-content')).toBeNull();
  });
});
