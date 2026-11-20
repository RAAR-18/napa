import { fonts } from './theme';
import { StyleSheet, Text, View } from 'react-native';
import { Brand } from './AppShell';

export default function WelcomeContent() {
  return (
    <View style={styles.brand}>
      <Brand />
      <Text accessibilityRole="header" style={styles.title}>
        Tu barrio, conectado.
      </Text>
      <Text style={styles.description}>Conecta a vendedores locales con clientes cercanos.</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  brand: {
    alignItems: 'center',
    marginBottom: 32,
  },
  title: {
    color: '#342036',
    fontFamily: fonts.display,
    fontSize: 43,
    lineHeight: 49,
    fontWeight: '700',
    textAlign: 'center',
    letterSpacing: -1,
    marginTop: 24,
  },
  eyebrow: {
    fontFamily: fonts.sans,
    color: '#C84529',
    fontWeight: '800',
    fontSize: 10,
    letterSpacing: 2.2,
    marginBottom: 18,
  },
  description: {
    color: '#796C72',
    fontFamily: fonts.sans,
    fontSize: 16,
    lineHeight: 24,
    marginTop: 8,
    maxWidth: 300,
    textAlign: 'center',
  },
});
