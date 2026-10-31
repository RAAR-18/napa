import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useAuth } from '../context/AuthContext';
import { RolUsuario } from '../types/auth';
import {
  checkApiHealth,
  EmprendimientoListItem,
  listEmprendimientos,
  listProductos,
  ProductoListItem,
} from '../services/api';

const categoryNames: Record<string, string> = {
  TODAS: 'Todos',
  ALIMENTOS_PREPARADOS: 'Comida',
  FRUTAS_VERDURAS: 'Frutas',
  BEBIDAS: 'Bebidas',
  ROPA_ACCESORIOS: 'Ropa',
  ARTESANIAS: 'Artesanías',
  OTROS: 'Otros',
};

const getRolDisplay = (rol: RolUsuario): { nombre: string; color: string; icono: string } => {
  switch (rol) {
    case 'CLIENTE':
      return { nombre: 'Cliente / Comprador', color: '#0284C7', icono: '🛒' };
    case 'VENDEDOR_AMBULANTE':
      return { nombre: 'Vendedor Ambulante', color: '#EA580C', icono: '🛺' };
    case 'VENDEDOR_PUNTO_FIJO':
      return { nombre: 'Vendedor Punto Fijo', color: '#16A34A', icono: '🏪' };
    case 'DOMICILIARIO':
      return { nombre: 'Domiciliario', color: '#9333EA', icono: '🛵' };
    default:
      return { nombre: 'Usuario', color: '#475569', icono: '👤' };
  }
};

