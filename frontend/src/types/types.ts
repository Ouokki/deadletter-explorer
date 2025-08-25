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
  topic?: string; // allow if caller provides it
};
