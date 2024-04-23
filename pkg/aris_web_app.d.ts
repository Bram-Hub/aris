/* tslint:disable */
/* eslint-disable */
/**
*/
export function run_app(): void;

export type InitInput = RequestInfo | URL | Response | BufferSource | WebAssembly.Module;

export interface InitOutput {
  readonly memory: WebAssembly.Memory;
  readonly run_app: () => void;
  readonly __wbindgen_malloc: (a: number) => number;
  readonly __wbindgen_realloc: (a: number, b: number, c: number) => number;
  readonly __wbindgen_export_2: WebAssembly.Table;
  readonly wasm_bindgen__convert__closures__invoke0_mut__h3412607c5cf09b91: (a: number, b: number) => void;
  readonly wasm_bindgen__convert__closures__invoke1_mut__hf45b906496727466: (a: number, b: number, c: number) => void;
  readonly wasm_bindgen__convert__closures__invoke1_mut_ref__h5f237d86244114ec: (a: number, b: number, c: number) => void;
  readonly wasm_bindgen__convert__closures__invoke1_mut_ref__h90e0e1b0def134da: (a: number, b: number, c: number) => void;
  readonly wasm_bindgen__convert__closures__invoke1_mut__h0804d9a8d4f71497: (a: number, b: number, c: number) => void;
  readonly __wbindgen_free: (a: number, b: number) => void;
  readonly __wbindgen_exn_store: (a: number) => void;
  readonly __wbindgen_start: () => void;
}

export type SyncInitInput = BufferSource | WebAssembly.Module;
/**
* Instantiates the given `module`, which can either be bytes or
* a precompiled `WebAssembly.Module`.
*
* @param {SyncInitInput} module
*
* @returns {InitOutput}
*/
export function initSync(module: SyncInitInput): InitOutput;

/**
* If `module_or_path` is {RequestInfo} or {URL}, makes a request and
* for everything else, calls `WebAssembly.instantiate` directly.
*
* @param {InitInput | Promise<InitInput>} module_or_path
*
* @returns {Promise<InitOutput>}
*/
export default function init (module_or_path?: InitInput | Promise<InitInput>): Promise<InitOutput>;
