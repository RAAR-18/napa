import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react';
import { router, usePathname } from 'expo-router';
import {
  AccessibilityInfo,
  Animated,
  Easing,
  Image,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { readSession } from '../lib/auth';
import { Icon, type IconName } from './NapaUI';
import { DESKTOP_BREAKPOINT, art, fonts, palette } from './theme';

type NavigationState = { path: string; role: string | null };
const NavigationContext = createContext<NavigationState>({ path: '', role: null });
export const useNapaNavigation = () => useContext(NavigationContext);

export function NapaNavigationProvider({ children }: PropsWithChildren) {
  const path = usePathname();
  const [role, setRole] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    void readSession()
      .then((session) => {
        if (active) setRole(session?.rol ?? null);
      })
      .catch(() => {
        if (active) setRole(null);
      });
    return () => {
      active = false;
    };
  }, [path]);
  return <NavigationContext.Provider value={{ path, role }}>{children}</NavigationContext.Provider>;
}

type NavItem = { label: string; route: string; icon: IconName; related?: string[] };
const shared: NavItem[] = [
  {
    label: 'Notificaciones',
    route: '/notificaciones',
    icon: 'bell',
    related: ['/preferencias-notificaciones'],
  },
  { label: 'Historial de pagos', route: '/historial-pagos', icon: 'layers' },
  { label: 'Mis reportes', route: '/mis-reportes', icon: 'bar-chart-2', related: ['/reportar'] },
];
const client: NavItem[] = [
  {
    label: 'Explorar',
    route: '/emprendimientos',
    icon: 'compass',
    related: ['/catalogo', '/emprendimiento-detalle'],
  },
  {
    label: 'Mis pedidos',
    route: '/mis-pedidos',
    icon: 'package',
    related: ['/pedido-cliente', '/calificar-pedido', '/trazabilidad-domicilio'],
  },
];
const seller: NavItem[] = [
  {
    label: 'Pedidos recibidos',
    route: '/pedidos-vendedor',
    icon: 'shopping-bag',
    related: ['/pedido-vendedor', '/ubicacion-entrega'],
  },
  {
    label: 'Mis productos',
    route: '/mis-productos',
    icon: 'box',
    related: ['/nuevo-producto', '/editar-producto'],
  },
  {
    label: 'Mi emprendimiento',
    route: '/mi-emprendimiento',
    icon: 'home',
    related: ['/emprendimiento'],
  },
  { label: 'Mis domicilios', route: '/domicilios-vendedor', icon: 'truck' },
  { label: 'Métodos de pago', route: '/configuracion-pagos', icon: 'credit-card' },
];
const courier: NavItem[] = [
  { label: 'Domicilios disponibles', route: '/domicilios-disponibles', icon: 'navigation' },
  {
    label: 'En curso',
    route: '/domicilios-en-curso',
    icon: 'package',
    related: ['/domicilio-domiciliario', '/ruta-domicilio'],
  },
  { label: 'Mi cuenta para cobrar', route: '/cuenta-domiciliario', icon: 'credit-card' },
];
const admin: NavItem[] = [
  {
    label: 'Supervisar domicilios',
    route: '/domicilios-admin',
    icon: 'truck',
    related: ['/domicilio-admin'],
  },
  { label: 'Reportes', route: '/reportes-admin', icon: 'bar-chart-2', related: ['/reporte-admin'] },
  { label: 'Usuarios', route: '/usuarios-admin', icon: 'users', related: ['/usuario-admin'] },
  { label: 'Calificaciones y comentarios', route: '/moderacion-admin', icon: 'star' },
  { label: 'Historial general de pagos', route: '/historial-pagos', icon: 'layers' },
  {
    label: 'Notificaciones',
    route: '/notificaciones',
    icon: 'bell',
    related: ['/preferencias-notificaciones'],
  },
];
const account: NavItem[] = [
  { label: 'Mi perfil', route: '/perfil', icon: 'user' },
  {
    label: 'Mi cuenta',
    route: '/session',
    icon: 'settings',
    related: ['/change-password', '/pendientes-envio'],
  },
];
const roleNames: Record<string, string> = {
  CLIENTE: 'Cliente',
  VENDEDOR_PUNTO_FIJO: 'Vendedor · Punto fijo',
  VENDEDOR_AMBULANTE: 'Vendedor · Ambulante',
  DOMICILIARIO: 'Domiciliario',
  ADMINISTRADOR: 'Administrador',
};

