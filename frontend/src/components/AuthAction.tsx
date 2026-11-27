import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Icon, type IconName } from './NapaUI';
import { fonts, palette } from './theme';

export function AuthAction({ title, icon, onPress }: { title: string; icon?: IconName; onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={onPress}
      style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
        styles.action,
        hovered && styles.actionHover,
        pressed && { opacity: 0.75 },
      ]}
    >
      {icon ? <View style={styles.actionIcon}><Icon name={icon} color={palette.coralDark} size={17} /></View> : null}
      <Text style={styles.actionLabel}>{title}</Text>
      <Icon name="chevron-right" color={palette.coralDark} size={15} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  action: {
    flexDirection: 'row', alignItems: 'center', alignSelf: 'center', maxWidth: '100%', gap: 8,
    minHeight: 44, paddingHorizontal: 11, paddingVertical: 7,
    borderWidth: 1, borderColor: '#F3BDA9', borderRadius: 12, backgroundColor: '#FFF1E6',
  },
  actionHover: { backgroundColor: '#FFE5D3', borderColor: palette.coral },
  actionIcon: {
    width: 27, height: 27, borderRadius: 8, backgroundColor: '#FFE1CB',
    alignItems: 'center', justifyContent: 'center',
  },
  actionLabel: { flexShrink: 1, textAlign: 'center', fontFamily: fonts.sans, fontSize: 13, lineHeight: 18, fontWeight: '700', color: palette.coralDark },
});
