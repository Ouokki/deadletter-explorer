import React, { useEffect, useMemo, useState } from "react";
import { JSONPath } from "jsonpath-plus";
import ReactJson from "react-json-view";
import * as jsondiffpatch from "jsondiffpatch";
import { Eye, EyeOff, Plus, Save, Trash2, Play, Shield, Undo2 } from "lucide-react";
import { HashOptions, MaskOptions, PreviewAuditItem, RedactionAction, RedactionStudioProps, Rule } from "../types/types";

// ---------- Utilities ----------
const uid = () => Math.random().toString(36).slice(2, 10);

function safeParse(json: string): any | undefined {
  try { return JSON.parse(json); } catch { return undefined; }
}

function fnv1a(val: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < val.length; i++) {
    h ^= val.charCodeAt(i);
    h += (h << 1) + (h << 4) + (h << 7) + (h << 8) + (h << 24);
  }
  return (h >>> 0).toString(16).padStart(8, "0");
}

function pointerGet(root: any, pointer: string): any {
  const parts = pointer.split("/").slice(1).map((p) => p.replace(/~1/g, "/").replace(/~0/g, "~"));
  return parts.reduce((acc, key) => (acc !== undefined ? acc[key] : undefined), root);
}
function pointerSet(root: any, pointer: string, value: any) {
  const parts = pointer.split("/").slice(1).map((p) => p.replace(/~1/g, "/").replace(/~0/g, "~"));
  const last = parts.pop(); if (!last) return;
  const parent = parts.reduce((acc, key) => (acc !== undefined ? acc[key] : undefined), root);
  if (parent && typeof parent === "object") parent[last] = value;
}
function pointerDelete(root: any, pointer: string) {
  const parts = pointer.split("/").slice(1).map((p) => p.replace(/~1/g, "/").replace(/~0/g, "~"));
  const last = parts.pop(); if (!last) return;
  const parent = parts.reduce((acc, key) => (acc !== undefined ? acc[key] : undefined), root);
  if (parent && typeof parent === "object") {
    if (Array.isArray(parent)) {
      const idx = Number(last); if (!Number.isNaN(idx)) parent.splice(idx, 1);
    } else { delete (parent as any)[last]; }
  }
}

function maskString(value: any, opts?: MaskOptions): any {
  if (value == null) return value;
  const s = String(value);
  if (opts?.fixed) return opts.fixed;
  const keepFirst = opts?.keepFirst ?? 0;
  const keepLast = opts?.keepLast ?? 0;
  const pad = opts?.pad ?? "*";
  const middleLen = Math.max(0, s.length - keepFirst - keepLast);
  return s.slice(0, keepFirst) + (middleLen > 0 ? pad.repeat(middleLen) : "") + s.slice(s.length - keepLast);
}
function hashPreview(value: any, opts?: HashOptions): any {
  const s = String(value);
  const core = fnv1a(s + "|" + (opts?.secretRef ?? "preview"));
  const tag = opts?.short ? core.slice(0, 8) : core;
  return `h:${tag}`;
}
function deepClone<T>(obj: T): T { return JSON.parse(JSON.stringify(obj)); }

function applyRulesForPreview(src: any, rules: Rule[]): { redacted: any; audit: PreviewAuditItem[] } {
  const root = deepClone(src);
  const audit: PreviewAuditItem[] = [];
  for (const r of rules) {
    if (r.enabled === false) continue;
    try {
      const pointers: string[] = JSONPath({ path: r.path, json: root, resultType: "pointer" }) as any;
      if (!pointers || pointers.length === 0) { audit.push({ path: r.path, action: r.action, count: 0, note: r.note }); continue; }
      let count = 0;
      for (const ptr of pointers) {
        const oldVal = pointerGet(root, ptr);
        if (oldVal === undefined) continue;
        switch (r.action) {
          case "REMOVE": pointerDelete(root, ptr); count++; break;
          case "MASK": pointerSet(root, ptr, maskString(oldVal, r.mask)); count++; break;
          case "HASH": pointerSet(root, ptr, hashPreview(oldVal, r.hash)); count++; break;
        }
      }
      audit.push({ path: r.path, action: r.action, count, note: r.note });
    } catch (e: any) {
      audit.push({ path: r.path, action: r.action, count: 0, error: e?.message ?? String(e) });
    }
  }
  return { redacted: root, audit };
}

