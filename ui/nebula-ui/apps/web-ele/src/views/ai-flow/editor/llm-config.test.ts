import { describe, expect, it } from 'vitest';

import {
  applyLlmConfigToNode,
  hydrateLlmNodeConfig,
  llmConfigFromNode,
} from './llm-config';

describe('llm node config compatibility', () => {
  it('hydrates harness flat fields for the editor', () => {
    const node = hydrateLlmNodeConfig({
      nodeConfig: {},
      profileCode: 'DS-V3-001',
      systemPrompt: '你是技术编辑。',
      promptTemplate: '请总结 {{article}}',
      temperature: 0.2,
      topP: 0.8,
      maxTokens: 2048,
      outputMode: 'JSON',
    });

    expect((node.nodeConfig as Record<string, any>).llm).toMatchObject({
      model: { profileCode: 'DS-V3-001' },
      prompt: {
        systemPrompt: '你是技术编辑。',
        userPromptTemplate: '请总结 {{article}}',
      },
      parameters: { temperature: 0.2, topP: 0.8, maxTokens: 2048 },
      output: { type: 'JSON' },
    });
  });

  it('uses the flow profile when a generated node inherits its model', () => {
    const config = llmConfigFromNode(
      { promptTemplate: '写一篇文章', nodeConfig: {} },
      'DEFAULT-PROFILE',
    );

    expect(config.model.profileCode).toBe('DEFAULT-PROFILE');
    expect(config.prompt.userPromptTemplate).toBe('写一篇文章');
  });

  it('syncs editor changes back to runtime fields', () => {
    const config = llmConfigFromNode({
      nodeConfig: {
        llm: {
          model: { profileCode: 'PROFILE-2' },
          prompt: {
            systemPrompt: '你是审稿人。',
            userPromptTemplate: '审阅 {{draft}}',
          },
          parameters: { temperature: 0.1, topP: 0.7, maxTokens: 1024 },
          output: { type: 'TEXT' },
        },
      },
    });

    const node = applyLlmConfigToNode({ nodeConfig: {} }, config);

    expect(node).toMatchObject({
      profileCode: 'PROFILE-2',
      systemPrompt: '你是审稿人。',
      promptTemplate: '审阅 {{draft}}',
      temperature: 0.1,
      topP: 0.7,
      maxTokens: 1024,
      outputMode: 'TEXT',
    });
  });
});