function navigationFor(role: string | null) {
  if (role === 'ADMINISTRADOR') return { heading: 'Administración', items: admin, secondary: [] };
  if (role === 'DOMICILIARIO')
    return { heading: 'Tus entregas', items: courier, secondary: shared };
  if (role?.startsWith('VENDEDOR'))
    return {
      heading: 'Mi negocio',
      items: seller.filter(
        (item) => role !== 'VENDEDOR_AMBULANTE' || item.route !== '/domicilios-vendedor',
      ),
      secondary: shared,
    };
  return { heading: 'Descubre', items: client, secondary: shared };
}

export function homeForRole(role: string) {
  if (role === 'ADMINISTRADOR') return '/domicilios-admin' as const;
  if (role === 'DOMICILIARIO') return '/domicilios-disponibles' as const;
  if (role.startsWith('VENDEDOR')) return '/pedidos-vendedor' as const;
  return '/emprendimientos' as const;
}

export function Brand({ light = false, large = false }: { light?: boolean; large?: boolean }) {
  return (
    <View style={styles.brandWrap}>
      <Text
        allowFontScaling={false}
        numberOfLines={1}
        adjustsFontSizeToFit
        minimumFontScale={0.75}
        style={{
          color: light ? '#FFF9F1' : palette.dark,
          fontFamily: fonts.display,
          fontSize: large ? 65 : 32,
          lineHeight: large ? 82 : 42,
          fontWeight: '700',
          letterSpacing: 0,
          paddingHorizontal: 3,
          includeFontPadding: true,
        }}
      >
        Ñapa
      </Text>
      <View style={[styles.brandSpark, { right: 6, top: large ? 12 : 0 }]}>
        <View style={styles.spark} />
        <View style={[styles.spark, { transform: [{ rotate: '43deg' }], left: 13, top: 8 }]} />
        <View
          style={[
            styles.spark,
            { transform: [{ rotate: '78deg' }], left: 19, top: 23, height: 12 },
          ]}
        />
      </View>
    </View>
  );
}

function HomeLink({ children, onNavigate }: PropsWithChildren<{ onNavigate?: () => void }>) {
  const { role } = useNapaNavigation();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="Ñapa, ir a mi inicio"
      disabled={!role}
      onPress={() => {
        if (!role) return;
        Keyboard.dismiss();
        onNavigate?.();
        router.replace(homeForRole(role));
      }}
      style={({ pressed }) => [styles.homeLink, pressed && { opacity: 0.7 }]}
    >
      {children}
    </Pressable>
  );
}

