import type { PropsWithChildren, ReactNode } from 'react';
import {
  Image,
  Pressable,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
  type ImageSourcePropType,
  type StyleProp,
  type ViewStyle,
} from 'react-native';

import glyphs from '../../assets/fonts/Feather.json';
import { DESKTOP_BREAKPOINT, art, fonts, palette } from './theme';

export type IconName = keyof typeof glyphs;
export function Icon({
  name,
  size = 20,
  color = palette.dark,
}: {
  name: IconName;
  size?: number;
  color?: string;
}) {
  return (
    <Text
      accessible={false}
      aria-hidden
      allowFontScaling={false}
      style={{
        fontFamily: fonts.icons,
        fontWeight: 'normal',
        fontStyle: 'normal',
        fontSize: size,
        lineHeight: Math.ceil(size * 1.4),
        minWidth: size + 6,
        paddingHorizontal: 2,
        includeFontPadding: true,
        textAlign: 'center',
        textAlignVertical: 'center',
        flexShrink: 0,
        color,
      }}
    >
      {String.fromCodePoint(glyphs[name])}
    </Text>
  );
}

export function useContentWidth() {
  const { width } = useWindowDimensions();
  return width - (width >= DESKTOP_BREAKPOINT ? 244 + 64 : 36);
}

export function CardGrid({ children }: PropsWithChildren) {
  return <View style={styles.grid}>{children}</View>;
}

export function GridCard({ children, style }: PropsWithChildren<{ style?: StyleProp<ViewStyle> }>) {
  const width = useContentWidth();
  const columns = width >= 1050 ? 3 : width >= 620 ? 2 : 1;
  return <View style={[{ width: `${100 / columns}%`, padding: 8 }, style]}>{children}</View>;
}

export function ShortcutCard({
  title,
  description,
  icon,
  onPress,
}: {
  title: string;
  description: string;
  icon: IconName;
  onPress: () => void;
}) {
  return (
    <GridCard>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={title}
        onPress={onPress}
        style={({ pressed }) => [styles.shortcut, pressed && { opacity: 0.7 }]}
      >
        <View style={styles.shortcutIcon}>
          <Icon name={icon} color={palette.coralDark} size={24} />
        </View>
        <Text style={styles.shortcutTitle}>{title}</Text>
        <Text style={styles.shortcutDescription}>{description}</Text>
        <View style={{ alignSelf: 'flex-end', marginTop: 10 }}>
          <Icon name="arrow-up-right" color={palette.coralDark} />
        </View>
      </Pressable>
    </GridCard>
  );
}

export function FilterPanel({ children }: PropsWithChildren) {
  return <View style={styles.filterPanel}>{children}</View>;
}

export function FilterRow({ label, children }: PropsWithChildren<{ label: string }>) {
  const wide = useContentWidth() > 750;
  return (
    <View style={[styles.filterRow, wide && styles.filterRowWide]}>
      <Text style={[styles.filterLabel, wide && { width: 130 }]}>{label}</Text>
      <View style={styles.filterOptions}>{children}</View>
    </View>
  );
}

export function StatusBadge({
  label,
  tone = 'sage',
}: {
  label: string;
  tone?: 'sage' | 'peach' | 'muted' | 'lavender';
}) {
  const backgroundColor = {
    sage: palette.sage,
    peach: palette.peach,
    muted: '#EEE8E2',
    lavender: palette.lavender,
  }[tone];
  const dot = { sage: '#7A9B33', peach: '#D68429', muted: '#A89C93', lavender: '#8F70A3' }[tone];
  return (
    <View style={[styles.badge, { backgroundColor }]}>
      <View style={[styles.dot, { backgroundColor: dot }]} />
      <Text style={styles.badgeText}>{label}</Text>
    </View>
  );
}

export function DetailLine({ icon, children }: PropsWithChildren<{ icon: IconName }>) {
  return (
    <View style={styles.detailLine}>
      <Icon name={icon} size={18} />
      <Text style={styles.detailText}>{children}</Text>
    </View>
  );
}

export function ActionLink({
  label,
  accessibilityLabel,
  onPress,
  outline = false,
}: {
  label: string;
  accessibilityLabel?: string;
  onPress: () => void;
  outline?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      onPress={onPress}
      style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
        styles.action,
        outline && styles.actionOutline,
        hovered && { opacity: 0.88 },
        pressed && { opacity: 0.7 },
      ]}
    >
      <Text style={[styles.actionLabel, outline && { color: palette.coralDark }]}>{label}</Text>
      <Icon name="arrow-right" color={outline ? palette.coralDark : '#FFFFFF'} size={21} />
    </Pressable>
  );
}

