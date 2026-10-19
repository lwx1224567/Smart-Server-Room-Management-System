/// <reference types="node" />
// Existing JavaScript SFCs have no generated declaration. Keep their code unchanged.
declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent;
  export default component;
}
