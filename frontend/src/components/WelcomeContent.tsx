import { StyleSheet, Text, View } from 'react-native';

export default function WelcomeContent() {
  return (
    <View style={styles.brand}>
      <Text accessibilityRole="header" style={styles.title}>Ñapa</Text>
      <Text style={styles.description}>
        Conecta a vendedores locales con clientes cercanos.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  brand: {
    alignItems: 'center',
    marginBottom: 32,
  },
  title: {
    color: '#1D5A46',
    fontSize: 38,
    fontWeight: '800',
    letterSpacing: -1,
  },
  description: {
    color: '#52645E',
    fontSize: 16,
    lineHeight: 24,
    marginTop: 8,
    maxWidth: 300,
    textAlign: 'center',
  },
});
