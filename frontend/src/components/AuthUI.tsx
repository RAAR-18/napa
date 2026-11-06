import type { PropsWithChildren } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type TextInputProps,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

export const palette = {
  green: '#1D5A46',
  dark: '#17372E',
  muted: '#66756F',
  border: '#D9E2DC',
  background: '#F4F7F4',
  white: '#FFFFFF',
  error: '#A32D2D',
  errorBackground: '#FFF0EE',
};

export function AuthPage({ children }: PropsWithChildren) {
  return (
    <SafeAreaView style={styles.safeArea}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={styles.flex}
      >
        <ScrollView
          contentContainerStyle={styles.page}
          keyboardShouldPersistTaps="handled"
        >
          {children}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

export function PageHeading({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description: string;
}) {
  return (
    <View style={styles.heading}>
      <Text style={styles.brand}>Ñapa</Text>
      <Text style={styles.eyebrow}>{eyebrow.toUpperCase()}</Text>
      <Text accessibilityRole="header" style={styles.title}>{title}</Text>
      <Text style={styles.description}>{description}</Text>
    </View>
  );
}

export function Field({
  label,
  ...props
}: TextInputProps & { label: string }) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor="#8A9991"
        style={styles.input}
        {...props}
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
      style={({ pressed }) => [
        styles.primaryButton,
        (pressed || disabled || busy) && styles.primaryButtonMuted,
      ]}
    >
      {busy ? (
        <ActivityIndicator color={palette.white} />
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
      disabled={disabled}
      onPress={onPress}
      style={styles.textButton}
    >
      <Text style={[styles.textButtonLabel, disabled && styles.disabledText]}>
        {title}
      </Text>
    </Pressable>
  );
}

export function ErrorBanner({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return (
    <View accessibilityRole="alert" style={styles.errorBanner}>
      <Text style={styles.errorText}>{message}</Text>
    </View>
  );
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
      style={[styles.option, selected && styles.optionSelected]}
    >
      <Text style={[styles.optionText, selected && styles.optionTextSelected]}>
        {label}
      </Text>
    </Pressable>
  );
}

export function FormFooter({ children }: PropsWithChildren) {
  return <View style={styles.footer}>{children}</View>;
}

const styles = StyleSheet.create({
  safeArea: {
    backgroundColor: palette.background,
    flex: 1,
  },
  flex: {
    flex: 1,
  },
  page: {
    alignSelf: 'center',
    maxWidth: 520,
    paddingHorizontal: 24,
    paddingVertical: 32,
    width: '100%',
  },
  heading: {
    marginBottom: 28,
  },
  brand: {
    color: palette.green,
    fontSize: 24,
    fontWeight: '800',
    marginBottom: 24,
  },
  eyebrow: {
    color: palette.green,
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 1.5,
    marginBottom: 8,
  },
  title: {
    color: palette.dark,
    fontSize: 30,
    fontWeight: '800',
    letterSpacing: -0.6,
  },
  description: {
    color: palette.muted,
    fontSize: 15,
    lineHeight: 23,
    marginTop: 8,
  },
  field: {
    marginBottom: 16,
  },
  label: {
    color: palette.dark,
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 7,
  },
  input: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 12,
    borderWidth: 1,
    color: palette.dark,
    fontSize: 16,
    minHeight: 52,
    paddingHorizontal: 15,
  },
  primaryButton: {
    alignItems: 'center',
    backgroundColor: palette.green,
    borderRadius: 13,
    justifyContent: 'center',
    marginTop: 8,
    minHeight: 54,
  },
  primaryButtonMuted: {
    opacity: 0.65,
  },
  primaryButtonText: {
    color: palette.white,
    fontSize: 16,
    fontWeight: '800',
  },
  textButton: {
    alignItems: 'center',
    alignSelf: 'center',
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: 8,
  },
  textButtonLabel: {
    color: palette.green,
    fontSize: 15,
    fontWeight: '700',
  },
  disabledText: {
    opacity: 0.5,
  },
  errorBanner: {
    backgroundColor: palette.errorBackground,
    borderColor: '#F0C6C0',
    borderRadius: 10,
    borderWidth: 1,
    marginBottom: 16,
    padding: 12,
  },
  errorText: {
    color: palette.error,
    fontSize: 14,
    lineHeight: 20,
  },
  helperText: {
    color: palette.muted,
    fontSize: 13,
    lineHeight: 19,
    marginBottom: 16,
    marginTop: -6,
  },
  option: {
    alignItems: 'center',
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 10,
    borderWidth: 1,
    flexGrow: 1,
    justifyContent: 'center',
    marginBottom: 8,
    marginRight: 8,
    minHeight: 42,
    paddingHorizontal: 12,
  },
  optionSelected: {
    backgroundColor: '#E8F2EB',
    borderColor: palette.green,
  },
  optionText: {
    color: palette.dark,
    fontSize: 13,
    fontWeight: '600',
  },
  optionTextSelected: {
    color: palette.green,
    fontWeight: '800',
  },
  footer: {
    alignItems: 'center',
    marginTop: 18,
  },
});
