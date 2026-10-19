export function normalizeVoiceText(
  text: string,
  map?: Record<string, string>,
  wakeWords?: string[],
  name?: string,
): string;
export function assessSpeechAlternatives(
  alternatives: unknown,
  normalize?: (text: string) => string,
): { text: string; needsReview: boolean };