export function Illustration({
  source,
  style,
}: {
  source: ImageSourcePropType;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <View style={[styles.illustration, style]}>
      <Image
        source={source}
        resizeMode="cover"
        style={{ width: '100%', height: '100%' }}
        accessible={false}
      />
    </View>
  );
}

export function RefreshButton({
  onPress,
  disabled = false,
  label = 'Actualizar',
}: {
  onPress: () => void;
  disabled?: boolean;
  label?: string;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ hovered }: { pressed: boolean; hovered?: boolean }) => [
        styles.refresh,
        hovered && { backgroundColor: palette.peach },
        disabled && { opacity: 0.4 },
      ]}
    >
      <Icon name="refresh-cw" color={palette.coralDark} size={18} />
      <Text style={styles.refreshLabel}>{label}</Text>
    </Pressable>
  );
}

export function EmptyState({
  title,
  description,
  icon = 'inbox',
}: {
  title: string;
  description?: string;
  icon?: IconName;
}) {
  return (
    <View style={styles.empty}>
      <View style={styles.emptyIcon}>
        <Icon name={icon} size={29} color={palette.coral} />
      </View>
      <Text style={styles.emptyTitle}>{title}</Text>
      {description ? <Text style={styles.emptyDescription}>{description}</Text> : null}
    </View>
  );
}

export const heroContent: Record<
  string,
  { title: string; description: string; image: ImageSourcePropType }
> = {
  '/emprendimientos': {
    title: 'Tu barrio tiene\nmucho que ofrecer.',
    description: 'Descubre emprendimientos cercanos y apoya el talento local.',
    image: art.cliente,
  },
  '/pedidos-vendedor': {
    title: 'Tu negocio crece\ncon cada pedido.',
    description: 'Organiza tus pedidos y sigue compartiendo lo mejor de tu negocio.',
    image: art.vendedor,
  },
  '/domicilios-disponibles': {
    title: 'Conecta tu barrio,\nentrega a entrega.',
    description: 'Elige un domicilio y conoce cuánto ganarás antes de aceptarlo.',
    image: art.domiciliario,
  },
  '/domicilios-admin': {
    title: 'Todo en orden,\nbarrio en movimiento.',
    description: 'Supervisa los domicilios y acompaña a la comunidad.',
    image: art.administrador,
  },
};

export function Hero({ content }: { content: (typeof heroContent)[string] }) {
  const width = useContentWidth();
  const wide = width >= 880;
  return (
    <View style={[styles.hero, !wide && styles.heroSmall]}>
      <View style={[styles.heroShape, !wide && { width: 220, height: 160, right: -50 }]} />
      <Image
        source={content.image}
        resizeMode="contain"
        accessible={false}
        style={[styles.heroImage, !wide && styles.heroImageSmall]}
      />
      <View style={[styles.heroCopy, !wide && { width: '100%' }]}>
        <Text
          accessibilityRole="header"
          style={[
            styles.heroTitle,
            {
              fontSize: wide ? (width > 1150 ? 53 : 43) : width < 330 ? 28 : 34,
              lineHeight: wide ? (width > 1150 ? 57 : 49) : width < 330 ? 33 : 39,
            },
          ]}
        >
          {content.title}
        </Text>
        <Text style={[styles.heroDescription, !wide && { maxWidth: '78%', fontSize: 14 }]}>
          {content.description}
        </Text>
      </View>
    </View>
  );
}

