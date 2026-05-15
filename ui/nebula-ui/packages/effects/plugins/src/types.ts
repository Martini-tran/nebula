import type { Component } from 'vue';

export interface nebulaPluginsFormOptions {
  usenebulaForm: (...args: any[]) => any;
}

export interface nebulaPluginsModalOptions {
  usenebulaModal?: () => any;
}

export interface nebulaPluginsMessageOptions {
  useMessage?: () => any;
}

export interface nebulaPluginsComponentsOptions {
  [key: string]: Component;
}

export interface nebulaPluginsOptions {
  form?: nebulaPluginsFormOptions;
  modal?: nebulaPluginsModalOptions;
  message?: nebulaPluginsMessageOptions;
  components?: nebulaPluginsComponentsOptions;
}






