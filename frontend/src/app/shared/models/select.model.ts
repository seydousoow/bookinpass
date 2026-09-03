export interface SelectOption<T = string> {
  readonly label: string;
  readonly value: T;
  readonly hint?: string;
  readonly icon?: string;
  readonly disabled?: boolean;
}
