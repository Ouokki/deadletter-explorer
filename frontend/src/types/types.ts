export type MessageDto = {
  topic?: string;
  partition: number;
  offset: number;
  timestamp: number;
  keyUtf8?: string | null;
  valueUtf8?: string | null;
  valueBase64?: string | null;
  headers: Record<string, string>;
};

export type ReplayItemDto = {
  partition: number;
  offset: number;
  valueBase64?: string | null;
  headersBase64?: Record<string, string>;
  topic?: string; 
};

export type RedactionAction = 'MASK' | 'REMOVE' | 'HASH';

export type MaskOptions = { keepFirst?: number; keepLast?: number; pad?: string; fixed?: string };

export type HashOptions = { secretRef?: string; short?: boolean };

export type Rule = {
  id: string;
  path: string;
  action: RedactionAction;
  mask?: MaskOptions;
  hash?: HashOptions;
  enabled?: boolean;
  note?: string;
};

export type AuditItem = {
  path: string;
  action: RedactionAction;
  count: number;
  note?: string;
  error?: string;
};
export type PreviewResponse = { redacted: any; audit: AuditItem[] };

export type PreviewAuditItem = {
  path: string;
  action: RedactionAction;
  count: number;
  note?: string;
  error?: string;
};

export type RedactionStudioProps = {
  initialRules?: Rule[];
  initialSampleJson?: string;
  onPickMessage?: () => Promise<string | undefined>;
  onSave?: (rules: Rule[]) => Promise<void>;
  onValidatePath?: (path: string, sample: any) => Promise<{ ok: boolean; message?: string } | undefined>;
};

export type AuthUser = {
  username?: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  realmRoles: string[];
  clientRoles: Record<string, string[]>;
  raw: any;
};