export function SectionHeading({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  const compact = useContentWidth() < 600;
  return (
    <View style={[styles.section, compact && { flexDirection: 'column', alignItems: 'stretch' }]}>
      <View style={{ flex: 1 }}>
        <Text accessibilityRole="header" style={styles.sectionTitle}>
          {title}
        </Text>
        {description ? <Text style={styles.sectionDescription}>{description}</Text> : null}
      </View>
      {action ? <View style={compact && { alignSelf: 'flex-start' }}>{action}</View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', marginHorizontal: -8, marginTop: 8 },
  shortcut: {
    flex: 1,
    padding: 22,
    borderWidth: 1,
    borderColor: palette.border,
    borderRadius: 18,
    backgroundColor: palette.white,
  },
  shortcutIcon: {
    backgroundColor: '#FCE5D3',
    width: 48,
    height: 48,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
  },
  shortcutTitle: {
    fontFamily: fonts.display,
    fontWeight: '700',
    fontSize: 22,
    lineHeight: 28,
    color: palette.dark,
  },
  shortcutDescription: {
    fontFamily: fonts.sans,
    fontSize: 13,
    lineHeight: 20,
    color: palette.muted,
    marginTop: 6,
  },
  filterPanel: {
    borderWidth: 1,
    borderColor: palette.border,
    borderRadius: 18,
    paddingHorizontal: 20,
    paddingVertical: 10,
    marginVertical: 12,
    backgroundColor: '#FFFCF780',
    gap: 8,
  },
  filterRow: { gap: 10, paddingVertical: 6 },
  filterRowWide: { flexDirection: 'row', alignItems: 'center' },
  filterLabel: { color: palette.dark, fontFamily: fonts.sans, fontSize: 15, fontWeight: '700' },
  filterOptions: { flex: 1, flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 6 },
  badge: {
    alignSelf: 'flex-start',
    maxWidth: '100%',
    borderRadius: 30,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    paddingHorizontal: 12,
    paddingVertical: 5,
  },
  badgeText: {
    color: palette.dark,
    fontFamily: fonts.sans,
    fontWeight: '700',
    fontSize: 12,
    flexShrink: 1,
  },
  dot: { height: 7, width: 7, borderRadius: 4 },
  detailLine: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, marginTop: 9 },
  detailText: {
    flex: 1,
    color: palette.dark,
    fontFamily: fonts.sans,
    fontSize: 14,
    lineHeight: 21,
  },
  action: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 13,
    backgroundColor: palette.coral,
    borderRadius: 15,
    minHeight: 44,
    paddingHorizontal: 20,
    paddingVertical: 10,
    marginTop: 16,
  },
  actionOutline: { backgroundColor: 'transparent', borderWidth: 1, borderColor: palette.coral },
  actionLabel: { color: '#FFFFFF', fontFamily: fonts.sans, fontWeight: '700', fontSize: 15 },
  refresh: {
    borderWidth: 1,
    borderColor: palette.coral,
    borderRadius: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingHorizontal: 16,
    minHeight: 41,
    paddingVertical: 8,
  },
  refreshLabel: {
    color: palette.coralDark,
    fontFamily: fonts.sans,
    fontWeight: '700',
    fontSize: 13,
  },
  illustration: { height: 145, overflow: 'hidden', backgroundColor: palette.peach },
  empty: {
    padding: 32,
    alignItems: 'center',
    backgroundColor: palette.white,
    borderWidth: 1,
    borderColor: palette.border,
    borderRadius: 20,
    marginVertical: 18,
  },
  emptyIcon: {
    width: 64,
    height: 64,
    backgroundColor: palette.peach,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  emptyTitle: {
    fontFamily: fonts.display,
    fontWeight: '700',
    fontSize: 23,
    color: palette.dark,
    textAlign: 'center',
  },
  emptyDescription: {
    fontFamily: fonts.sans,
    fontSize: 14,
    color: palette.muted,
    lineHeight: 22,
    maxWidth: 380,
    textAlign: 'center',
    marginTop: 7,
  },
  hero: {
    minHeight: 244,
    justifyContent: 'center',
    position: 'relative',
    marginTop: 5,
    marginBottom: 6,
    overflow: 'hidden',
  },
  heroSmall: {
    minHeight: 220,
    backgroundColor: '#F8EADC',
    borderRadius: 22,
    padding: 20,
    marginTop: 16,
    marginBottom: 22,
  },
  heroShape: {
    position: 'absolute',
    right: 20,
    bottom: 12,
    height: 205,
    width: 410,
    backgroundColor: '#F9DDC2',
    borderRadius: 130,
  },
  heroImage: { position: 'absolute', width: '47%', height: 255, right: -10, bottom: -4 },
  heroImageSmall: { width: 230, height: 180, right: -68, bottom: -8, opacity: 0.27 },
  heroCopy: { width: '59%', paddingVertical: 22, zIndex: 1 },
  heroTitle: {
    fontFamily: fonts.display,
    fontWeight: '800',
    color: palette.dark,
    letterSpacing: -1.5,
  },
  heroDescription: {
    fontFamily: fonts.sans,
    color: palette.dark,
    fontSize: 16,
    lineHeight: 25,
    marginTop: 12,
    maxWidth: 570,
  },
  section: { flexDirection: 'row', alignItems: 'center', gap: 16, marginBottom: 4, marginTop: 12 },
  sectionTitle: {
    fontFamily: fonts.display,
    fontWeight: '700',
    color: palette.dark,
    fontSize: 29,
    lineHeight: 36,
    letterSpacing: -0.7,
  },
  sectionDescription: {
    fontFamily: fonts.sans,
    fontSize: 15,
    color: palette.muted,
    lineHeight: 22,
    marginTop: 4,
  },
});
