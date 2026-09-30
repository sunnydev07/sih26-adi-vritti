/**
 * Mobile theme — reads the shared @adi-vritti/ui-tokens package.
 * No hardcoded colors/words here; change tokens.json and both apps follow.
 */
import tokens from "@adi-vritti/ui-tokens";

export const colors = {
  primary: tokens.brand.primary,
  primaryStrong: tokens.brand.primaryStrong,
  primarySoft: tokens.brand.primarySoft,
  accent: tokens.brand.accent,
  ink: tokens.brand.ink,
  muted: tokens.brand.muted,
  surface: tokens.brand.surface,
  page: tokens.brand.page,
  border: tokens.brand.border,
} as const;

export type Tone = keyof typeof tokens.status;

export function toneColors(tone: Tone): { color: string; bg: string; ink: string } {
  return tokens.status[tone] ?? tokens.status.info;
}

export const radius = {
  card: tokens.radius.card,
  pill: tokens.radius.pill,
  control: tokens.radius.control,
} as const;

export const type = {
  hero: tokens.type.hero,
  title: tokens.type.title,
  body: tokens.type.body,
  caption: tokens.type.caption,
  tiny: tokens.type.tiny,
} as const;

export const spacing = {
  screen: tokens.layout.screenPadding,
  card: tokens.layout.cardPadding,
  minTouch: tokens.touch.minTarget,
} as const;

export const assistantName: string = tokens.assistant.name;
export const assistantSuggestions: string[] = tokens.assistant.suggestions;
