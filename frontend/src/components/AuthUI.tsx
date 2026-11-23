import { useState, type PropsWithChildren, type ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  type TextInputProps,
} from 'react-native';

import { AppShell, Brand, useNapaNavigation } from './AppShell';
import { Hero, SectionHeading, heroContent } from './NapaUI';
import { fonts, palette } from './theme';
import { Toast } from './Toast';

export { palette } from './theme';

export function AuthPage({ children }: PropsWithChildren) {
  return <AppShell>{children}</AppShell>;
}

export function PageHeading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  const { path } = useNapaNavigation();
  const hero = heroContent[path];
  if (hero)
    return (
      <>
        <Hero content={hero} />
        <SectionHeading title={title} description={description} action={action} />
      </>
    );
  return (
    <View style={styles.heading}>
      {!path || ['/', '/login', '/register', '/verify', '/recover'].includes(path) ? (
        <View style={styles.brand}>
          <Brand />
        </View>
      ) : null}
      <Text style={styles.eyebrow}>{eyebrow.toUpperCase()}</Text>
      <Text accessibilityRole="header" style={styles.title}>
        {title}
      </Text>
      {description ? <Text style={styles.description}>{description}</Text> : null}
      {action ? <View style={{ alignSelf: 'flex-start', marginTop: 14 }}>{action}</View> : null}
    </View>
  );
}

export function Field({
  label,
  style,
  onFocus,
  onBlur,
  ...props
}: TextInputProps & { label: string }) {
  const [focused, setFocused] = useState(false);
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor="#A29289"
        {...props}
        onFocus={(event) => {
          setFocused(true);
          onFocus?.(event);
        }}
        onBlur={(event) => {
          setFocused(false);
          onBlur?.(event);
        }}
        style={[styles.input, focused && styles.inputFocused, style]}
      />
    </View>
  );
}

export function PrimaryButton({
  title,
  onPress,
  busy = false,
  disabled = false,
}: {
  title: string;
  onPress: () => void;
  busy?: boolean;
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityLabel={title}
      accessibilityRole="button"
      accessibilityState={{ disabled: disabled || busy, busy }}
      disabled={disabled || busy}
      onPress={onPress}
      style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
        styles.primaryButton,
        hovered && styles.primaryButtonHover,
        pressed && { opacity: 0.8 },
        (disabled || busy) && styles.primaryButtonMuted,
      ]}
    >
      {busy ? (
        <ActivityIndicator color="#FFFFFF" />
      ) : (
        <Text style={styles.primaryButtonText}>{title}</Text>
      )}
    </Pressable>
  );
}

export function TextButton({
  title,
  onPress,
  disabled = false,
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityLabel={title}
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ hovered }: { pressed: boolean; hovered?: boolean }) => [
        styles.textButton,
        hovered && { backgroundColor: '#F2E7DE' },
      ]}
    >
      <Text style={[styles.textButtonLabel, disabled && styles.disabledText]}>{title}</Text>
    </Pressable>
  );
}

/** Los errores se muestran como notificación arriba de la pantalla (ver Toast). */
export function ErrorBanner({ message }: { message: string | null }) {
  return <Toast message={message} tone="error" />;
}

/** Aviso de éxito con la misma notificación. */
export function SuccessNotice({ message }: { message: string | null }) {
  return <Toast message={message} tone="success" />;
}

export function HelperText({ children }: PropsWithChildren) {
  return <Text style={styles.helperText}>{children}</Text>;
}

export function OptionButton({
  label,
  selected,
  onPress,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityLabel={label}
      accessibilityRole="radio"
      accessibilityState={{ checked: selected }}
      onPress={onPress}
      style={({ hovered }: { pressed: boolean; hovered?: boolean }) => [
        styles.option,
        hovered && styles.optionHover,
        selected && styles.optionSelected,
      ]}
    >
      <Text style={[styles.optionText, selected && styles.optionTextSelected]}>{label}</Text>
    </Pressable>
  );
}

export function FormFooter({ children }: PropsWithChildren) {
  return <View style={styles.footer}>{children}</View>;
}

const styles = StyleSheet.create({
  heading: { marginBottom: 27, marginTop: 22 },
  brand: { marginBottom: 30 },
  eyebrow: {
    color: palette.coralDark,
    fontFamily: fonts.sans,
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 2,
    marginBottom: 10,
  },
  title: {
    color: palette.dark,
    fontFamily: fonts.display,
    fontSize: 35,
    lineHeight: 42,
    fontWeight: '700',
    letterSpacing: -0.8,
  },
  description: {
    color: palette.muted,
    fontFamily: fonts.sans,
    fontSize: 15,
    lineHeight: 24,
    marginTop: 10,
    maxWidth: 750,
  },
  field: { marginBottom: 18, flexShrink: 1 },
  label: {
    color: palette.dark,
    fontFamily: fonts.sans,
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 8,
  },
  input: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 12,
    borderWidth: 1,
    color: palette.dark,
    fontFamily: fonts.sans,
    fontSize: 15,
    minHeight: 50,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  inputFocused: { borderColor: palette.coral, backgroundColor: '#FFFFFF' },
  primaryButton: {
    alignItems: 'center',
    backgroundColor: palette.coral,
    borderRadius: 15,
    justifyContent: 'center',
    marginTop: 9,
    minHeight: 48,
    maxWidth: '100%',
    paddingHorizontal: 22,
    paddingVertical: 12,
  },
  primaryButtonHover: { backgroundColor: palette.coralDark },
  primaryButtonMuted: { opacity: 0.47 },
  primaryButtonText: {
    color: '#FFFFFF',
    fontFamily: fonts.sans,
    fontSize: 15,
    fontWeight: '700',
    textAlign: 'center',
    flexShrink: 1,
  },
  textButton: {
    alignItems: 'center',
    alignSelf: 'center',
    maxWidth: '100%',
    flexShrink: 1,
    minHeight: 42,
    justifyContent: 'center',
    paddingHorizontal: 13,
    borderRadius: 10,
    marginTop: 4,
  },
  textButtonLabel: {
    color: palette.dark,
    fontFamily: fonts.sans,
    fontSize: 13,
    fontWeight: '700',
    textDecorationLine: 'underline',
    textAlign: 'center',
  },
  disabledText: { opacity: 0.35 },
  errorBanner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
    backgroundColor: palette.errorBackground,
    borderColor: '#F0C4B4',
    borderRadius: 13,
    borderWidth: 1,
    marginVertical: 12,
    padding: 14,
  },
  errorText: {
    flex: 1,
    color: palette.error,
    fontFamily: fonts.sans,
    fontSize: 13,
    lineHeight: 21,
  },
  helperText: {
    color: palette.muted,
    fontFamily: fonts.sans,
    fontSize: 13,
    lineHeight: 21,
    marginBottom: 12,
    marginTop: 5,
  },
  option: {
    alignItems: 'center',
    backgroundColor: 'transparent',
    borderColor: '#CDB5A6',
    borderRadius: 12,
    borderWidth: 1,
    justifyContent: 'center',
    marginBottom: 4,
    marginRight: 6,
    minHeight: 36,
    paddingHorizontal: 18,
    paddingVertical: 6,
  },
  optionHover: { backgroundColor: '#F1E7DF' },
  optionSelected: { backgroundColor: '#50364E', borderColor: '#50364E' },
  optionText: { color: palette.dark, fontFamily: fonts.sans, fontSize: 13, fontWeight: '500' },
  optionTextSelected: { color: '#FFFFFF', fontWeight: '700' },
  footer: { alignItems: 'center', marginTop: 21, gap: 4 },
});
