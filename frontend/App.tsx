import { StatusBar } from 'expo-status-bar';
import { StyleSheet, Text, View } from 'react-native';

export default function App() {
  return (
    <View style={styles.container}>
      <Text accessibilityRole="header" style={styles.title}>Ñapa</Text>
      <Text style={styles.description}>
        Conecta a vendedores locales con clientes cercanos.
      </Text>
      <StatusBar style="auto" />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  title: {
    color: '#1D5A46',
    fontSize: 36,
    fontWeight: '700',
  },
  description: {
    color: '#475569',
    fontSize: 16,
    marginTop: 12,
    textAlign: 'center',
  },
});
