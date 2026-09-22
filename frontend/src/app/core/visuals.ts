/** Demo products have no photos: each category gets an icon and a gradient instead. */
const VISUALS: Record<string, { icon: string; from: string; to: string }> = {
  Electronics: { icon: '🎧', from: '#6366f1', to: '#22d3ee' },
  'Home & Kitchen': { icon: '🍳', from: '#f97316', to: '#facc15' },
  Fashion: { icon: '👟', from: '#ec4899', to: '#a855f7' },
  'Sports & Outdoors': { icon: '🏕️', from: '#10b981', to: '#84cc16' },
  'Beauty & Health': { icon: '🧴', from: '#f43f5e', to: '#fb923c' },
  'Books & Office': { icon: '📚', from: '#0ea5e9', to: '#6366f1' },
};

export function visualFor(category: string): { icon: string; background: string } {
  const v = VISUALS[category] ?? { icon: '📦', from: '#64748b', to: '#94a3b8' };
  return { icon: v.icon, background: `linear-gradient(135deg, ${v.from}, ${v.to})` };
}
