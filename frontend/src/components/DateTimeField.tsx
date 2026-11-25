import { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Icon } from './NapaUI';
import { fonts, palette } from './theme';

type Props = {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  mode: 'date' | 'time' | 'datetime';
  editable?: boolean;
};
const pad = (n: number) => String(n).padStart(2, '0');
const dateValue = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const timeValue = (d: Date) => `${pad(d.getHours())}:${pad(d.getMinutes())}`;

function initialDate(value: string, mode: Props['mode']) {
  const now = new Date();
  const match = mode === 'time' ? /^(\d{2}):(\d{2})$/.exec(value) : /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?$/.exec(value);
  if (!match) return now;
  if (mode === 'time') now.setHours(Number(match[1]), Number(match[2]), 0, 0);
  else now.setFullYear(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  if (mode === 'datetime') now.setHours(Number(match[4] ?? 12), Number(match[5] ?? 0), 0, 0);
  return now;
}

/** Selector local: no convierte la fecha elegida a UTC ni necesita permisos del dispositivo. */
export function DateTimeField({ label, value, onChangeText, mode, editable = true }: Props) {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState(() => new Date());
  const [month, setMonth] = useState(() => new Date());
  const year = month.getFullYear();
  const monthNumber = month.getMonth();
  const offset = (new Date(year, monthNumber, 1).getDay() + 6) % 7;
  const days = new Date(year, monthNumber + 1, 0).getDate();
  const formatted = !value ? 'Seleccionar' : mode === 'time' ? value
    : initialDate(value, mode).toLocaleString('es-CO', mode === 'date'
      ? { day: 'numeric', month: 'short', year: 'numeric' }
      : { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

  function show() {
    const date = initialDate(value, mode);
    setSelected(date);
    setMonth(new Date(date.getFullYear(), date.getMonth(), 1));
    setOpen(true);
  }
  function clockPart(part: 'hours' | 'minutes', count: number) {
    return (
      <View style={styles.clockColumn}>
        <Text style={styles.clockLabel}>{part === 'hours' ? 'Hora' : 'Minutos'}</Text>
        <ScrollView style={styles.clockList} nestedScrollEnabled
          contentOffset={{ x: 0, y: (part === 'hours' ? selected.getHours() : selected.getMinutes()) * 44 }}>
          {Array.from({ length: count }, (_, n) => {
            const active = (part === 'hours' ? selected.getHours() : selected.getMinutes()) === n;
            return (
              <Pressable key={n} accessibilityRole="button"
                accessibilityLabel={`Elegir ${part === 'hours' ? 'hora' : 'minuto'} ${pad(n)}`}
                accessibilityState={{ selected: active }}
                onPress={() => {
                  const next = new Date(selected);
                  if (part === 'hours') next.setHours(n); else next.setMinutes(n);
                  setSelected(next);
                }} style={[styles.clockOption, active && styles.selected]}>
                <Text style={[styles.optionText, active && styles.selectedText]}>{pad(n)}</Text>
              </Pressable>
            );
          })}
        </ScrollView>
      </View>
    );
  }

  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <Pressable accessibilityRole="button" accessibilityLabel={label}
        accessibilityValue={{ text: formatted }} disabled={!editable} onPress={show}
        style={({ pressed }) => [styles.trigger, pressed && { borderColor: palette.coral }, !editable && { opacity: 0.5 }]}>
        <Icon name={mode === 'time' ? 'clock' : 'calendar'} color={palette.coralDark} size={20} />
        <Text style={[styles.value, !value && { color: palette.muted }]}>{formatted}</Text>
        <Icon name="chevron-down" color={palette.muted} size={16} />
      </Pressable>
      {open ? (
        <Modal transparent visible animationType="fade" onRequestClose={() => setOpen(false)}>
          <View style={styles.backdrop}>
            <View style={styles.dialog} accessibilityViewIsModal>
              <Text accessibilityRole="header" style={styles.title}>{label}</Text>
              <ScrollView contentContainerStyle={styles.dialogContent}>
                {mode !== 'time' ? (
                  <View>
                    <View style={styles.monthHeader}>
                      <Pressable accessibilityRole="button" accessibilityLabel="Mes anterior" style={styles.monthArrow}
                        onPress={() => setMonth(new Date(year, monthNumber - 1, 1))}><Icon name="chevron-left" /></Pressable>
                      <Text style={styles.monthTitle}>{month.toLocaleDateString('es-CO', { month: 'long', year: 'numeric' })}</Text>
                      <Pressable accessibilityRole="button" accessibilityLabel="Mes siguiente" style={styles.monthArrow}
                        onPress={() => setMonth(new Date(year, monthNumber + 1, 1))}><Icon name="chevron-right" /></Pressable>
                    </View>
                    <View style={styles.grid}>
                      {['L', 'M', 'X', 'J', 'V', 'S', 'D'].map((day) => <Text key={day} style={styles.weekday}>{day}</Text>)}
                      {Array.from({ length: offset }, (_, n) => <View key={`empty-${n}`} style={styles.dayCell} />)}
                      {Array.from({ length: days }, (_, i) => {
                        const day = new Date(year, monthNumber, i + 1);
                        const active = dateValue(day) === dateValue(selected);
                        return (
                          <View key={i} style={styles.dayCell}>
                            <Pressable accessibilityRole="button" accessibilityLabel={`Elegir día ${dateValue(day)}`}
                              accessibilityState={{ selected: active }}
                              onPress={() => {
                                const next = new Date(selected);
                                next.setFullYear(year, monthNumber, i + 1);
                                setSelected(next);
                              }} style={[styles.day, active && styles.selected]}>
                              <Text style={[styles.optionText, active && styles.selectedText]}>{i + 1}</Text>
                            </Pressable>
                          </View>
                        );
                      })}
                    </View>
                  </View>
                ) : null}
                {mode !== 'date' ? (
                  <View>
                    <Text style={styles.clockSummary}>{timeValue(selected)} · formato de 24 horas</Text>
                    <View style={styles.clock}>{clockPart('hours', 24)}{clockPart('minutes', 60)}</View>
                  </View>
                ) : null}
              </ScrollView>
              <View style={styles.buttons}>
                <Pressable accessibilityRole="button" accessibilityLabel="Cancelar selección" onPress={() => setOpen(false)} style={styles.cancel}>
                  <Text style={styles.optionText}>Cancelar</Text>
                </Pressable>
                <Pressable accessibilityRole="button" accessibilityLabel="Aplicar selección" style={styles.apply}
                  onPress={() => {
                    onChangeText(mode === 'date' ? dateValue(selected) : mode === 'time' ? timeValue(selected) : `${dateValue(selected)}T${timeValue(selected)}`);
                    setOpen(false);
                  }}><Text style={styles.selectedText}>Aplicar</Text></Pressable>
              </View>
              {mode === 'date' && value ? (
                <Pressable accessibilityRole="button" accessibilityLabel="Quitar fecha"
                  onPress={() => { onChangeText(''); setOpen(false); }} style={styles.clear}>
                  <Text style={styles.optionText}>Quitar fecha</Text>
                </Pressable>
              ) : null}
            </View>
          </View>
        </Modal>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  field: { marginBottom: 18, minWidth: 0 },
  label: { fontFamily: fonts.sans, color: palette.dark, fontSize: 14, fontWeight: '700', marginBottom: 8 },
  trigger: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 13, minHeight: 52, borderRadius: 12, borderWidth: 1, borderColor: palette.border, backgroundColor: palette.white },
  value: { flex: 1, fontFamily: fonts.sans, fontSize: 14, color: palette.dark },
  backdrop: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#20132499', padding: 16 },
  dialog: { width: '100%', maxWidth: 440, maxHeight: '90%', backgroundColor: palette.background, borderRadius: 22, padding: 16 },
  title: { fontFamily: fonts.display, fontSize: 22, color: palette.dark, marginBottom: 10 },
  dialogContent: { paddingBottom: 8 },
  monthHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  monthArrow: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  monthTitle: { flex: 1, textAlign: 'center', textTransform: 'capitalize', fontFamily: fonts.sans, fontWeight: '700', color: palette.dark },
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  weekday: { width: '14.285714%', textAlign: 'center', color: palette.muted, fontFamily: fonts.sans, paddingVertical: 8 },
  dayCell: { width: '14.285714%', height: 44, padding: 1 },
  day: { flex: 1, alignItems: 'center', justifyContent: 'center', borderRadius: 12 },
  selected: { backgroundColor: palette.coral },
  optionText: { fontFamily: fonts.sans, fontSize: 15, color: palette.dark },
  selectedText: { fontFamily: fonts.sans, fontWeight: '700', fontSize: 15, color: '#FFFFFF' },
  clockSummary: { textAlign: 'center', fontFamily: fonts.sans, fontSize: 14, color: palette.coralDark, fontWeight: '700', marginVertical: 12 },
  clock: { flexDirection: 'row', gap: 16 },
  clockColumn: { flex: 1 },
  clockLabel: { fontFamily: fonts.sans, fontSize: 13, fontWeight: '700', color: palette.muted, textAlign: 'center', marginBottom: 6 },
  clockList: { height: 132, borderRadius: 12, backgroundColor: palette.white, borderWidth: 1, borderColor: palette.border },
  clockOption: { height: 44, justifyContent: 'center', alignItems: 'center', borderRadius: 10 },
  buttons: { flexDirection: 'row', gap: 12, marginTop: 12 },
  cancel: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 12, borderWidth: 1, borderColor: palette.border },
  apply: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 12, backgroundColor: palette.coral },
  clear: { minHeight: 44, justifyContent: 'center', alignItems: 'center', marginTop: 4 },
});
