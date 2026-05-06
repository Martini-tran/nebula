import type {
  nebulaFormProps as FormProps,
  nebulaFormSchema as FormSchema,
} from '@nebula/common-ui';

import type { ComponentPropsMap, ComponentType } from './component';

import { setupnebulaForm, usenebulaForm as useForm, z } from '@nebula/common-ui';
import { $t } from '@nebula/locales';

async function initSetupnebulaForm() {
  setupnebulaForm<ComponentType>({
    config: {
      modelPropNameMap: {
        Upload: 'fileList',
        CheckboxGroup: 'model-value',
      },
    },
    defineRules: {
      required: (value, _params, ctx) => {
        if (value === undefined || value === null || value.length === 0) {
          return $t('ui.formRules.required', [ctx.label]);
        }
        return true;
      },
      selectRequired: (value, _params, ctx) => {
        if (value === undefined || value === null) {
          return $t('ui.formRules.selectRequired', [ctx.label]);
        }
        return true;
      },
    },
  });
}

const usenebulaForm = useForm<ComponentType, ComponentPropsMap>;

export { initSetupnebulaForm, usenebulaForm, z };

export type nebulaFormSchema = FormSchema<ComponentType, ComponentPropsMap>;
export type nebulaFormProps = FormProps<ComponentType, ComponentPropsMap>;






