// Browser ASR is fallible: retain alternatives and ask before using an uncertain device ID.
export function normalizeVoiceText(
  raw,
  characterMap = {},
  wakeWords = ['小薇', '小微', '小維'],
  name = '小维',
) {
  let text = [...raw].map((c) => characterMap[c] || c).join('');
  wakeWords.forEach((word) => {
    text = text.replaceAll(word, name);
  });
  return text
    .replace(/小\s*[薇微維维]/g, name)
    .replace(/[Ａ-Ｅａ-ｅ０-９]/g, (c) => String.fromCharCode(c.charCodeAt(0) - 65248))
    .replace(/(?:诶|欸|哎)\s*(?=[零〇0][一二两三四五六七八九1-9])/g, 'A')
    .replace(/〇/g, '零');
}
export function cabinetSceneModelKeyFromSpeech(text) {
  const m = text
    .toUpperCase()
    .match(/([A-E])\s*(?:区)?\s*(?:第)?\s*[-—－]?\s*([0-9]+|[零一二两三四五六七八九十]+)/);
  if (!m) return null;
  const digits = { 零: 0, 一: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9 };
  let n = m[2];
  if (!/^\d+$/.test(n)) {
    if (n.includes('十')) {
      const [a, b] = n.split('十');
      n = (a ? digits[a] : 1) * 10 + (b ? digits[b] : 0);
    } else n = [...n].map((c) => digits[c]).join('');
  }
  return `${m[1]}-${String(Number(n)).padStart(2, '0')}`;
}
export function assessSpeechAlternatives(alternatives, normalize = normalizeVoiceText) {
  const candidates = Array.from(alternatives, (a) => ({
    text: normalize(a.transcript.trim()),
    confidence: a.confidence,
  }));
  const first = candidates[0];
  if (!first) return { text: '', needsReview: true, reason: 'empty' };
  // A confidence of zero is treated as unavailable, as some engines never supply it.
  const low = Number.isFinite(first.confidence) && first.confidence > 0 && first.confidence < 0.55;
  const id = cabinetSceneModelKeyFromSpeech(first.text);
  const disagreement = candidates.slice(1).some((c) => {
    const other = cabinetSceneModelKeyFromSpeech(c.text);
    return (
      other &&
      other !== id &&
      (!c.confidence || !first.confidence || first.confidence - c.confidence < 0.18)
    );
  });
  return {
    text: first.text,
    needsReview: low || disagreement,
    reason: disagreement ? 'device' : low ? 'confidence' : null,
  };
}
