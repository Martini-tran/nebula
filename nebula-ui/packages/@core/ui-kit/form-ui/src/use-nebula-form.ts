import type {
  BaseFormComponentType,
  ExtendedFormApi,
  nebulaFormProps,
} from './types';

import { defineComponent, h, isReactive, onBeforeUnmount, watch } from 'vue';

import { useStore } from '@nebula-core/shared/store';

import { FormApi } from './form-api';
import nebulaUseForm from './use-nebula-form.vue';

export function usenebulaForm<
  T extends BaseFormComponentType = BaseFormComponentType,
  P extends Record<string, any> = Record<never, never>,
>(options: nebulaFormProps<T, P>) {
  const IS_REACTIVE = isReactive(options);
  const api = new FormApi(options as unknown as nebulaFormProps);
  const extendedApi: ExtendedFormApi = api as never;
  extendedApi.useStore = (selector) => {
    return useStore(api.store, selector);
  };

  const Form = defineComponent(
    (props: nebulaFormProps, { attrs, slots }) => {
      onBeforeUnmount(() => {
        api.unmount();
      });
      api.setState({ ...props, ...attrs });
      return () =>
        h(nebulaUseForm, { ...props, ...attrs, formApi: extendedApi }, slots);
    },
    {
      name: 'nebulaUseForm',
      inheritAttrs: false,
    },
  );
  // Add reactivity support
  if (IS_REACTIVE) {
    watch(
      () => options.schema,
      () => {
        api.setState({ schema: options.schema });
      },
      { immediate: true },
    );
  }

  return [Form, extendedApi] as const;
}






