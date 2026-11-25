import { useCallback, useRef } from 'react';
import { AppState, Platform } from 'react-native';
import { useFocusEffect } from 'expo-router';

/**
 * El Stack de Expo Router deja montadas las pantallas anteriores. Los refrescos periódicos
 * consultan esta función para no pedir datos al servidor mientras la pantalla está tapada por
 * otra, la app está en segundo plano o la pestaña del navegador está oculta.
 */
export function usePantallaVisible(): () => boolean {
  const enFoco = useRef(true);
  useFocusEffect(useCallback(() => {
    enFoco.current = true;
    return () => {
      enFoco.current = false;
    };
  }, []));

  return useCallback(() => {
    if (!enFoco.current) return false;
    if (AppState.currentState === 'background' || AppState.currentState === 'inactive') return false;
    if (Platform.OS === 'web' && typeof document !== 'undefined' && document.visibilityState === 'hidden') {
      return false;
    }
    return true;
  }, []);
}