interface HomeScreenProps {
  onOpenProfile: () => void;
  onCreateBusiness: () => void;
  onCreateProduct: () => void;
  onOpenBusinesses: () => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({ onOpenProfile, onCreateBusiness, onCreateProduct, onOpenBusinesses }) => {
  const { user, tokens, logout } = useAuth();
  const [apiOnline, setApiOnline] = useState<boolean | null>(null);
  const [businesses, setBusinesses] = useState<EmprendimientoListItem[]>([]);
  const [businessesLoading, setBusinessesLoading] = useState(false);
  const [businessesError, setBusinessesError] = useState<string | null>(null);
  const [selectedCategory, setSelectedCategory] = useState('TODAS');
  const [products, setProducts] = useState<ProductoListItem[]>([]);
  const [productsError, setProductsError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    checkApiHealth().then((online) => {
      if (active) {
        setApiOnline(online);
      }
    });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!user || user.rol !== 'CLIENTE' || !tokens?.accessToken) {
      return;
    }

    let active = true;
    setBusinessesLoading(true);
    setBusinessesError(null);
    listEmprendimientos(tokens.accessToken)
      .then((result) => {
        if (active) {
          setBusinesses(result.content);
        }
      })
      .catch((requestError: unknown) => {
        if (active) {
          setBusinessesError(
            requestError instanceof Error
              ? requestError.message
              : 'No se pudieron cargar los comercios.',
          );
        }
      })
      .finally(() => {
        if (active) {
          setBusinessesLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [tokens?.accessToken, user]);

  useEffect(() => {
    if (!user || user.rol !== 'CLIENTE' || !tokens?.accessToken) {
      return;
    }

    let active = true;
    listProductos(tokens.accessToken)
      .then((result) => {
        if (active) {
          setProducts(result.content);
        }
      })
      .catch((requestError: unknown) => {
        if (active) {
          setProductsError(requestError instanceof Error ? requestError.message : 'No se pudieron cargar los productos.');
        }
      });

    return () => {
      active = false;
    };
  }, [tokens?.accessToken, user]);

  if (!user) {
    return null;
  }

  const rolInfo = getRolDisplay(user.rol);
  const visibleBusinesses = businesses.filter(
    (business) => selectedCategory === 'TODAS' || business.categoria === selectedCategory,
  );

  return (
    <ScrollView contentContainerStyle={styles.container}>
      {/* Cabecera */}
      <View style={styles.header}>
        <View style={styles.brandRow}>
          <View>
            <Text style={styles.eyebrow}>MERCADO LOCAL</Text>
            <Text accessibilityRole="header" style={styles.title}>Ñapa</Text>
          </View>
          <TouchableOpacity
            style={styles.profileBadge}
            onPress={onOpenProfile}
            accessibilityRole="button"
          >
            <Text style={styles.profileBadgeIcon}>
              {user.nombre.charAt(0).toUpperCase()}
            </Text>
            <View>
              <Text style={styles.profileBadgeName} numberOfLines={1}>{user.nombre}</Text>
              <Text style={styles.profileBadgeLabel}>Mi perfil</Text>
            </View>
          </TouchableOpacity>
        </View>

        <Text style={styles.welcomeText}>
          ¡Bienvenido de nuevo, <Text style={styles.nameHighlight}>{user.nombre}</Text>!
        </Text>
        <Text style={styles.description}>
          Conecta a vendedores locales con clientes cercanos.
        </Text>
        <View style={styles.statusRow}>
          <View style={[styles.statusDot, apiOnline === true && styles.statusOnline]} />
          <Text style={styles.statusText}>
            {apiOnline === null
              ? 'Verificando conexión...'
              : apiOnline
                ? 'Servicio conectado'
                : 'Servicio no disponible'}
          </Text>
        </View>
      </View>

      {user.rol === 'CLIENTE' && (
        <View style={styles.businessSection}>
          <View style={styles.sectionHeader}>
            <View>
              <Text style={styles.sectionEyebrow}>CERCA DE TI</Text>
              <Text style={styles.sectionTitle}>Comercios locales</Text>
            </View>
            <TouchableOpacity onPress={onOpenBusinesses} accessibilityRole="button">
              <Text style={styles.seeAll}>Ver todos</Text>
            </TouchableOpacity>
          </View>

          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.categoryList}
          >
            {Object.entries(categoryNames).map(([category, label]) => (
              <TouchableOpacity
                key={category}
                style={[styles.categoryChip, selectedCategory === category && styles.categoryChipSelected]}
                onPress={() => setSelectedCategory(category)}
              >
                <Text style={[styles.categoryChipText, selectedCategory === category && styles.categoryChipTextSelected]}>
                  {label}
                </Text>
              </TouchableOpacity>
            ))}
          </ScrollView>

          {businessesLoading && <ActivityIndicator color="#FF5A5F" style={styles.businessLoader} />}
          {businessesError && (
            <View style={styles.businessMessage}>
              <Text style={styles.businessError}>{businessesError}</Text>
            </View>
          )}
          {!businessesLoading && !businessesError && visibleBusinesses.length === 0 && (
            <View style={styles.businessMessage}>
              <Text style={styles.businessEmptyTitle}>Aún no hay comercios en esta categoría</Text>
              <Text style={styles.businessEmptyText}>Prueba con otra categoría o vuelve más tarde.</Text>
            </View>
          )}
          {!businessesLoading && !businessesError && visibleBusinesses.map((business) => (
            <TouchableOpacity
              key={business.id}
              style={styles.businessCard}
              onPress={onOpenBusinesses}
              activeOpacity={0.8}
            >
              <View style={styles.businessCardTop}>
                {business.imagenUrl ? (
                  <Image source={{ uri: business.imagenUrl }} style={styles.businessImage} />
                ) : (
                  <View style={styles.businessIconTile}><Text style={styles.businessIcon}>🏪</Text></View>
                )}
                <View style={styles.businessCardHeading}>
                  <Text style={styles.businessName} numberOfLines={1}>{business.nombre}</Text>
                  <Text style={styles.businessCategory}>{categoryNames[business.categoria] || business.categoria}</Text>
                </View>
                <Text style={[styles.businessStatus, business.abierto ? styles.businessOpen : styles.businessClosed]}>
                  {business.abierto ? 'Abierto' : 'Cerrado'}
                </Text>
              </View>
              <Text style={styles.businessDescription} numberOfLines={2}>
                {business.descripcion || 'Emprendimiento local de Ñapa.'}
              </Text>
              <Text style={styles.businessAddress} numberOfLines={1}>📍 {business.direccion}</Text>
            </TouchableOpacity>
          ))}
        </View>
      )}

      {user.rol === 'CLIENTE' && (
        <View style={styles.productSection}>
          <View style={styles.sectionHeader}>
            <View>
              <Text style={styles.sectionEyebrow}>PARA COMPRAR HOY</Text>
              <Text style={styles.sectionTitle}>Productos disponibles</Text>
            </View>
          </View>
          {productsError && <Text style={styles.businessError}>{productsError}</Text>}
          {!productsError && products.length === 0 && (
            <View style={styles.businessMessage}>
              <Text style={styles.businessEmptyTitle}>Los vendedores aún no han publicado productos</Text>
            </View>
          )}
          {products.map((product) => (
            <TouchableOpacity key={product.id} style={styles.productCard} activeOpacity={0.8}>
              {product.imagenUrl ? (
                <View style={styles.productImageWrapper}>
                  <Image source={{ uri: product.imagenUrl }} style={styles.productImage} />
                </View>
              ) : (
                <View style={styles.productImagePlaceholder}><Text style={styles.productImageIcon}>🛍️</Text></View>
              )}
              <View style={styles.productInfo}>
                <Text style={styles.productName} numberOfLines={1}>{product.nombre}</Text>
                <Text style={styles.productBusiness} numberOfLines={1}>{product.emprendimientoNombre || 'Comercio local'}</Text>
                <Text style={styles.productDescription} numberOfLines={1}>{product.descripcion || `Disponible por ${product.unidad}`}</Text>
                <Text style={styles.productPrice}>${product.precio.toLocaleString('es-CO')} <Text style={styles.productUnit}>/ {product.unidad}</Text></Text>
              </View>
            </TouchableOpacity>
          ))}
        </View>
      )}

      {/* Acciones Rápidas según el Rol */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Tus acciones principales</Text>

        {user.rol === 'CLIENTE' && (
          <View style={styles.cardsList}>
            <TouchableOpacity style={styles.actionCard} activeOpacity={0.75}>
              <View style={styles.iconTile}><Text style={styles.cardIcon}>📦</Text></View>
              <View style={styles.cardContent}>
                <Text style={styles.cardHeader}>Mis Pedidos y Reservas</Text>
                <Text style={styles.cardDesc}>
                  Revisa el estado de tus compras para entrega o recogida.
                </Text>
              </View>
              <Text style={styles.cardArrow}>›</Text>
            </TouchableOpacity>
          </View>
        )}

        {user.rol === 'VENDEDOR_AMBULANTE' && (
          <View style={styles.cardsList}>
            <TouchableOpacity style={styles.actionCard} activeOpacity={0.75}>
              <View style={styles.iconTile}><Text style={styles.cardIcon}>📍</Text></View>
              <View style={styles.cardContent}>
                <Text style={styles.cardHeader}>Mi Zona y Disponibilidad</Text>
                <Text style={styles.cardDesc}>
                  Indica dónde te encuentras vendiendo hoy en la calle.
                </Text>
              </View>
              <Text style={styles.cardArrow}>›</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.actionCard} activeOpacity={0.75}>
              <View style={styles.iconTile}><Text style={styles.cardIcon}>🔔</Text></View>
              <View style={styles.cardContent}>
                <Text style={styles.cardHeader}>Entregas Directas Pendientes</Text>
                <Text style={styles.cardDesc}>
                  Clientes cercanos que han solicitado que te acerques.
                </Text>
              </View>
              <Text style={styles.cardArrow}>›</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.actionCard} activeOpacity={0.75} onPress={onCreateProduct}>
              <View style={styles.iconTile}><Text style={styles.cardIcon}>🏷️</Text></View>
              <View style={styles.cardContent}>
                <Text style={styles.cardHeader}>Mis Productos e Inventario</Text>
                <Text style={styles.cardDesc}>
                  Publica fotos, precios y cantidades de lo que vendes.
                </Text>
              </View>
              <Text style={styles.cardArrow}>›</Text>
            </TouchableOpacity>
          </View>
        )}

        {user.rol === 'VENDEDOR_PUNTO_FIJO' && (
          <View style={styles.cardsList}>
            <TouchableOpacity style={styles.actionCard} activeOpacity={0.75} onPress={onCreateBusiness}>
              <View style={styles.iconTile}><Text style={styles.cardIcon}>🏪</Text></View>
              <View style={styles.cardContent}>
                <Text style={styles.cardHeader}>Mi Emprendimiento</Text>
                <Text style={styles.cardDesc}>
                  Administra tu puesto fijo, horarios y métodos de pago aceptados.
                </Text>
              </View>
              <Text style={styles.cardArrow}>›</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.actionCard} activeOpacity={0.75}>
              <View style={styles.iconTile}><Text style={styles.cardIcon}>🛵</Text></View>
              <View style={styles.cardContent}>
                <Text style={styles.cardHeader}>Publicar Domicilio</Text>
                <Text style={styles.cardDesc}>
                  Asigna domiciliarios independientes para pedidos ya pagados.
                </Text>
              </View>
              <Text style={styles.cardArrow}>›</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.actionCard} activeOpacity={0.75}>
              <View style={styles.iconTile}><Text style={styles.cardIcon}>🛍️</Text></View>
              <View style={styles.cardContent}>
                <Text style={styles.cardHeader}>Pedidos para Recoger en Punto</Text>
                <Text style={styles.cardDesc}>
                  Prepara los pedidos que los clientes pasarán a retirar.
                </Text>
              </View>
              <Text style={styles.cardArrow}>›</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.actionCard} activeOpacity={0.75} onPress={onCreateProduct}>
              <View style={styles.iconTile}><Text style={styles.cardIcon}>🏷️</Text></View>
              <View style={styles.cardContent}>
                <Text style={styles.cardHeader}>Mis Productos</Text>
                <Text style={styles.cardDesc}>
                  Publica fotos, precios y cantidades de lo que vendes.
                </Text>
              </View>
              <Text style={styles.cardArrow}>›</Text>
            </TouchableOpacity>
          </View>
        )}

        {user.rol === 'DOMICILIARIO' && (
          <View style={styles.cardsList}>
            <TouchableOpacity style={styles.actionCard} activeOpacity={0.75}>
              <View style={styles.iconTile}><Text style={styles.cardIcon}>🛵</Text></View>
              <View style={styles.cardContent}>
                <Text style={styles.cardHeader}>Domicilios Disponibles</Text>
                <Text style={styles.cardDesc}>
                  Revisa puntos de recogida, entrega y la tarifa clara de ganancia.
                </Text>
              </View>
              <Text style={styles.cardArrow}>›</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.actionCard} activeOpacity={0.75}>
              <View style={styles.iconTile}><Text style={styles.cardIcon}>🗺️</Text></View>
              <View style={styles.cardContent}>
                <Text style={styles.cardHeader}>Ruta y Código de Entrega</Text>
                <Text style={styles.cardDesc}>
                  Navega hasta el cliente e ingresa el código para confirmar la entrega.
                </Text>
              </View>
              <Text style={styles.cardArrow}>›</Text>
            </TouchableOpacity>
          </View>
        )}
      </View>

      {/* Botón de Salir */}
      <TouchableOpacity style={styles.logoutButton} onPress={logout}>
        <Text style={styles.logoutText}>Cerrar Sesión</Text>
      </TouchableOpacity>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    backgroundColor: '#F6F7F9',
    padding: 18,
    paddingTop: 34,
    alignItems: 'center',
  },
  header: {
    width: '100%',
    maxWidth: 600,
    backgroundColor: '#FF5A5F',
    borderRadius: 20,
    padding: 20,
    marginBottom: 20,
    shadowColor: '#FF5A5F',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.18,
    shadowRadius: 16,
    elevation: 5,
  },
  eyebrow: {
    color: '#FFE7E8',
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1.8,
    marginBottom: 4,
  },
  brandRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 18,
  },
  title: {
    fontSize: 32,
    fontWeight: '800',
    color: '#FFFFFF',
  },
  profileBadge: {
    maxWidth: 170,
    minWidth: 108,
    paddingHorizontal: 8,
    paddingVertical: 7,
    borderRadius: 16,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(150, 25, 42, 0.35)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.35)',
  },
  profileBadgeIcon: {
    width: 28,
    height: 28,
    borderRadius: 14,
    marginRight: 7,
    textAlign: 'center',
    textAlignVertical: 'center',
    backgroundColor: '#FFFFFF',
    color: '#FF5A5F',
    fontSize: 14,
    fontWeight: '800',
  },
  profileBadgeName: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '800',
  },
  profileBadgeLabel: {
    color: '#FFE7E8',
    fontSize: 10,
    marginTop: 1,
  },
  welcomeText: {
    fontSize: 20,
    color: '#FFFFFF',
    fontWeight: '600',
  },
  nameHighlight: {
    color: '#FFF0D0',
    fontWeight: '800',
  },
  description: {
    fontSize: 13,
    color: '#FFE7E8',
    marginTop: 6,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 14,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 8,
    backgroundColor: '#FFD1D3',
  },
  statusOnline: {
    backgroundColor: '#16A34A',
  },
  statusText: {
    color: '#FFE7E8',
    fontSize: 12,
  },
  businessSection: {
    width: '100%',
    maxWidth: 600,
    marginBottom: 26,
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  sectionEyebrow: {
    color: '#FF5A5F',
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1.4,
  },
  seeAll: {
    color: '#FF5A5F',
    fontSize: 13,
    fontWeight: '800',
  },
  categoryList: {
    gap: 8,
    paddingBottom: 14,
  },
  categoryChip: {
    backgroundColor: '#FFFFFF',
    borderColor: '#ECEEF2',
    borderRadius: 18,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 9,
  },
  categoryChipSelected: {
    backgroundColor: '#FF5A5F',
    borderColor: '#FF5A5F',
  },
  categoryChipText: {
    color: '#6B7280',
    fontSize: 12,
    fontWeight: '700',
  },
  categoryChipTextSelected: {
    color: '#FFFFFF',
  },
  businessLoader: {
    marginVertical: 28,
  },
  businessMessage: {
    backgroundColor: '#FFFFFF',
    borderColor: '#ECEEF2',
    borderRadius: 16,
    borderWidth: 1,
    padding: 18,
  },
  businessError: {
    color: '#C2414B',
    fontSize: 13,
  },
  businessEmptyTitle: {
    color: '#1F2937',
    fontSize: 14,
    fontWeight: '800',
  },
  businessEmptyText: {
    color: '#6B7280',
    fontSize: 13,
    marginTop: 4,
  },
  businessCard: {
    backgroundColor: '#FFFFFF',
    borderColor: '#ECEEF2',
    borderRadius: 18,
    borderWidth: 1,
    elevation: 2,
    marginBottom: 10,
    padding: 15,
    shadowColor: '#AAB1BD',
    shadowOpacity: 0.12,
    shadowRadius: 7,
  },
  businessCardTop: {
    alignItems: 'center',
    flexDirection: 'row',
  },
  businessIconTile: {
    alignItems: 'center',
    backgroundColor: '#FFF0D0',
    borderRadius: 13,
    height: 44,
    justifyContent: 'center',
    marginRight: 11,
    width: 44,
  },
  businessImage: {
    borderRadius: 13,
    height: 44,
    marginRight: 11,
    width: 44,
  },
  businessIcon: {
    fontSize: 22,
  },
  businessCardHeading: {
    flex: 1,
  },
  businessName: {
    color: '#1F2937',
    fontSize: 15,
    fontWeight: '800',
  },
  businessCategory: {
    color: '#FF5A5F',
    fontSize: 12,
    marginTop: 2,
  },
  businessStatus: {
    borderRadius: 9,
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '800',
    overflow: 'hidden',
    paddingHorizontal: 7,
    paddingVertical: 5,
  },
  businessOpen: {
    backgroundColor: '#16A34A',
  },
  businessClosed: {
    backgroundColor: '#6B7280',
  },
  businessDescription: {
    color: '#6B7280',
    fontSize: 12,
    lineHeight: 17,
    marginTop: 11,
  },
  businessAddress: {
    color: '#6B7280',
    fontSize: 11,
    marginTop: 9,
  },
  productSection: {
    width: '100%',
    maxWidth: 600,
    marginBottom: 26,
  },
  productCard: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderColor: '#ECEEF2',
    borderRadius: 18,
    borderWidth: 1,
    flexDirection: 'row',
    marginBottom: 10,
    padding: 12,
  },
  productImageWrapper: {
    borderRadius: 13,
    height: 88,
    overflow: 'hidden',
    width: 88,
  },
  productImage: {
    height: '100%',
    width: '100%',
  },
  productImagePlaceholder: {
    alignItems: 'center',
    backgroundColor: '#FFF0D0',
    borderRadius: 13,
    height: 88,
    justifyContent: 'center',
    width: 88,
  },
  productImageIcon: {
    fontSize: 30,
  },
  productInfo: {
    flex: 1,
    marginLeft: 13,
  },
  productName: {
    color: '#1F2937',
    fontSize: 15,
    fontWeight: '800',
  },
  productBusiness: {
    color: '#FF5A5F',
    fontSize: 12,
    fontWeight: '700',
    marginTop: 3,
  },
  productDescription: {
    color: '#6B7280',
    fontSize: 12,
    marginTop: 5,
  },
  productPrice: {
    color: '#1F2937',
    fontSize: 16,
    fontWeight: '800',
    marginTop: 7,
  },
  productUnit: {
    color: '#6B7280',
    fontSize: 11,
    fontWeight: '500',
  },
  section: {
    width: '100%',
    maxWidth: 600,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1F2937',
    marginBottom: 14,
  },
  cardsList: {
    gap: 12,
  },
  actionCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 18,
    padding: 14,
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#ECEEF2',
    shadowColor: '#AAB1BD',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.16,
    shadowRadius: 10,
    elevation: 2,
  },
  iconTile: {
    width: 52,
    height: 52,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 14,
    backgroundColor: '#FFF0D0',
  },
  cardIcon: {
    fontSize: 25,
  },
  cardContent: {
    flex: 1,
  },
  cardHeader: {
    fontSize: 15,
    fontWeight: '700',
    color: '#1F2937',
  },
  cardDesc: {
    fontSize: 13,
    color: '#6B7280',
    marginTop: 2,
  },
  cardArrow: {
    color: '#FF5A5F',
    fontSize: 30,
    fontWeight: '300',
    marginLeft: 8,
  },
  logoutButton: {
    marginTop: 36,
    paddingVertical: 14,
    paddingHorizontal: 26,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#FF5A5F',
  },
  logoutText: {
    color: '#FF5A5F',
    fontSize: 14,
    fontWeight: '600',
  },
});
