import { useCallback, useEffect, useRef } from 'react';
import { AppState } from 'react-native';
import { useFocusEffect } from 'expo-router';

export const AUTO_REFRESH_MS = 30_000;

export function useAutoRefresh(refrescar: () => void, habilitado = true) {
  const actual = useRef(refrescar);
  useEffect(() => { actual.current = refrescar; }, [refrescar]);
  useFocusEffect(useCallback(() => {
    if (!habilitado) return;
    const refrescarVisible = () => {
      if (!AppState.currentState || AppState.currentState === 'active') actual.current();
    };
    const timer = setInterval(refrescarVisible, AUTO_REFRESH_MS);
    const listener = AppState.addEventListener('change', (estado) => {
      if (estado === 'active') actual.current();
    });
    return () => { clearInterval(timer); listener.remove(); };
  }, [habilitado]));
}
