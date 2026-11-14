package co.edu.napa.productos.application.ports.in;

import co.edu.napa.productos.application.ports.in.DescontarInventarioPort.LineaPedido;

import java.util.List;

/** Devuelve al inventario lo que un pedido cancelado había descontado. Ignora productos eliminados. */
public interface DevolverInventarioPort {
    void devolver(List<LineaPedido> items);
}
