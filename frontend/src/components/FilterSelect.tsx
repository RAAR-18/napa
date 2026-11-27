import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Icon } from './NapaUI';
import { fonts, palette } from './theme';

export function FilterSelect<T extends string>({ label, value, options, expanded, onToggle, onSelect }: {
  label: string;
  value: T;
  options: readonly { value: T; label: string }[];
  expanded: boolean;
  onToggle: () => void;
  onSelect: (value: T) => void;
}) {
  const selected = options.find((option) => option.value === value);
  return (
    <View style={styles.container}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${label}: ${selected?.label ?? ''}`}
        accessibilityHint="Abre o cierra las opciones del filtro"
        accessibilityState={{ expanded }}
        onPress={onToggle}
        style={({ pressed }) => [styles.trigger, expanded && styles.triggerOpen, pressed && { opacity: 0.8 }]}
      >
        <Text style={styles.label}>{selected?.label}</Text>
        <Icon name={expanded ? 'chevron-up' : 'chevron-down'} color={palette.plum} size={18} />
      </Pressable>
      {expanded ? (
        <View accessibilityRole="radiogroup" accessibilityLabel={`Opciones de ${label}`} style={styles.menu}>
          {options.map((option) => {
            const active = option.value === value;
            return (
              <Pressable
                key={option.value}
                accessibilityRole="radio"
                accessibilityLabel={option.label}
                accessibilityState={{ checked: active }}
                onPress={() => onSelect(option.value)}
                style={({ pressed }) => [styles.option, active && styles.selected, pressed && { opacity: 0.75 }]}
              >
                <Text style={[styles.optionLabel, active && styles.selectedLabel]}>{option.label}</Text>
                {active ? <Icon name="check" size={17} color={palette.white} /> : null}
              </Pressable>
            );
          })}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { width: '100%', maxWidth: 440, minWidth: 0 },
  trigger: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12,
    minHeight: 46, paddingHorizontal: 15, paddingVertical: 9,
    backgroundColor: palette.white, borderColor: palette.border, borderWidth: 1, borderRadius: 12,
  },
  triggerOpen: { borderColor: palette.plum, backgroundColor: '#F3EAF0' },
  label: { flex: 1, fontFamily: fonts.sans, fontSize: 14, lineHeight: 20, fontWeight: '700', color: palette.plum },
  menu: { marginTop: 6, padding: 5, gap: 3, borderWidth: 1, borderColor: palette.border, borderRadius: 13, backgroundColor: palette.white },
  option: { minHeight: 44, flexDirection: 'row', alignItems: 'center', gap: 8, borderRadius: 9, paddingHorizontal: 12, paddingVertical: 9 },
  selected: { backgroundColor: palette.plum },
  optionLabel: { flex: 1, fontFamily: fonts.sans, fontSize: 14, lineHeight: 20, color: palette.dark },
  selectedLabel: { color: palette.white, fontWeight: '700' },
});