// ---- Theming helpers (navy–indigo, low gloss) ----
function Badge({ children, tone = "secondary" }: { children: React.ReactNode; tone?: "secondary" | "danger" }) {
  const cls =
    tone === "danger"
      ? "bg-rose-900/30 text-rose-200 ring-1 ring-inset ring-rose-800/40"
      : "bg-white/5 text-slate-200 ring-1 ring-inset ring-white/10";
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>{children}</span>
  );
}

function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-6 w-11 items-center rounded-full transition ${checked ? "bg-indigo-600" : "bg-slate-600"}`}
      aria-pressed={checked}
    >
      <span className={`inline-block h-5 w-5 transform rounded-full bg-white transition ${checked ? "translate-x-5" : "translate-x-1"}`} />
    </button>
  );
}

function TextInput(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className={[
        "w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-400",
        "focus:outline-none focus:ring-2 focus:ring-indigo-500",
        props.className || "",
      ].join(" ")}
    />
  );
}
function NumberInput(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return <TextInput type="number" {...props} />;
}
function TextArea(props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      {...props}
      className={[
        "w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm font-mono text-slate-100 placeholder:text-slate-400",
        "focus:outline-none focus:ring-2 focus:ring-indigo-500",
        props.className || "",
      ].join(" ")}
    />
  );
}
function Select(props: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      {...props}
      className={[
        "w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-slate-100",
        "focus:outline-none focus:ring-2 focus:ring-indigo-500",
        props.className || "",
      ].join(" ")}
    />
  );
}
function Button({
  variant = "solid",
  className,
  ...rest
}: { variant?: "solid" | "ghost" | "secondary" } & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const base =
    "inline-flex items-center gap-2 rounded-xl px-3.5 py-2 text-sm font-medium transition focus:outline-none focus:ring-2 focus:ring-indigo-500";
  const variants = {
    solid: "bg-indigo-600 text-white hover:bg-indigo-500",
    secondary: "border border-white/10 text-slate-200 hover:bg-white/5",
    ghost: "text-slate-300 hover:bg-white/5",
  } as const;
  return <button className={`${base} ${variants[variant]} disabled:opacity-50 disabled:cursor-not-allowed ${className || ""}`} {...rest} />;
}

function RedactionStudio(props: RedactionStudioProps) {
  const [rules, setRules] = useState<Rule[]>(
    () =>
      props.initialRules ?? [
        { id: uid(), path: "$.customer.email", action: "MASK", mask: { keepLast: 3, pad: "*" }, enabled: true, note: "mask email" },
        { id: uid(), path: "$.payment.card.number", action: "HASH", hash: { secretRef: "REDACTION_KEY", short: true }, enabled: true },
        { id: uid(), path: "$.payment.card.cvv", action: "MASK", mask: { fixed: "[REDACTED]" }, enabled: true },
      ]
  );

  const [sampleText, setSampleText] = useState<string>(() =>
    props.initialSampleJson ??
    JSON.stringify(
      {
        customer: { name: "Alice Martin", email: "alice.martin@example.com", phone: "+33612345678" },
        payment: { card: { number: "4111111111111111", cvv: "123" }, amount: 129.9 },
        meta: { ts: 1724693034000, source: "dlq/orders-DLQ" },
      },
      null,
      2
    )
  );

  const sampleObj = useMemo(() => safeParse(sampleText), [sampleText]);
  const [redacted, setRedacted] = useState<any | undefined>();
  const [audit, setAudit] = useState<PreviewAuditItem[]>([]);
  const [hasPreviewed, setHasPreviewed] = useState(false);
  const [showDiff, setShowDiff] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | undefined>();
  const [validateOnEdit, setValidateOnEdit] = useState(false);
  const [tab, setTab] = useState<"paste" | "pick">("paste");

  const diff = useMemo(() => {
    if (!sampleObj || !redacted) return undefined;
    return jsondiffpatch.diff(sampleObj, redacted);
  }, [sampleObj, redacted]);

  function addRule() {
    setRules((rs) => [...rs, { id: uid(), path: "$.path.to.field", action: "MASK", mask: { keepLast: 4, pad: "*" }, enabled: true }]);
  }
  function removeRule(id: string) { setRules((rs) => rs.filter((r) => r.id !== id)); }
  function updateRule<T extends keyof Rule>(id: string, key: T, val: Rule[T]) { setRules((rs) => rs.map((r) => (r.id === id ? { ...r, [key]: val } : r))); }

  function runPreview() {
    setError(undefined);
    if (!sampleObj) { setError("Invalid sample JSON."); return; }
    try {
      const { redacted, audit } = applyRulesForPreview(sampleObj, rules);
      setRedacted(redacted); setAudit(audit); setHasPreviewed(true);
    } catch (e: any) { setError(e?.message ?? String(e)); }
  }

  async function handleSave() {
    if (!props.onSave) return;
    setSaving(true); setError(undefined);
    try { await props.onSave(rules); } catch (e: any) { setError(e?.message ?? String(e)); } finally { setSaving(false); }
  }

  async function pickMessage() {
    if (!props.onPickMessage) return;
    const body = await props.onPickMessage();
    if (body) setSampleText(pretty(body));
  }

  function pretty(json: string) { try { return JSON.stringify(JSON.parse(json), null, 2); } catch { return json; } }

  useEffect(() => {
    if (!validateOnEdit || !props.onValidatePath) return;
    const id = setTimeout(async () => {
      const r = rules[rules.length - 1]; if (!r) return;
      const sample = sampleObj ?? {};
      try { const res = await props.onValidatePath!(r.path, sample); if (res && !res.ok) setError(res.message ?? "JSONPath invalid"); } catch {}
    }, 500);
    return () => clearTimeout(id);
  }, [rules, validateOnEdit, sampleObj]);

  const rulesInvalid = useMemo(() => rules.some((r) => !r.path || !r.action), [rules]);

  return (
    <div className="mx-auto max-w-[1400px] p-4 md:p-6 lg:p-8 text-slate-100">
      <div className="mb-6 flex items-center gap-3">
        <Shield className="h-5 w-5 text-indigo-400" />
        <h1 className="text-2xl font-semibold tracking-tight">Redaction Studio</h1>
        <Badge>Preview before saving</Badge>
      </div>

      {error && (
        <div className="mb-4 rounded-xl border border-rose-800/40 bg-rose-900/30 p-4 text-sm text-rose-200">
          <div className="font-semibold">Erreur</div>
          <div>{error}</div>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        {/* Left column: Rules builder */}
        <div className="space-y-4 lg:col-span-5">
          <div className="rounded-2xl border border-white/10 bg-[#0F162B]">
            <div className="border-b border-white/10 p-4">
              <h2 className="text-base font-semibold">Rules</h2>
            </div>
            <div className="space-y-3 p-4">
              {rules.map((r) => (
                <div key={r.id} className="rounded-xl border border-white/10 p-3">
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <Toggle checked={r.enabled !== false} onChange={(v) => updateRule(r.id, "enabled", v)} />
                      <Badge tone={r.action === "REMOVE" ? "danger" : "secondary"}>{r.action}</Badge>
                    </div>
                    <div className="flex items-center gap-1">
                      <button
                        className="inline-flex items-center justify-center rounded-lg p-2 text-slate-300 hover:bg-white/5 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                        onClick={() => removeRule(r.id)}
                        aria-label="Remove rule"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
                    <div className="md:col-span-3">
                      <label className="mb-1 block text-sm text-slate-300">JSONPath</label>
                      <TextInput
                        placeholder="$.customer.email or $.items[*].secret"
                        value={r.path}
                        onChange={(e) => updateRule(r.id, "path", e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-sm text-slate-300">Action</label>
                      <Select value={r.action} onChange={(e) => updateRule(r.id, "action", e.target.value as RedactionAction)}>
                        <option value="MASK">MASK</option>
                        <option value="REMOVE">REMOVE</option>
                        <option value="HASH">HASH</option>
                      </Select>
                    </div>
                  </div>

                  {r.action === "MASK" && (
                    <div className="mt-3 grid grid-cols-3 gap-3">
                      <div>
                        <label className="mb-1 block text-sm text-slate-300">keepFirst</label>
                        <NumberInput
                          value={r.mask?.keepFirst ?? 0}
                          onChange={(e) => updateRule(r.id, "mask", { ...r.mask, keepFirst: Number(e.target.value) })}
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-sm text-slate-300">keepLast</label>
                        <NumberInput
                          value={r.mask?.keepLast ?? 0}
                          onChange={(e) => updateRule(r.id, "mask", { ...r.mask, keepLast: Number(e.target.value) })}
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-sm text-slate-300">pad</label>
                        <TextInput
                          value={r.mask?.pad ?? "*"}
                          onChange={(e) => updateRule(r.id, "mask", { ...r.mask, pad: e.target.value })}
                        />
                      </div>
                      <div className="col-span-3">
                        <label className="mb-1 block text-sm text-slate-300">fixed replacement (optional)</label>
                        <TextInput
                          placeholder="[REDACTED]"
                          value={r.mask?.fixed ?? ""}
                          onChange={(e) => updateRule(r.id, "mask", { ...r.mask, fixed: e.target.value })}
                        />
                      </div>
                    </div>
                  )}

                  {r.action === "HASH" && (
                    <div className="mt-3 grid grid-cols-2 gap-3">
                      <div>
                        <label className="mb-1 block text-sm text-slate-300">secretRef (label)</label>
                        <TextInput
                          placeholder="REDACTION_KEY"
                          value={r.hash?.secretRef ?? ""}
                          onChange={(e) => updateRule(r.id, "hash", { ...r.hash, secretRef: e.target.value })}
                        />
                      </div>
                      <label className="mb-1 flex items-center gap-2 text-sm text-slate-300">
                        <input
                          type="checkbox"
                          className="h-4 w-4 rounded border-white/10 bg-white/5 accent-indigo-500"
                          checked={!!r.hash?.short}
                          onChange={(e) => updateRule(r.id, "hash", { ...r.hash, short: e.target.checked })}
                        />
                        short hash
                      </label>
                    </div>
                  )}

                  <div className="mt-3">
                    <label className="mb-1 block text-sm text-slate-300">Note (optional)</label>
                    <TextInput
                      placeholder="why this rule exists (audit)"
                      value={r.note ?? ""}
                      onChange={(e) => updateRule(r.id, "note", e.target.value)}
                    />
                  </div>
                </div>
              ))}

              <div className="flex items-center gap-2">
                <Button onClick={addRule} variant="secondary">
                  <Plus className="h-4 w-4" /> Add rule
                </Button>
                <div className="mx-2 h-6 w-px bg-white/10" />
                <label className="flex items-center gap-2 text-sm text-slate-400">
                  <input
                    type="checkbox"
                    className="h-4 w-4 rounded border-white/10 bg-white/5 accent-indigo-500"
                    checked={validateOnEdit}
                    onChange={(e) => setValidateOnEdit(e.target.checked)}
                  />
                  Validate JSONPath on edit (server)
                </label>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Button onClick={runPreview}>
              <Play className="h-4 w-4" /> Preview
            </Button>
            <Button onClick={handleSave} disabled={!props.onSave || rulesInvalid || !hasPreviewed || saving}>
              <Save className="h-4 w-4" /> Save rules
            </Button>
            <Button variant="ghost" onClick={() => { setRedacted(undefined); setAudit([]); setHasPreviewed(false); }}>
              <Undo2 className="h-4 w-4" /> Reset preview
            </Button>
          </div>
        </div>

        {/* Right column: Sample + Preview */}
        <div className="space-y-4 lg:col-span-7">
          <div className="rounded-2xl border border-white/10 bg-[#0F162B]">
            <div className="border-b border-white/10 p-4">
              <h2 className="text-base font-semibold">Sample payload</h2>
            </div>
            <div className="p-4">
              <div className="mb-3 inline-flex rounded-lg border border-white/10 bg-white/5 p-1 text-sm">
                <button
                  onClick={() => setTab("paste")}
                  className={`rounded-md px-3 py-1.5 ${tab === "paste" ? "bg-[#0F162B] text-slate-100" : "text-slate-400 hover:text-slate-200"}`}
                >
                  Paste JSON
                </button>
                <button
                  onClick={() => props.onPickMessage && setTab("pick")}
                  className={`rounded-md px-3 py-1.5 ${tab === "pick" ? "bg-[#0F162B] text-slate-100" : "text-slate-400 hover:text-slate-200"} ${!props.onPickMessage ? "opacity-50" : ""}`}
                  disabled={!props.onPickMessage}
                >
                  Pick from DLQ
                </button>
              </div>

              {tab === "paste" && (
                <TextArea rows={14} value={sampleText} onChange={(e) => setSampleText(e.target.value)} />
              )}
              {tab === "pick" && (
                <Button className="ml-2" variant="secondary" onClick={pickMessage}>Open picker</Button>
              )}
            </div>
          </div>

          <div className="rounded-2xl border border-white/10 bg-[#0F162B]">
            <div className="flex items-center justify-between border-b border-white/10 p-4">
              <h2 className="text-base font-semibold">Preview</h2>
              <Button variant="ghost" onClick={() => setShowDiff((v) => !v)}>
                {showDiff ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />} {showDiff ? "Hide diff" : "Show diff"}
              </Button>
            </div>
            <div className="p-4">
              {!hasPreviewed && (
                <div className="rounded-lg border border-dashed border-white/20 p-6 text-center text-sm text-slate-400">
                  Run a preview to see redacted output and diff.
                </div>
              )}

              {hasPreviewed && (
                <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                  <div>
                    <label className="text-sm font-medium text-slate-300">Original</label>
                    <div className="mt-2 rounded-xl border border-white/10 bg-black/20 p-3">
                      {sampleObj ? (
                        <ReactJson
                          src={sampleObj}
                          name={false}
                          collapsed={2}
                          enableClipboard={false}
                          displayDataTypes={false}
                          theme="monokai"
                          style={{ backgroundColor: 'transparent' }}
                        />
                      ) : (
                        <div className="text-sm text-slate-400">Invalid JSON</div>
                      )}
                    </div>
                  </div>
                  <div>
                    <label className="text-sm font-medium text-slate-300">Redacted</label>
                    <div className="mt-2 rounded-xl border border-white/10 bg-black/20 p-3">
                      {redacted ? (
                        <ReactJson
                          src={redacted}
                          name={false}
                          collapsed={2}
                          enableClipboard={false}
                          displayDataTypes={false}
                          theme="monokai"
                          style={{ backgroundColor: 'transparent' }}
                        />
                      ) : (
                        <div className="text-sm text-slate-400">No preview yet</div>
                      )}
                    </div>
                  </div>
                </div>
              )}

              {hasPreviewed && showDiff && diff && (
                <div className="mt-4">
                  <label className="text-sm font-medium text-slate-300">Diff</label>
                  <div className="mt-2 overflow-auto rounded-xl border border-white/10 bg-black/20 p-3">
                    <pre className="text-xs leading-relaxed text-slate-200">{JSON.stringify(diff, null, 2)}</pre>
                  </div>
                </div>
              )}

              {hasPreviewed && (
                <div className="mt-4">
                  <label className="text-sm font-medium text-slate-300">Audit</label>
                  <div className="mt-2 overflow-auto rounded-xl border border-white/10">
                    <table className="w-full text-left text-sm">
                      <thead className="sticky top-0 z-10 bg-[#0F162B]">
                        <tr className="border-b border-white/10">
                          <th className="px-3 py-2 text-xs font-medium text-slate-400">Action</th>
                          <th className="px-3 py-2 text-xs font-medium text-slate-400">JSONPath</th>
                          <th className="px-3 py-2 text-xs font-medium text-slate-400">Matches</th>
                          <th className="px-3 py-2 text-xs font-medium text-slate-400">Note</th>
                          <th className="px-3 py-2 text-xs font-medium text-slate-400">Error</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-white/5">
                        {audit.map((a, i) => (
                          <tr key={i} className="hover:bg-white/5">
                            <td className="px-3 py-2">
                              <Badge tone={a.action === "REMOVE" ? "danger" : "secondary"}>{a.action}</Badge>
                            </td>
                            <td className="px-3 py-2 font-mono text-slate-200">{a.path}</td>
                            <td className="px-3 py-2">{a.count}</td>
                            <td className="px-3 py-2 text-slate-400">{a.note ?? ""}</td>
                            <td className="px-3 py-2 text-rose-300">{a.error ?? ""}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="mt-8 rounded-xl border border-white/10 bg-white/5 p-4 text-sm text-slate-300">
        <p className="mb-2 font-medium text-slate-200">Implementation notes</p>
        <ul className="list-inside list-disc space-y-1">
          <li>Preview uses a fast, non-cryptographic hash (FNV-1a). Backend should apply HMAC-SHA256 at persist/replay time.</li>
          <li>Use server-side validation for JSONPath if you need role-based checks or schema-aware guards.</li>
          <li>For Avro/Protobuf, preview can be disabled or backed by server deserialization with schemas.</li>
          <li>Never log raw payloads when previewing; use the <code>audit</code> table above to trace applied rules.</li>
        </ul>
      </div>
    </div>
  );
}

export default RedactionStudio;