function Sidebar({ onNavigate, showProfile = true }: { onNavigate?: () => void; showProfile?: boolean }) {
  const { path, role } = useNapaNavigation();
  const navigation = navigationFor(role);
  const accountActive = path === '/session' || account[1].related?.includes(path);
  const navItem = (item: NavItem) => {
    const active = path === item.route || item.related?.includes(path);
    return (
      <Pressable
        key={item.route}
        accessibilityRole="button"
        accessibilityLabel={item.label}
        accessibilityState={{ selected: Boolean(active) }}
        onPress={() => {
          onNavigate?.();
          router.push(item.route as never);
        }}
        style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
          styles.navItem,
          active && styles.navItemActive,
          hovered && !active && styles.navItemHover,
          pressed && { opacity: 0.72 },
        ]}
      >
        <Icon name={item.icon} color="#FFF8F1" size={21} />
        <Text style={[styles.navText, active && { fontWeight: '700' }]}>{item.label}</Text>
      </Pressable>
    );
  };
  return (
    <View style={styles.sidebar}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.sidebarInner}>
        <View style={styles.logoBlock}>
          <HomeLink onNavigate={onNavigate}><Brand light large /></HomeLink>
          <Text style={styles.tagline}>Tu barrio, conectado.</Text>
          <View style={styles.brandUnderline} />
        </View>
        <Text style={styles.navHeading}>{navigation.heading.toUpperCase()}</Text>
        {navigation.items.map(navItem)}
        <Image source={art.canasta} resizeMode="contain" accessible={false} style={styles.basket} />
        {navigation.secondary.length ? (
          <View style={styles.navSecondary}>{navigation.secondary.map(navItem)}</View>
        ) : null}
        {showProfile ? (
          <View style={styles.accountLinks}>
            <Text style={styles.navHeading}>TU CUENTA</Text>
            {navItem(account[0])}
          </View>
        ) : null}
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Mi cuenta"
          accessibilityHint="Abre las opciones de tu cuenta"
          accessibilityState={{ selected: Boolean(accountActive) }}
          onPress={() => {
            onNavigate?.();
            router.push('/session');
          }}
          style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
            styles.userBlock,
            (accountActive || hovered) && styles.userBlockHighlighted,
            pressed && { opacity: 0.72 },
          ]}
        >
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>
              {(roleNames[role ?? 'CLIENTE'] ?? 'Mi cuenta').slice(0, 1)}
            </Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.userRole}>{roleNames[role ?? 'CLIENTE'] ?? 'Mi cuenta'}</Text>
            <Text style={styles.userHint}>Ver mi cuenta</Text>
          </View>
          <Icon name="chevron-right" size={18} color="#DCCCD7" />
        </Pressable>
      </ScrollView>
    </View>
  );
}

const publicRoutes = ['/', '/login', '/register', '/verify', '/recover'];
const formRoutes = [
  '/perfil',
  '/change-password',
  '/configuracion-pagos',
  '/cuenta-domiciliario',
  '/nuevo-producto',
  '/editar-producto',
  '/emprendimiento',
  '/reportar',
  '/calificar-pedido',
  '/preferencias-notificaciones',
];

function MobileMenu({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [progress] = useState(() => new Animated.Value(0));
  const [reduceMotion, setReduceMotion] = useState(false);

  useEffect(() => {
    let active = true;
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', (enabled) => {
      if (active) setReduceMotion(enabled);
    });
    void AccessibilityInfo.isReduceMotionEnabled()
      .then((enabled) => { if (active) setReduceMotion(enabled); })
      .catch(() => undefined);
    return () => {
      active = false;
      subscription.remove();
    };
  }, []);

  useEffect(() => {
    const animation = Animated.timing(progress, {
      toValue: open ? 1 : 0,
      duration: reduceMotion ? 0 : open ? 280 : 220,
      easing: open ? Easing.out(Easing.cubic) : Easing.inOut(Easing.quad),
      useNativeDriver: Platform.OS !== 'web',
    });
    animation.start();
    return () => animation.stop();
  }, [open, progress, reduceMotion]);

  return (
    <View
      style={styles.menuOverlay}
      pointerEvents={open ? 'auto' : 'none'}
      accessibilityElementsHidden={!open}
      importantForAccessibility={open ? 'auto' : 'no-hide-descendants'}
    >
      <Animated.View style={[styles.menuScrim, { opacity: progress }]}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Cerrar menú de navegación"
          onPress={onClose}
          style={styles.flex}
        />
      </Animated.View>
      <Animated.View
        style={{
          transform: [{ translateX: progress.interpolate({ inputRange: [0, 1], outputRange: [-244, 0] }) }],
        }}
      >
        <Sidebar onNavigate={onClose} />
      </Animated.View>
    </View>
  );
}

export function AppShell({ children }: PropsWithChildren) {
  const { path, role } = useNapaNavigation();
  const { width } = useWindowDimensions();
  const [menuOpen, setMenuOpen] = useState(false);
  const desktop = width >= DESKTOP_BREAKPOINT;
  const signedInPage = Boolean(path) && !publicRoutes.includes(path);
  const homePath = homeForRole(role ?? 'CLIENTE');
  const showBack = Boolean(path) && path !== '/' && (!signedInPage || path !== homePath);
  const navigation = navigationFor(role);
  const allItems = [...navigation.items, ...navigation.secondary, ...account];
  const current = allItems.find((item) => item.route === path || item.related?.includes(path));

  function goBack() {
    if (menuOpen) {
      setMenuOpen(false);
      return;
    }
    Keyboard.dismiss();
    if (router.canGoBack()) router.back();
    else router.replace(signedInPage ? homePath : '/');
  }

  const backButton = (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="Volver a la pantalla anterior"
      accessibilityHint={menuOpen ? 'Cierra el menú de navegación' : 'Regresa sin salir de Ñapa'}
      onPress={goBack}
      style={({ pressed }) => [styles.backButton, pressed && styles.backButtonPressed]}
    >
      <Icon name="chevron-left" color={palette.coralDark} size={21} />
      <Text style={styles.backLabel}>Volver</Text>
    </Pressable>
  );

  if (!signedInPage)
    return (
      <SafeAreaView style={styles.safeArea}>
        <KeyboardAvoidingView
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
          style={styles.flex}
        >
          {!desktop && showBack ? <View style={styles.publicBackHeader}>{backButton}</View> : null}
          <View style={styles.publicLayout}>
            {desktop ? (
              <View style={styles.publicStory}>
                <Brand light large />
                <Text style={styles.publicTitle}>Lo mejor de tu barrio, más cerca.</Text>
                <Image
                  source={art.cliente}
                  resizeMode="contain"
                  accessible={false}
                  style={styles.publicArt}
                />
                <Text style={styles.publicTag}>Hecho aquí. Compartido contigo.</Text>
              </View>
            ) : null}
            <ScrollView
              keyboardShouldPersistTaps="handled"
              style={styles.flex}
              contentContainerStyle={[styles.publicScroll, desktop && { paddingVertical: 64 }]}
            >
              <View style={styles.publicForm}>{children}</View>
            </ScrollView>
          </View>
        </KeyboardAvoidingView>
      </SafeAreaView>
    );

  return (
    <SafeAreaView style={styles.safeArea} edges={desktop ? [] : ['top', 'left', 'right', 'bottom']}>
      <View style={styles.layout}>
        {desktop ? <Sidebar /> : null}
        <View style={styles.main}>
          {!desktop ? (
            <View style={styles.mobileHeader}>
              <View style={styles.mobileHeaderLeft}>
                {showBack ? backButton : null}
                <HomeLink onNavigate={() => setMenuOpen(false)}><Brand /></HomeLink>
              </View>
              {width >= (showBack ? 480 : 360) ? <Text style={styles.mobileMotto}>COMERCIO LOCAL</Text> : null}
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={menuOpen ? 'Cerrar menú' : 'Abrir menú'}
                accessibilityState={{ expanded: menuOpen }}
                onPress={() => setMenuOpen((open) => !open)}
                style={styles.menuButton}
              >
                <Icon name={menuOpen ? 'x' : 'menu'} />
              </Pressable>
            </View>
          ) : null}
          <KeyboardAvoidingView
            style={styles.flex}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
          >
            <ScrollView
              keyboardShouldPersistTaps="handled"
              contentContainerStyle={[styles.content, !desktop && styles.contentMobile]}
            >
              {desktop ? (
                <View style={styles.breadcrumb}>
                  <View style={styles.crumbTrail}>
                    <HomeLink><Text style={styles.crumbBrand}>Ñapa</Text></HomeLink>
                    <Text style={styles.crumbSlash}>/</Text>
                    {role && role !== 'CLIENTE' ? (
                      <>
                        <Text style={styles.crumbText}>
                          {role === 'ADMINISTRADOR'
                            ? 'Administración'
                            : role === 'DOMICILIARIO'
                              ? 'Domiciliario'
                              : 'Vendedor'}
                        </Text>
                        <Text style={styles.crumbSlash}>/</Text>
                      </>
                    ) : null}
                    <Text style={styles.crumbCurrent}>{current?.label ?? 'Tu barrio'}</Text>
                  </View>
                  <Text style={styles.commerce}>COMERCIO LOCAL</Text>
                </View>
              ) : null}
              <View style={[styles.pageBody, formRoutes.includes(path) && styles.formBody]}>
                {children}
              </View>
              <View style={styles.contentFooter}>
                <View style={styles.footerDot} />
                <Text style={styles.footerText}>Hecho para conectar lo nuestro.</Text>
              </View>
            </ScrollView>
          </KeyboardAvoidingView>
        </View>
        {!desktop ? (
          <MobileMenu open={menuOpen} onClose={() => setMenuOpen(false)} />
        ) : null}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  homeLink: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center' },
  safeArea: { flex: 1, backgroundColor: palette.background },
  layout: { flex: 1, flexDirection: 'row' },
  main: { flex: 1, minWidth: 0 },
  sidebar: { width: 244, backgroundColor: palette.plum, height: '100%' },
  sidebarInner: { paddingHorizontal: 16, paddingTop: 25, paddingBottom: 22, flexGrow: 1 },
  logoBlock: { paddingHorizontal: 17, marginBottom: 22 },
  brandWrap: { alignSelf: 'flex-start', position: 'relative', paddingRight: 34, maxWidth: '100%', flexShrink: 1 },
  brandSpark: { position: 'absolute', width: 20, height: 30 },
  spark: {
    width: 4,
    height: 15,
    borderRadius: 4,
    backgroundColor: palette.coral,
    position: 'absolute',
    transform: [{ rotate: '10deg' }],
  },
  tagline: {
    color: '#F6EAE0',
    fontFamily: fonts.sans,
    fontSize: 14,
    fontStyle: 'italic',
    marginTop: 0,
  },
  brandUnderline: {
    height: 3,
    width: 95,
    backgroundColor: palette.coral,
    transform: [{ rotate: '-9deg' }],
    marginLeft: 50,
    marginTop: 13,
    borderRadius: 3,
  },
  navHeading: {
    fontFamily: fonts.sans,
    fontSize: 10,
    letterSpacing: 1.7,
    fontWeight: '800',
    color: '#DCCCD7',
    marginLeft: 14,
    marginBottom: 11,
    marginTop: 7,
  },
  navItem: {
    flexDirection: 'row',
    gap: 13,
    alignItems: 'center',
    minHeight: 45,
    paddingVertical: 9,
    paddingHorizontal: 13,
    borderRadius: 14,
    marginBottom: 3,
  },
  navItemActive: { backgroundColor: palette.coral },
  navItemHover: { backgroundColor: '#50364F' },
  navText: { color: '#FFF7F0', fontFamily: fonts.sans, fontSize: 14, flex: 1, lineHeight: 19 },
  basket: { width: 169, height: 110, alignSelf: 'center', marginVertical: 12 },
  navSecondary: { borderTopWidth: 1, borderTopColor: '#674E62', paddingTop: 16, paddingBottom: 12 },
  accountLinks: { borderTopWidth: 1, borderTopColor: '#674E62', paddingTop: 16, marginTop: 8 },
  userBlock: {
    borderTopWidth: 1,
    borderTopColor: '#674E62',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingTop: 16,
    paddingBottom: 12,
    paddingHorizontal: 8,
    borderRadius: 12,
    marginTop: 16,
  },
  userBlockHighlighted: { backgroundColor: '#50364F' },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: palette.peach,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { fontFamily: fonts.display, fontWeight: '700', fontSize: 25, color: palette.plum },
  userRole: {
    fontFamily: fonts.sans,
    color: '#FFF6EF',
    fontSize: 12,
    fontWeight: '700',
    lineHeight: 18,
  },
  userHint: { fontFamily: fonts.sans, color: '#BDA9B8', fontSize: 10, marginTop: 2 },
  content: {
    paddingHorizontal: 32,
    paddingTop: 18,
    paddingBottom: 30,
    maxWidth: 1500,
    width: '100%',
    alignSelf: 'center',
    flexGrow: 1,
  },
  contentMobile: { paddingHorizontal: 18, paddingTop: 5, paddingBottom: 20 },
  pageBody: { width: '100%', flexGrow: 1 },
  formBody: { maxWidth: 800, alignSelf: 'center', paddingTop: 16 },
  breadcrumb: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 15,
    borderBottomWidth: 1,
    borderBottomColor: palette.border,
    paddingBottom: 15,
    marginBottom: 8,
  },
  crumbTrail: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  crumbBrand: { fontFamily: fonts.sans, color: palette.plum, fontWeight: '800', fontSize: 13 },
  crumbSlash: { color: '#B99D8F', fontSize: 15 },
  crumbText: { fontFamily: fonts.sans, color: palette.plum, fontWeight: '600', fontSize: 13 },
  crumbCurrent: {
    fontFamily: fonts.sans,
    color: palette.coralDark,
    fontWeight: '700',
    fontSize: 13,
  },
  commerce: {
    fontFamily: fonts.sans,
    color: palette.plum,
    letterSpacing: 2,
    fontWeight: '800',
    fontSize: 10,
  },
  contentFooter: { marginTop: 32, flexDirection: 'row', alignItems: 'center', gap: 7 },
  footerDot: { width: 5, height: 5, backgroundColor: palette.coral, borderRadius: 3 },
  footerText: { fontFamily: fonts.sans, fontSize: 11, color: palette.muted, fontStyle: 'italic' },
  mobileHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    height: 65,
    borderBottomWidth: 1,
    borderBottomColor: palette.border,
  },
  mobileMotto: {
    fontFamily: fonts.sans,
    color: palette.muted,
    fontSize: 9,
    letterSpacing: 1.8,
    fontWeight: '700',
  },
  mobileHeaderLeft: { flexDirection: 'row', alignItems: 'center', gap: 8, flexShrink: 1 },
  publicBackHeader: {
    height: 58,
    paddingHorizontal: 18,
    justifyContent: 'center',
    borderBottomWidth: 1,
    borderBottomColor: palette.border,
  },
  backButton: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 44,
    minWidth: 44,
    paddingHorizontal: 8,
    gap: 3,
    borderRadius: 12,
    backgroundColor: '#F5E8DD',
  },
  backButtonPressed: { backgroundColor: palette.peach, opacity: 0.8 },
  backLabel: { fontFamily: fonts.sans, fontSize: 13, fontWeight: '700', color: palette.plum },
  menuButton: { padding: 9, borderWidth: 1, borderColor: palette.border, borderRadius: 12 },
  bottomNav: {
    flexDirection: 'row',
    backgroundColor: palette.white,
    borderTopWidth: 1,
    borderColor: palette.border,
    paddingTop: 8,
    paddingBottom: 14,
  },
  bottomItem: { flex: 1, alignItems: 'center', gap: 4, minHeight: 44, justifyContent: 'center' },
  bottomLabel: { fontFamily: fonts.sans, fontSize: 11, color: palette.muted, maxWidth: '95%' },
  menuOverlay: {
    position: 'absolute',
    top: 65,
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    zIndex: 20,
    overflow: 'hidden',
  },
  menuScrim: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: '#20132480',
  },
  publicLayout: { flex: 1, flexDirection: 'row' },
  publicStory: {
    width: '46%',
    backgroundColor: palette.plum,
    padding: 48,
    justifyContent: 'center',
    overflow: 'hidden',
  },
  publicTitle: {
    fontFamily: fonts.display,
    color: '#FFF8EF',
    fontSize: 48,
    lineHeight: 54,
    fontWeight: '700',
    maxWidth: 450,
    marginTop: 25,
    letterSpacing: -1.3,
  },
  publicArt: { width: '115%', height: 300, alignSelf: 'center', marginVertical: 15 },
  publicTag: { fontFamily: fonts.sans, color: '#DDC9D5', fontSize: 14 },
  publicScroll: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingVertical: 32,
    paddingHorizontal: 26,
  },
  publicForm: { width: '100%', maxWidth: 440, alignSelf: 'center' },
});
